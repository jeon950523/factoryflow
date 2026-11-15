/**
 * One pending physical-stock write per tab and operation kind. This is a browser
 * recovery aid, not a substitute for the backend's UNIQUE key + transaction.
 */
const KEYS = {
  receipt: 'factoryflow:pending-raw-receipt:v1',
  shipment: 'factoryflow:pending-finished-shipment:v1',
}

export function isUncertainWriteError(error) {
  return error?.status === 0 || error?.status >= 500 || error?.code === 'INVALID_RESPONSE'
}

function validPayload(kind, value) {
  if (!value || Array.isArray(value) || typeof value !== 'object'
      || typeof value.requestKey !== 'string'
      || !/^[A-Za-z0-9][A-Za-z0-9._:-]{0,99}$/.test(value.requestKey)
      || !Number.isSafeInteger(value.quantity) || value.quantity < 1) return false
  if (kind === 'receipt') {
    return Number.isSafeInteger(value.itemId) && value.itemId > 0
      && typeof value.lotCode === 'string' && value.lotCode.length > 0 && value.lotCode.length <= 80
  }
  return kind === 'shipment'
    && Number.isSafeInteger(value.finishedItemId) && value.finishedItemId > 0
}

export function readPendingWrite(kind) {
  try {
    const raw = sessionStorage.getItem(KEYS[kind])
    if (!raw) return null
    const value = JSON.parse(raw)
    if (!validPayload(kind, value)) return null
    // Allow only defined contract fields; do not forward arbitrary stored properties.
    return kind === 'receipt'
      ? { requestKey: value.requestKey, itemId: value.itemId, lotCode: value.lotCode, quantity: value.quantity }
      : { requestKey: value.requestKey, finishedItemId: value.finishedItemId, quantity: value.quantity }
  } catch {
    return null
  }
}

export function savePendingWrite(kind, payload) {
  if (!validPayload(kind, payload)) return false
  try {
    sessionStorage.setItem(KEYS[kind], JSON.stringify(payload))
    return true
  } catch {
    return false
  }
}

export function clearPendingWrite(kind) {
  try { sessionStorage.removeItem(KEYS[kind]) } catch { /* Storage may be disabled. */ }
}
