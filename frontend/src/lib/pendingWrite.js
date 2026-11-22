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
export const sharedPendingWriteKey = kind => SHARED[kind]

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
    return parsePayload(kind, sessionStorage.getItem(KEYS[kind]))
      ?? readSharedPendingWrite(kind, owner)
  } catch {
    return readSharedPendingWrite(kind, owner)
  }
}

// TASK-022 legacy tab-local helper: preserve original tests and storage contract.
export function savePendingWrite(kind, payload) {
  const data = snapshot(kind, payload)
  if (!data) return false
  try {
    const text = JSON.stringify(data)
    const previous = sessionStorage.getItem(KEYS[kind])
    if (previous && previous !== text) return false
    sessionStorage.setItem(KEYS[kind], text)
    return sessionStorage.getItem(KEYS[kind]) === text
  } catch { return false }
}

export function clearPendingWrite(kind) {
  try {
    sessionStorage.removeItem(KEYS[kind])
    return sessionStorage.getItem(KEYS[kind]) === null
  } catch { return false }
}

async function withWriteLock(kind, callback) {
  if (!SHARED[kind] || !globalThis.navigator?.locks?.request) {
    return { ok: false, reason: 'LOCK_UNAVAILABLE' }
  }
  try {
    return await globalThis.navigator.locks.request('factoryflow:stock-write:' + kind,
      { mode: 'exclusive' }, callback)
  } catch { return { ok: false, reason: 'STORAGE_UNAVAILABLE' } }
}

/** Claim matching key+body under one cross-tab exclusive lock BEFORE HTTP POST. */
export async function claimPendingWrite(kind, payload, owner) {
  const data = snapshot(kind, payload)
  if (!data || typeof owner !== 'string' || !owner) return { ok: false, reason: 'INVALID_COMMAND' }
  return withWriteLock(kind, () => {
    const text = JSON.stringify(data)
    const sharedText = JSON.stringify({ owner, payload: data })
    try {
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

/** Storage events update UX only. The lock + backend protect stock correctness. */
export function observeSharedPendingWrite(kind, listener) {
  if (!globalThis.window || !SHARED[kind]) return () => {}
  const handler = event => {
    if (event.key === SHARED[kind] || event.key === null) listener()
  }
  window.addEventListener('storage', handler)
  return () => window.removeEventListener('storage', handler)
}
