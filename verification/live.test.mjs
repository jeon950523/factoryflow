import test from 'node:test'
import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { reconcile } from './oracle.mjs'

// Intentionally restricted to disposable runner-local MySQL through real HTTP.
// No frontend, application services, repositories, mocks or existing test helpers.
const origin = 'http://127.0.0.1:8080/api/v1'
class Client {
  cookies = new Map()
  async call(path, { method = 'GET', body, csrf } = {}) {
    const form = body instanceof URLSearchParams
    const headers = { Accept: 'application/json' }
    if (this.cookies.size) headers.Cookie = [...this.cookies].map(([k, v]) => k + '=' + v).join('; ')
    if (csrf) headers['X-CSRF-TOKEN'] = csrf
    if (body !== undefined) headers['Content-Type'] = form ? 'application/x-www-form-urlencoded' : 'application/json'
    const response = await fetch(origin + path, { method, headers,
      body: body === undefined ? undefined : form ? body : JSON.stringify(body),
      redirect: 'error', signal: AbortSignal.timeout(20000) })
    for (const cookie of response.headers.getSetCookie()) {
      const pair = cookie.split(';', 1)[0]
      const i = pair.indexOf('=')
      if (i > 0) {
        const name = pair.slice(0, i), value = pair.slice(i + 1)
        if (!value || /(?:^|;\s*)Max-Age=0(?:;|$)/i.test(cookie)) this.cookies.delete(name)
        else this.cookies.set(name, value)
      }
    }
    return { status: response.status, payload: await response.json() }
  }
  async get(path) {
    return this.expect(await this.call(path), 200, path)
  }
  expect(result, status, label) {
    assert.equal(result.status, status, label + ': ' + (result.payload.errorCode ?? 'unexpected status'))
    return result.payload.data
  }
  async token() { return (await this.get('/auth/csrf')).token }
  async post(path, body, status = 201) {
    const result = await this.call(path, { method: 'POST', body, csrf: await this.token() })
    return this.expect(result, status, path)
  }
  async login() {
    const username = process.env.VERIFY_ADMIN_USERNAME, password = process.env.VERIFY_ADMIN_PASSWORD
    assert.ok(username && password, 'CI credentials required')
    const result = await this.call('/auth/login', { method: 'POST',
      body: new URLSearchParams({ username, password }), csrf: await this.token() })
    assert.equal(this.expect(result, 200, 'login').role, 'ADMIN')
  }
}
const key = prefix => prefix + '-' + randomUUID()
const byId = (a, b) => a.id - b.id
async function rows(client, path, itemId) {
  const result = await client.get(path + '?itemId=' + itemId + '&size=20')
  assert.equal(result.content.length, result.totalElements, 'fixture unexpectedly exceeds page size')
  return result.content.sort(byId)
}
async function state(client, itemId) {
  const lots = await rows(client, '/inventory/lots', itemId)
  const moves = await rows(client, '/stock-movements', itemId)
  const total = reconcile(lots, moves)
  const summaries = await rows(client, '/inventory', itemId)
  // Some read models omit an item with no LOTs; only zero is permitted then.
  if (summaries.length === 0) assert.deepEqual(total, { onHand: 0, reserved: 0, available: 0 })
  else {
    assert.equal(summaries.length, 1)
    const { onHand, reserved, available } = summaries[0]
    assert.deepEqual({ onHand, reserved, available }, total, 'summary vs LOT ledger')
  }
  return { lots, moves, total }
}
const qty = (onHand, reserved = 0) => ({ onHand, reserved, available: onHand - reserved })
async function expectStock(client, id, expected) {
  const result = await state(client, id)
  assert.deepEqual(result.total, expected)
  return result
}
async function command(client, id, action, expected = 200, body) {
  return client.post('/work-orders/' + id + '/' + action, body, expected)
}
async function status(client, id, expected) {
  assert.equal((await client.get('/work-orders/' + id)).status, expected)
}
test('SSOT black-box: independent inventory, rollback, FIFO, competition, QC and shipment contract', { timeout: 180000 }, async () => {
  const c = new Client()
  assert.equal((await c.call('/items')).status, 401, 'anonymous read denied')
  await c.login()
  const itemBody = { sku: key('DENIED'), name: 'CSRF negative control', itemType: 'RAW_MATERIAL' }
  assert.equal((await c.call('/items', { method: 'POST', body: itemBody })).status, 403, 'CSRF denied')
  const item = type => c.post('/items', { sku: key(type === 'RAW_MATERIAL' ? 'RM' : 'FG'), name: 'Independent fixture', itemType: type })
  const a = await item('RAW_MATERIAL'), b = await item('RAW_MATERIAL'), fg = await item('FINISHED_GOOD')
  await c.post('/boms', { finishedItemId: fg.id, components: [
    { rawItemId: a.id, qtyPerUnit: 2 }, { rawItemId: b.id, qtyPerUnit: 3 },
  ] })
  const receipt = (id, quantity) => ({ requestKey: key('REC'), itemId: id, lotCode: key('LOT'), quantity })
  const first = receipt(a.id, 5)
  const csrf = await c.token()
  const race = (path, payloads) => Promise.all(payloads.map(body => c.call(path, { method: 'POST', body, csrf })))
  const same = await race('/stock-receipts', [first, first])
  assert.deepEqual(same.map(x => x.status).sort(), [200, 201], 'same-key concurrent receipt')
  assert.equal(same[0].payload.data.id, same[1].payload.data.id)
  await c.post('/stock-receipts', { ...first, quantity: 6 }, 409)
  await c.post('/stock-receipts', receipt(a.id, 7))
  await c.post('/stock-receipts', receipt(b.id, 5))
  const initialA = await expectStock(c, a.id, qty(12))
  const initialB = await expectStock(c, b.id, qty(5))
  const order = () => c.post('/work-orders', { clientRequestId: key('WO'), finishedItemId: fg.id, plannedQty: 4 })
  const one = await order(), two = await order()
  await command(c, one.id, 'start', 409)
  await command(c, one.id, 'reserve', 409)
  await status(c, one.id, 'DRAFT')
  assert.deepEqual(await state(c, a.id), initialA, 'partial BOM reservation must roll back')
  assert.deepEqual(await state(c, b.id), initialB)
  await c.post('/stock-receipts', receipt(b.id, 7))
  const reserveToken = await c.token()
  const competing = await Promise.all([one, two].map(o => c.call('/work-orders/' + o.id + '/reserve', {
    method: 'POST', csrf: reserveToken,
  })))
  assert.deepEqual(competing.map(x => x.status).sort(), [200, 409], 'only one competing reservation can win')
  const winner = competing[0].status === 200 ? one : two
  const loser = winner === one ? two : one
  await status(c, winner.id, 'RESERVED')
  await status(c, loser.id, 'DRAFT')
  const reserved = await expectStock(c, a.id, qty(12, 8))
  assert.equal(reserved.lots.find(l => l.lotCode === first.lotCode).reserved, 5, 'oldest LOT consumed first')
  assert.deepEqual(reserved.lots.map(l => l.reserved).sort((x, y) => x - y), [3, 5])
  await expectStock(c, b.id, qty(12, 12))
  await command(c, winner.id, 'cancel')
  await expectStock(c, a.id, qty(12))
  await expectStock(c, b.id, qty(12))
  await command(c, loser.id, 'reserve')
  await command(c, loser.id, 'start')
  await expectStock(c, a.id, qty(4))
  await expectStock(c, b.id, qty(0))
  await command(c, loser.id, 'finish')
  await status(c, loser.id, 'AWAITING_QC')
  await expectStock(c, fg.id, qty(0))
  await command(c, loser.id, 'inspection', 400, { passedQty: 3, failedQty: 0 })
  await status(c, loser.id, 'AWAITING_QC')
  await expectStock(c, fg.id, qty(0))
  await command(c, loser.id, 'inspection', 201, { passedQty: 3, failedQty: 1 })
  await status(c, loser.id, 'COMPLETED')
  await command(c, loser.id, 'inspection', 409, { passedQty: 3, failedQty: 1 })
  await expectStock(c, fg.id, qty(3))
  const shipment = { requestKey: key('SHIP'), finishedItemId: fg.id, quantity: 2 }
  const competingShipments = await Promise.all([shipment, { ...shipment, requestKey: key('SHIP') }].map(async body => ({
    body, result: await c.call('/shipments', { method: 'POST', body, csrf: await c.token() }),
  })))
  assert.deepEqual(competingShipments.map(x => x.result.status).sort(), [201, 409], 'no overshipment')
  const committed = competingShipments.find(x => x.result.status === 201)
  const shipped = await c.post('/shipments', committed.body, 200)
  assert.equal(shipped.id, committed.result.payload.data.id)
  const record = await c.get('/shipments/' + shipped.id)
  assert.equal(record.allocations.reduce((sum, x) => sum + x.quantity, 0), 2)
  await expectStock(c, fg.id, qty(1))
  await expectStock(c, a.id, qty(4))
  await expectStock(c, b.id, qty(0))
})
