/**
 * A recoverable stock-write intent, NOT the authoritative stock ledger.
 * Web Locks serialize origin-wide claims. sessionStorage retains the TASK-022
 * same-tab recovery record; localStorage additionally lets other tabs recover.
 */
const KEYS = {
  receipt: 'factoryflow:pending-raw-receipt:v1',
  shipment: 'factoryflow:pending-finished-shipment:v1',
}
const SHARED = {
  receipt: 'factoryflow:shared-pending-raw-receipt:v1',
  shipment: 'factoryflow:shared-pending-finished-shipment:v1',
}
const RESET_EPOCH_KEY = 'factoryflow:demo-reset-epoch:v1'
const TAB_EPOCH = {
  receipt: 'factoryflow:pending-raw-receipt-epoch:v1',
  shipment: 'factoryflow:pending-finished-shipment-epoch:v1',
}
const lockName = kind => 'factoryflow:stock-write:' + kind
export const sharedPendingWriteKey = kind => SHARED[kind]

/** The reset epoch is a UI precondition, not a server-side authorization token. */
export function demoResetEpoch() {
  if (typeof localStorage === 'undefined') return ''
  try { return localStorage.getItem(RESET_EPOCH_KEY) ?? '' } catch { return null }
}

export const isUncertainWriteError = error =>
  error?.status === 0 || error?.status >= 500 || error?.code === 'INVALID_RESPONSE'

export function isConfirmedWriteRejection(error) {
  return error?.status === 400 || error?.status === 404
    || (error?.status === 409 && [
      'INSUFFICIENT_FINISHED_STOCK', 'INVALID_FINISHED_ITEM', 'INVALID_RAW_MATERIAL',
    ].includes(error.code))
}

function snapshot(kind, value) {
  if (!value || Array.isArray(value) || typeof value !== 'object'
    || typeof value.requestKey !== 'string'
    || !/^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$/.test(value.requestKey)
    || !Number.isSafeInteger(value.quantity) || value.quantity < 1) return null
  if (kind === 'receipt') {
    if (!Number.isSafeInteger(value.itemId) || value.itemId < 1
      || typeof value.lotCode !== 'string' || !value.lotCode.length || value.lotCode.length > 80) return null
    return { requestKey: value.requestKey, itemId: value.itemId, lotCode: value.lotCode, quantity: value.quantity }
  }
  if (kind !== 'shipment' || !Number.isSafeInteger(value.finishedItemId)
    || value.finishedItemId < 1) return null
  return { requestKey: value.requestKey, finishedItemId: value.finishedItemId, quantity: value.quantity }
}

const parsePayload = (kind, raw) => {
  try { return raw ? snapshot(kind, JSON.parse(raw)) : null } catch { return null }
}
const parseShared = (kind, raw) => {
  try {
    if (!raw) return null
    const record = JSON.parse(raw)
    const payload = snapshot(kind, record?.payload)
    return payload && typeof record?.owner === 'string' && record.owner
      ? { owner: record.owner, payload } : null
  } catch { return null }
}

export function readSharedPendingWrite(kind, owner) {
  if (!owner || !SHARED[kind]) return null
  try {
    const record = parseShared(kind, localStorage.getItem(SHARED[kind]))
    return record?.owner === owner ? record.payload : null
  } catch { return null }
}

export function readPendingWrite(kind, owner) {
  try {
    const raw = sessionStorage.getItem(KEYS[kind])
    const epoch = demoResetEpoch()
    const savedEpoch = sessionStorage.getItem(TAB_EPOCH[kind]) ?? ''
    // An old tab's session may survive while another tab resets the demo.
    // Do not expose a pre-reset command as an eligible retry.
    if (raw && (epoch === null || savedEpoch === epoch)) {
      const own = parsePayload(kind, raw)
      if (own) return own
    }
  } catch { /* Shared recovery can still be read if sessionStorage fails. */ }
  return readSharedPendingWrite(kind, owner)
}

