import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createSeedState, executeDemo, DemoError } from './engine.js'

const get = (state, path) => executeDemo(state, path).data
function post(state, path, body = {}) {
  const next = executeDemo(state, path, { method: 'POST', body })
  return { state: next.state, data: next.data }
}
function seed() { return createSeedState(Date.parse('2026-11-12T12:00:00Z')) }
const rawStock = (s, itemId) => get(s, '/api/v1/inventory?itemId=' + itemId).content[0]

test('seed has a completed example and a runnable draft with consistent inventory', () => {
  const s = seed()
  assert.equal(rawStock(s, 1).onHand, 40)
  assert.equal(rawStock(s, 4).onHand, 3)
  assert.equal(get(s, '/api/v1/work-orders?status=DRAFT').totalElements, 1)
  assert.equal(get(s, '/api/v1/work-orders?status=COMPLETED').totalElements, 1)
  assert.equal(get(s, '/api/v1/shipments').totalElements, 1)
})

test('anonymous demo works without password or server account features', () => {
  const s = seed()
  assert.deepEqual(get(s, '/api/v1/auth/me'), { username: 'demo', role: 'ADMIN' })
  assert.throws(() => get(s, '/api/v1/admin/users'), e => e.status === 403 && e.code === 'DEMO_NO_ACCOUNTS')
})

test('BOM/item lookup and paging return the same DTO shapes as live endpoints', () => {
  const s = seed()
  const page = get(s, '/api/v1/items?itemType=RAW_MATERIAL&page=1&size=2')
  assert.deepEqual([page.totalElements, page.totalPages, page.content.length], [3, 2, 1])
  assert.deepEqual(get(s, '/api/v1/items/lookup?ids=4,2,4').map(x => x.id), [4, 2])
  assert.equal(get(s, '/api/v1/boms/4').components.length, 3)
})

test('receiving raw stock creates a movement, and retry is idempotent', () => {
  const s = seed()
  const request = { itemId: 1, lotCode: 'STEEL-A01', quantity: 4, requestKey: 'REPLAY-1' }
  const first = post(s, '/api/v1/stock-receipts', request)
  const twice = post(first.state, '/api/v1/stock-receipts', request)
  assert.deepEqual(twice.data, first.data)
  assert.equal(rawStock(twice.state, 1).onHand, 44)
  assert.equal(get(twice.state, '/api/v1/stock-movements?itemId=1').totalElements, 3)
  assert.throws(() => post(twice.state, '/api/v1/stock-receipts',
    { ...request, quantity: 9 }), e => e.code === 'IDEMPOTENCY_KEY_CONFLICT')
})

test('shortage reservation changes nothing, even if earlier material was available', () => {
  const s = seed()
  const before = structuredClone(s)
  const order = post(s, '/api/v1/work-orders',
    { clientRequestId: 'BIG-ORDER', finishedItemId: 4, plannedQty: 100 })
  assert.throws(() => post(order.state, '/api/v1/work-orders/' + order.data.id + '/reserve'),
    e => e instanceof DemoError && e.code === 'INSUFFICIENT_STOCK' && e.status === 409)
  assert.equal(get(order.state, '/api/v1/work-orders/' + order.data.id).status, 'DRAFT')
  assert.equal(rawStock(order.state, 1).reserved, 0)
  assert.deepEqual(s, before)
})

test('reserve, consume, finish and QC have distinct stock effects', () => {
  let s = seed()
  s = post(s, '/api/v1/work-orders/2/reserve').state
  assert.equal(get(s, '/api/v1/work-orders/2').status, 'RESERVED')
  assert.deepEqual([rawStock(s, 1).onHand, rawStock(s, 1).reserved], [40, 20])
  s = post(s, '/api/v1/work-orders/2/start').state
  assert.deepEqual([rawStock(s, 1).onHand, rawStock(s, 1).reserved], [20, 0])
  assert.equal(get(s, '/api/v1/work-orders/2').status, 'IN_PROGRESS')
  s = post(s, '/api/v1/work-orders/2/finish').state
  assert.equal(rawStock(s, 4).onHand, 3)
  const qc = post(s, '/api/v1/work-orders/2/inspection', { passedQty: 8, failedQty: 2 })
  s = qc.state
  assert.equal(qc.data.passedQty, 8)
  assert.equal(rawStock(s, 4).onHand, 11)
  assert.equal(get(s, '/api/v1/inventory/lots?itemId=4').totalElements, 2)
  assert.equal(get(s, '/api/v1/work-orders/2').status, 'COMPLETED')
})

