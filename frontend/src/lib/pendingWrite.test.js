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


test('TASK-024 reset locks both kinds and rejects shared or tab-local unresolved stock writes', async () => {
  const prevSession = globalThis.sessionStorage
  const prevLocal = globalThis.localStorage
  const prevNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const prevCrypto = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
  const shared = new Map(), tabA = new Map(), tabB = new Map()
  const storage = data => ({
    getItem: key => data.get(key) ?? null,
    setItem: (key, value) => { data.set(key, value) },
    removeItem: key => { data.delete(key) },
  })
  const pendingLocks = new Map()
  const mutex = {
    async request(name, _options, work) {
      const before = pendingLocks.get(name) ?? Promise.resolve()
      let release
      pendingLocks.set(name, new Promise(resolve => { release = resolve }))
      await before
      try { return await work() } finally { release() }
    },
  }
  Object.defineProperty(globalThis, 'navigator',
    { configurable: true, value: { locks: mutex } })
  Object.defineProperty(globalThis, 'crypto',
    { configurable: true, value: { randomUUID: () => 'TASK-024-EPOCH-1' } })
  globalThis.localStorage = storage(shared)
  const { claimPendingWrite, releasePendingWrite, readPendingWrite, demoResetEpoch,
    withDemoResetGuard } = await import('./pendingWrite.js')
  const receipt = { requestKey: 'REC-RESET-A', itemId: 7, lotCode: 'LOT-RESET', quantity: 2 }
  const shipment = { requestKey: 'SHIP-RESET-A', finishedItemId: 4, quantity: 1 }
  try {
    globalThis.sessionStorage = storage(tabA)
    const before = demoResetEpoch()
    assert.equal(before, '')
    assert.deepEqual(await claimPendingWrite('receipt', receipt, 'demo', before), { ok: true })
    globalThis.sessionStorage = storage(tabB)
    let resets = 0
    await assert.rejects(withDemoResetGuard(async () => { resets++ }), /미확인.*초기화하지/)
    assert.equal(resets, 0)
    assert.equal(demoResetEpoch(), before)
    // The other tab can release the same command. A still has stale session state.
    assert.deepEqual(await claimPendingWrite('receipt', receipt, 'demo', before), { ok: true })
    assert.deepEqual(await releasePendingWrite('receipt', receipt, 'demo'), { ok: true })
    assert.deepEqual(await claimPendingWrite('shipment', shipment, 'demo', before), { ok: true })
    await assert.rejects(withDemoResetGuard(async () => { resets++ }), /미확인.*초기화하지/)
    assert.deepEqual(await releasePendingWrite('shipment', shipment, 'demo'), { ok: true })
    await withDemoResetGuard(async () => { resets++ })
    assert.equal(resets, 1)
    assert.equal(demoResetEpoch(), 'TASK-024-EPOCH-1')
    globalThis.sessionStorage = storage(tabA)
    // Stale A cannot read/replay its pre-reset request after B reset.
    assert.equal(readPendingWrite('receipt', 'demo'), null)
    assert.deepEqual(await claimPendingWrite('receipt', receipt, 'demo', before),
      { ok: false, reason: 'RESET_CHANGED' })
    assert.deepEqual(await claimPendingWrite('receipt', receipt, 'demo', demoResetEpoch()),
      { ok: false, reason: 'RESET_CHANGED' })
    const next = { ...receipt, requestKey: 'REC-RESET-NEXT' }
    assert.deepEqual(await claimPendingWrite('receipt', next, 'demo', demoResetEpoch()), { ok: true })
    assert.deepEqual(await releasePendingWrite('receipt', next, 'demo'), { ok: true })
  } finally {
    if (prevSession === undefined) delete globalThis.sessionStorage
    else globalThis.sessionStorage = prevSession
    if (prevLocal === undefined) delete globalThis.localStorage
    else globalThis.localStorage = prevLocal
    if (prevNavigator) Object.defineProperty(globalThis, 'navigator', prevNavigator)
    else delete globalThis.navigator
    if (prevCrypto) Object.defineProperty(globalThis, 'crypto', prevCrypto)
    else delete globalThis.crypto
  }
})