// TASK-022 legacy tab-local helper: preserve original tests and storage contract.
export function savePendingWrite(kind, payload) {
  const data = snapshot(kind, payload)
  if (!data) return false
  try {
    const text = JSON.stringify(data)
    const epoch = demoResetEpoch()
    if (epoch === null) return false
    const previous = sessionStorage.getItem(KEYS[kind])
    if (previous && previous !== text) return false
    if (previous && (sessionStorage.getItem(TAB_EPOCH[kind]) ?? '') !== epoch) return false
    sessionStorage.setItem(KEYS[kind], text)
    sessionStorage.setItem(TAB_EPOCH[kind], epoch)
    return sessionStorage.getItem(KEYS[kind]) === text
      && sessionStorage.getItem(TAB_EPOCH[kind]) === epoch
  } catch { return false }
}

export function clearPendingWrite(kind) {
  try {
    sessionStorage.removeItem(KEYS[kind])
    sessionStorage.removeItem(TAB_EPOCH[kind])
    return sessionStorage.getItem(KEYS[kind]) === null
      && sessionStorage.getItem(TAB_EPOCH[kind]) === null
  } catch { return false }
}

async function withWriteLock(kind, callback) {
  if (!SHARED[kind] || !globalThis.navigator?.locks?.request) {
    return { ok: false, reason: 'LOCK_UNAVAILABLE' }
  }
  try {
    return await globalThis.navigator.locks.request(lockName(kind),
      { mode: 'exclusive' }, callback)
  } catch { return { ok: false, reason: 'STORAGE_UNAVAILABLE' } }
}

/** Claim matching key+body under one cross-tab exclusive lock BEFORE HTTP POST. */
export async function claimPendingWrite(kind, payload, owner, expectedEpoch = demoResetEpoch()) {
  const data = snapshot(kind, payload)
  if (!data || typeof owner !== 'string' || !owner) return { ok: false, reason: 'INVALID_COMMAND' }
  if (expectedEpoch === null) return { ok: false, reason: 'STORAGE_UNAVAILABLE' }
  return withWriteLock(kind, () => {
    const text = JSON.stringify(data)
    const sharedText = JSON.stringify({ owner, payload: data })
    try {
      const epoch = demoResetEpoch()
      if (epoch === null) return { ok: false, reason: 'STORAGE_UNAVAILABLE' }
      // Reset can win while this tab waits on Web Locks. A pre-reset draft
      // must not be sent into freshly reseeded IndexedDB.
      if (epoch !== expectedEpoch) return { ok: false, reason: 'RESET_CHANGED' }
      const ownText = sessionStorage.getItem(KEYS[kind])
      const ownEpoch = sessionStorage.getItem(TAB_EPOCH[kind]) ?? ''
      if (ownText && ownEpoch !== epoch) {
        const old = parsePayload(kind, ownText)
        if (!old || old.requestKey === data.requestKey) {
          return { ok: false, reason: 'RESET_CHANGED' }
        }
        // This tab has a new post-reset key; its stale pre-reset mirror is
        // no longer eligible for replay and can be replaced.
        if (!clearPendingWrite(kind)) return { ok: false, reason: 'STORAGE_UNAVAILABLE' }
      }
      const previous = localStorage.getItem(SHARED[kind])
      if (previous) {
        const record = parseShared(kind, previous)
        if (!record) return { ok: false, reason: 'INVALID_SHARED_RECORD' }
        if (record.owner !== owner || JSON.stringify(record.payload) !== text) {
          return { ok: false, reason: 'OTHER_PENDING' }
        }
      }
      const own = sessionStorage.getItem(KEYS[kind])
      if (own && own !== text) return { ok: false, reason: 'OTHER_PENDING' }
      localStorage.setItem(SHARED[kind], sharedText)
      if (localStorage.getItem(SHARED[kind]) !== sharedText) {
        return { ok: false, reason: 'STORAGE_UNAVAILABLE' }
      }
      if (!savePendingWrite(kind, data)) {
        if (!previous) localStorage.removeItem(SHARED[kind])
        return { ok: false, reason: 'STORAGE_UNAVAILABLE' }
      }
      return { ok: true }
    } catch { return { ok: false, reason: 'STORAGE_UNAVAILABLE' } }
  })
}

