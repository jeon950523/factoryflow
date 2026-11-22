import test from 'node:test'
import assert from 'node:assert/strict'
import {
  isUncertainWriteError, isConfirmedWriteRejection, savePendingWrite, readPendingWrite, clearPendingWrite,
} from './pendingWrite.js'

function withStorage(run) {
  const previous = globalThis.sessionStorage
  const data = new Map()
  globalThis.sessionStorage = {
    setItem: (key, value) => data.set(key, value),
    getItem: key => data.get(key) ?? null,
    removeItem: key => data.delete(key),
  }
  try { run(data) } finally {
    if (previous === undefined) delete globalThis.sessionStorage
    else globalThis.sessionStorage = previous
  }
}

test('ambiguous writes are limited to transport failures, 5xx and invalid response', () => {
  assert.equal(isUncertainWriteError({ status: 0 }), true)
  assert.equal(isUncertainWriteError({ status: 503 }), true)
  assert.equal(isUncertainWriteError({ status: 200, code: 'INVALID_RESPONSE' }), true)
  assert.equal(isUncertainWriteError({ status: 409, code: 'INSUFFICIENT_FINISHED_STOCK' }), false)
  assert.equal(isUncertainWriteError({ status: 400 }), false)
  assert.equal(isUncertainWriteError({ status: 401 }), false)
  assert.equal(isUncertainWriteError({ status: 403 }), false)
})

test('receipt retry recovers exact key and body from tab storage then clears on success', () => withStorage(() => {
  const payload = { requestKey: 'REC-0001', itemId: 51, lotCode: 'LOT-51', quantity: 12 }
  assert.equal(savePendingWrite('receipt', payload), true)
  assert.deepEqual(readPendingWrite('receipt'), payload)
  clearPendingWrite('receipt')
  assert.equal(readPendingWrite('receipt'), null)
}))

test('shipment retry allows one key and returns only API contract fields', () => withStorage(data => {
  const payload = { requestKey: 'SHIP-0001', finishedItemId: 7, quantity: 4 }
  assert.equal(savePendingWrite('shipment', payload), true)
  const key = [...data.keys()][0]
  data.set(key, JSON.stringify({ ...payload, unexpected: 'not forwarded' }))
  assert.deepEqual(readPendingWrite('shipment'), payload)
}))

test('malformed stored writes are ignored, and disabled storage does not throw', () => withStorage(data => {
  data.set('factoryflow:pending-finished-shipment:v1', JSON.stringify({
    requestKey: 'SHIP-BAD', finishedItemId: 5, quantity: -1,
  }))
  assert.equal(readPendingWrite('shipment'), null)
  data.set('factoryflow:pending-finished-shipment:v1', '{malformed')
  assert.equal(readPendingWrite('shipment'), null)
  assert.equal(savePendingWrite('shipment', { requestKey: 'bad key', finishedItemId: 5, quantity: 1 }), false)
}))


test('only explicit business/validation rejections may unlock an unresolved write', () => {
  assert.equal(isConfirmedWriteRejection({ status: 400, code: 'INVALID_RECEIPT' }), true)
  assert.equal(isConfirmedWriteRejection({ status: 404, code: 'ITEM_NOT_FOUND' }), true)
  assert.equal(isConfirmedWriteRejection({ status: 409, code: 'INSUFFICIENT_FINISHED_STOCK' }), true)
  assert.equal(isConfirmedWriteRejection({ status: 409, code: 'IDEMPOTENCY_KEY_CONFLICT' }), false)
  assert.equal(isConfirmedWriteRejection({ status: 401 }), false)
  assert.equal(isConfirmedWriteRejection({ status: 403 }), false)
  assert.equal(isConfirmedWriteRejection({ status: 503 }), false)
})

test('write-ahead record refuses to replace another unresolved command', () => withStorage(() => {
  const first = { requestKey: 'REC-A', itemId: 7, lotCode: 'LOT-A', quantity: 2 }
  const another = { ...first, requestKey: 'REC-B' }
  assert.equal(savePendingWrite('receipt', first), true)
  assert.equal(savePendingWrite('receipt', first), true)
  assert.equal(savePendingWrite('receipt', another), false)
  assert.deepEqual(readPendingWrite('receipt'), first)
  clearPendingWrite('receipt')
  assert.equal(savePendingWrite('receipt', another), true)
}))