test('canceling a RESERVED order releases quantities without physical movements', () => {
  let s = seed()
  s = post(s, '/api/v1/work-orders/2/reserve').state
  const count = s.movements.length
  s = post(s, '/api/v1/work-orders/2/cancel').state
  assert.equal(get(s, '/api/v1/work-orders/2').status, 'CANCELED')
  assert.equal(rawStock(s, 1).reserved, 0)
  assert.equal(rawStock(s, 1).onHand, 40)
  assert.equal(s.movements.length, count)
})

test('shipment allocates FIFO only from QC_PASS and never partially ships', () => {
  let s = seed()
  s = post(s, '/api/v1/work-orders/2/reserve').state
  s = post(s, '/api/v1/work-orders/2/start').state
  s = post(s, '/api/v1/work-orders/2/finish').state
  s = post(s, '/api/v1/work-orders/2/inspection', { passedQty: 10, failedQty: 0 }).state
  const shipment = post(s, '/api/v1/shipments', { finishedItemId: 4, quantity: 8, requestKey: 'SHIP-CHECK' })
  s = shipment.state
  assert.deepEqual(shipment.data.allocations, [{ lotId: 4, quantity: 3 }, { lotId: 5, quantity: 5 }])
  assert.equal(rawStock(s, 4).onHand, 5)
  assert.throws(() => post(s, '/api/v1/shipments',
    { finishedItemId: 4, quantity: 6, requestKey: 'SHIP-TOO-MUCH' }),
    e => e.code === 'INSUFFICIENT_FINISHED_STOCK')
  assert.equal(rawStock(s, 4).onHand, 5)
})

test('repeating a shipment request does not double-decrement inventory', () => {
  const s = seed()
  const command = { finishedItemId: 4, quantity: 2, requestKey: 'SHIP-RETRY' }
  const first = post(s, '/api/v1/shipments', command)
  const second = post(first.state, '/api/v1/shipments', command)
  assert.deepEqual(first.data, second.data)
  assert.equal(rawStock(second.state, 4).onHand, 1)
  assert.equal(second.state.shipments.length, 2)
})

test('work orders snapshot BOM at creation; retries do not duplicate the order', () => {
  const s = seed()
  const first = post(s, '/api/v1/work-orders',
    { clientRequestId: 'NEW-WO-1', finishedItemId: 4, plannedQty: 3 })
  const retry = post(first.state, '/api/v1/work-orders',
    { clientRequestId: 'NEW-WO-1', finishedItemId: 4, plannedQty: 3 })
  assert.equal(retry.data.id, first.data.id)
  assert.equal(retry.state.orders.length, 3)
  assert.deepEqual(retry.data.materials.map(x => x.requiredQty), [6, 12, 3])
})

test('invalid transition returns 409 without mutating original state', () => {
  const s = seed()
  assert.throws(() => post(s, '/api/v1/work-orders/2/finish'), e => e.status === 409)
  assert.equal(get(s, '/api/v1/work-orders/2').status, 'DRAFT')
  assert.equal(s.reservations.length, 0)
})

test('zero QC pass does not create a finished-goods LOT', () => {
  let s = seed()
  s = post(s, '/api/v1/work-orders/2/reserve').state
  s = post(s, '/api/v1/work-orders/2/start').state
  s = post(s, '/api/v1/work-orders/2/finish').state
  const existing = s.lots.length
  s = post(s, '/api/v1/work-orders/2/inspection', { passedQty: 0, failedQty: 10 }).state
  assert.equal(s.lots.length, existing)
  assert.equal(rawStock(s, 4).onHand, 3)
})

test('duplicate SKU and BOM are rejected while leaving the seed untouched', () => {
  const s = seed()
  const original = structuredClone(s)
  assert.throws(() => post(s, '/api/v1/items', { sku: 'RAW-PLATE-001', name: '동일', itemType: 'RAW_MATERIAL' }),
    e => e.code === 'DUPLICATE_SKU')
  assert.throws(() => post(s, '/api/v1/boms', { finishedItemId: 4, components: [{ rawItemId: 1, qtyPerUnit: 1 }] }),
    e => e.code === 'BOM_ALREADY_EXISTS')
  assert.deepEqual(s, original)
})

test('read operations do not modify snapshots or persist accidental changes', () => {
  const s = seed()
  const snapshot = structuredClone(s)
  const result = executeDemo(s, '/api/v1/inventory?size=10')
  assert.equal(result.modified, false)
  assert.strictEqual(result.state, s)
  assert.deepEqual(s, snapshot)
})