test('TASK-024 reset fails closed without Web Locks or epoch persistence', async () => {
  const beforeNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const beforeLocal = globalThis.localStorage
  const beforeSession = globalThis.sessionStorage
  let changes = 0
  const { withDemoResetGuard } = await import('./pendingWrite.js')
  try {
    Object.defineProperty(globalThis, 'navigator', { configurable: true, value: {} })
    await assert.rejects(withDemoResetGuard(async () => { changes++ }), /잠금/)
    Object.defineProperty(globalThis, 'navigator', {
      configurable: true,
      value: { locks: { request: async (_name, _opts, task) => task() } },
    })
    globalThis.localStorage = {
      getItem: () => null,
      setItem: () => { throw new Error('QuotaExceededError') },
      removeItem: () => {},
    }
    globalThis.sessionStorage = { getItem: () => null }
    await assert.rejects(withDemoResetGuard(async () => { changes++ }), /QuotaExceededError/)
    assert.equal(changes, 0)
  } finally {
    if (beforeNavigator) Object.defineProperty(globalThis, 'navigator', beforeNavigator)
    else delete globalThis.navigator
    if (beforeLocal === undefined) delete globalThis.localStorage
    else globalThis.localStorage = beforeLocal
    if (beforeSession === undefined) delete globalThis.sessionStorage
    else globalThis.sessionStorage = beforeSession
  }
})

test('TASK-024 reset that wins the lock invalidates a queued pre-reset claim', async () => {
  const beforeNavigator = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const beforeLocal = globalThis.localStorage
  const beforeSession = globalThis.sessionStorage
  const beforeCrypto = Object.getOwnPropertyDescriptor(globalThis, 'crypto')
  const shared = new Map(), tab = new Map(), queues = new Map()
  const store = map => ({
    getItem: key => map.get(key) ?? null,
    setItem: (key, value) => map.set(key, value),
    removeItem: key => map.delete(key),
  })
  const locks = {
    async request(name, _options, callback) {
      const previous = queues.get(name) ?? Promise.resolve()
      let release
      queues.set(name, new Promise(resolve => { release = resolve }))
      await previous
      try { return await callback() } finally { release() }
    },
  }
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { locks } })
  Object.defineProperty(globalThis, 'crypto',
    { configurable: true, value: { randomUUID: () => 'TASK-024-EPOCH-QUEUED' } })
  globalThis.localStorage = store(shared)
  globalThis.sessionStorage = store(tab)
  const { withDemoResetGuard, claimPendingWrite, demoResetEpoch } = await import('./pendingWrite.js')
  try {
    let finishReset
    const hold = new Promise(resolve => { finishReset = resolve })
    let started
    const began = new Promise(resolve => { started = resolve })
    const reset = withDemoResetGuard(async () => {
      started()
      await hold
    })
    await began
    const attempted = claimPendingWrite('receipt',
      { requestKey: 'REC-PRE-RESET', itemId: 1, lotCode: 'LOT-OLD', quantity: 2 },
      'demo', '') // Captured by UI before waiting for the reset-held Web Lock.
    finishReset()
    await reset
    assert.equal(demoResetEpoch(), 'TASK-024-EPOCH-QUEUED')
    assert.deepEqual(await attempted, { ok: false, reason: 'RESET_CHANGED' })
    assert.equal([...shared.keys()].filter(key => key.includes('shared-pending')).length, 0)
  } finally {
    if (beforeNavigator) Object.defineProperty(globalThis, 'navigator', beforeNavigator)
    else delete globalThis.navigator
    if (beforeCrypto) Object.defineProperty(globalThis, 'crypto', beforeCrypto)
    else delete globalThis.crypto
    if (beforeLocal === undefined) delete globalThis.localStorage
    else globalThis.localStorage = beforeLocal
    if (beforeSession === undefined) delete globalThis.sessionStorage
    else globalThis.sessionStorage = beforeSession
  }
})