test('write-ahead storage rejects exceptions and silent non-persistence', () => withStorage(data => {
  const payload = { requestKey: 'SHIP-PREFLIGHT', finishedItemId: 7, quantity: 1 }
  const storage = globalThis.sessionStorage
  const originalSet = storage.setItem
  storage.setItem = () => { throw new Error('QuotaExceededError') }
  assert.equal(savePendingWrite('shipment', payload), false)
  assert.deepEqual([...data.values()], [])
  storage.setItem = originalSet
  const originalGet = storage.getItem
  storage.getItem = () => null
  assert.equal(savePendingWrite('shipment', payload), false)
  storage.getItem = originalGet
}))

test('cross-tab claim serializes different keys and shares only the owning login intent', async () => {
  const oldSession = globalThis.sessionStorage
  const oldLocal = globalThis.localStorage
  const oldNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const shared = new Map(), tabA = new Map(), tabB = new Map()
  const storage = data => ({
    getItem: key => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
    removeItem: key => data.delete(key),
  })
  // Minimal FIFO Web Locks simulation. No localStorage get/set race is allowed.
  let tail = Promise.resolve()
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: {
    locks: { request: async (_name, _options, callback) => {
      const before = tail
      let release
      tail = new Promise(resolve => { release = resolve })
      await before
      try { return await callback() } finally { release() }
    } },
  } })
  globalThis.localStorage = storage(shared)
  const { claimPendingWrite, readSharedPendingWrite, releasePendingWrite } = await import('./pendingWrite.js')
  const first = { requestKey: 'REC-TAB-A', itemId: 51, lotCode: 'LOT-A', quantity: 3 }
  const second = { ...first, requestKey: 'REC-TAB-B' }
  try {
    globalThis.sessionStorage = storage(tabA)
    assert.deepEqual(await claimPendingWrite('receipt', first, 'operator'), { ok: true })
    globalThis.sessionStorage = storage(tabB)
    assert.deepEqual(readSharedPendingWrite('receipt', 'operator'), first)
    assert.equal(readSharedPendingWrite('receipt', 'other-login'), null)
    assert.deepEqual(await claimPendingWrite('receipt', second, 'operator'),
      { ok: false, reason: 'OTHER_PENDING' })
    assert.deepEqual(await claimPendingWrite('receipt', second, 'other-login'),
      { ok: false, reason: 'OTHER_PENDING' })
    assert.deepEqual(await claimPendingWrite('receipt', first, 'operator'), { ok: true })
    assert.deepEqual(await releasePendingWrite('receipt', second, 'operator'),
      { ok: false, reason: 'OTHER_PENDING' })
    assert.deepEqual(await releasePendingWrite('receipt', first, 'operator'), { ok: true })
    assert.equal(readSharedPendingWrite('receipt', 'operator'), null)
    globalThis.sessionStorage = storage(tabA)
    assert.deepEqual(await releasePendingWrite('receipt', first, 'operator'), { ok: true })
    assert.deepEqual(await claimPendingWrite('receipt', second, 'operator'), { ok: true })
  } finally {
    if (oldSession === undefined) delete globalThis.sessionStorage
    else globalThis.sessionStorage = oldSession
    if (oldLocal === undefined) delete globalThis.localStorage
    else globalThis.localStorage = oldLocal
    if (oldNavigator) Object.defineProperty(globalThis, 'navigator', oldNavigator)
    else delete globalThis.navigator
  }
})

test('missing Web Locks fails closed without writing pending stock command', async () => {
  const oldNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const { claimPendingWrite } = await import('./pendingWrite.js')
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: {} })
  try {
    assert.deepEqual(await claimPendingWrite('shipment',
      { requestKey: 'SHIP-NO-LOCK', finishedItemId: 7, quantity: 2 }, 'operator'),
      { ok: false, reason: 'LOCK_UNAVAILABLE' })
  } finally {
    if (oldNavigator) Object.defineProperty(globalThis, 'navigator', oldNavigator)
    else delete globalThis.navigator
  }
})