/** Release only THIS confirmed/rejected command; never erase another tab's write. */
export async function releasePendingWrite(kind, payload, owner) {
  const data = snapshot(kind, payload)
  if (!data || !owner) return { ok: false, reason: 'INVALID_COMMAND' }
  return withWriteLock(kind, () => {
    try {
      const previous = localStorage.getItem(SHARED[kind])
      if (previous) {
        const record = parseShared(kind, previous)
        if (!record) return { ok: false, reason: 'INVALID_SHARED_RECORD' }
        if (record.owner !== owner || JSON.stringify(record.payload) !== JSON.stringify(data)) {
          return { ok: false, reason: 'OTHER_PENDING' }
        }
        localStorage.removeItem(SHARED[kind])
        if (localStorage.getItem(SHARED[kind]) !== null) {
          return { ok: false, reason: 'STORAGE_UNAVAILABLE' }
        }
      }
      return clearPendingWrite(kind) ? { ok: true } : { ok: false, reason: 'STORAGE_UNAVAILABLE' }
    } catch { return { ok: false, reason: 'STORAGE_UNAVAILABLE' } }
  })
}

/**
 * Cross-tab demo reset barrier. A reset and every pending stock-write claim
 * use the exact same receipt→shipment Web Lock ordering. The locks remain held
 * until IndexedDB's readwrite reset commits or aborts.
 *
 * Reset generation is saved BEFORE the IDB transaction, not after: otherwise
 * the database could reset while a prior tab still considers its key valid.
 */
export async function withDemoResetGuard(resetTransaction) {
  if (!globalThis.navigator?.locks?.request) {
    throw new Error('브라우저 잠금 기능을 지원하지 않아 데모 초기화를 중단했습니다.')
  }
  return globalThis.navigator.locks.request(lockName('receipt'), { mode: 'exclusive' },
    () => globalThis.navigator.locks.request(lockName('shipment'), { mode: 'exclusive' }, async () => {
      try {
        const epoch = demoResetEpoch()
        if (epoch === null) throw new Error('브라우저 저장소를 읽지 못해 초기화를 중단했습니다.')
        for (const kind of ['receipt', 'shipment']) {
          // Raw presence, including malformed records or another user's intent,
          // is enough to block destructive reset; never silently discard it.
          if (localStorage.getItem(SHARED[kind]) !== null) {
            throw new Error('다른 탭에 미확인 입고·출고 요청이 있어 초기화하지 않았습니다. 동일 요청의 결과를 먼저 확인하세요.')
          }
          const raw = sessionStorage.getItem(KEYS[kind])
          const tabEpoch = sessionStorage.getItem(TAB_EPOCH[kind]) ?? ''
          if (raw !== null && tabEpoch === epoch) {
            throw new Error('이 탭에 미확인 입고·출고 요청이 있어 초기화하지 않았습니다. 동일 요청의 결과를 먼저 확인하세요.')
          }
        }
        const next = globalThis.crypto?.randomUUID?.()
        if (!next) throw new Error('데모 초기화 식별값을 발급하지 못했습니다.')
        localStorage.setItem(RESET_EPOCH_KEY, next)
        if (localStorage.getItem(RESET_EPOCH_KEY) !== next) {
          throw new Error('초기화 식별값을 저장하지 못해 초기화를 중단했습니다.')
        }
      } catch (error) {
        if (error instanceof Error) throw error
        throw new Error('브라우저 저장소 문제로 데모 초기화를 중단했습니다.')
      }
      return resetTransaction()
    }))
}

/** Storage events update UX only. The lock + backend protect stock correctness. */
export function observeSharedPendingWrite(kind, listener) {
  if (!globalThis.window || !SHARED[kind]) return () => {}
  const handler = event => {
    if (event.key === SHARED[kind] || event.key === RESET_EPOCH_KEY || event.key === null) listener()
  }
  window.addEventListener('storage', handler)
  return () => window.removeEventListener('storage', handler)
}
