/** Production builds use isolated IndexedDB until a real backend is deployed.
 * Set VITE_DEMO_MODE=false at build time to opt into the real Spring API.
 */
export const isDemoMode = import.meta.env?.PROD === true && import.meta.env?.VITE_DEMO_MODE !== 'false'

const configured = (import.meta.env?.VITE_API_BASE_URL ?? '').trim()
const baseUrl = configured.replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, { status = 0, code = 'NETWORK_ERROR', traceId = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.traceId = traceId
  }
}

export function params(query = {}) {
  const result = new URLSearchParams()
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') result.set(key, String(value))
  })
  const text = result.toString()
  return text ? `?${text}` : ''
}

let csrfToken = null
let csrfPromise = null
let csrfGeneration = 0
let sessionProbe = null
let onSessionExpired = null

export function setSessionExpiredHandler(handler) { onSessionExpired = handler }
export function clearCsrfToken() {
  csrfGeneration += 1
  csrfToken = null
  csrfPromise = null
}

export async function ensureCsrfToken() {
  if (csrfToken) return csrfToken
  if (!csrfPromise) {
    const generation = csrfGeneration
    const request = api('/api/v1/auth/csrf').then(result => {
      // Discard an older token after login/logout rotated the server session.
      if (generation !== csrfGeneration) return ensureCsrfToken()
      csrfToken = result.token
      return csrfToken
    }).finally(() => {
      if (csrfPromise === request) csrfPromise = null
    })
    csrfPromise = request
  }
  return csrfPromise
}

async function checkSessionAfterForbidden(generation) {
  // A stale 403 from a previous login must not inspect or revoke a new session.
  if (generation !== csrfGeneration) return
  if (!sessionProbe || sessionProbe.generation !== generation) {
    const promise = fetch(`${baseUrl}/api/v1/auth/me`, {
      method: 'GET',
      credentials: 'same-origin',
      cache: 'no-store',
      headers: { Accept: 'application/json' },
    }).then(response => {
      if (response.status === 401 && generation === csrfGeneration) {
        clearCsrfToken()
        onSessionExpired?.()
      }
    }).catch(() => {
      // If this diagnostic request fails, preserve the original 403.
    }).finally(() => {
      if (sessionProbe?.promise === promise) sessionProbe = null
    })
    sessionProbe = { generation, promise }
  }
  await sessionProbe.promise
}

export async function api(path, { method = 'GET', body, signal } = {}) {
  if (isDemoMode) {
    const { requestDemo } = await import('./demo/store.js')
    return requestDemo(path, { method, body, signal })
  }
  const formBody = body instanceof URLSearchParams
  const unsafe = !['GET','HEAD','OPTIONS'].includes(method.toUpperCase())
  const csrf = unsafe ? await ensureCsrfToken() : null
  const requestGeneration = csrfGeneration
  let response
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method,
      signal,
      credentials: 'same-origin',
      cache: 'no-store',
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': formBody ? 'application/x-www-form-urlencoded' : 'application/json' }),
        ...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
      },
      body: body === undefined ? undefined : (formBody ? body.toString() : JSON.stringify(body)),
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new ApiError('서버에 연결할 수 없습니다. 백엔드 실행 상태와 API 주소를 확인하세요.')
  }
  let payload
  try {
    payload = await response.json()
  } catch {
    throw new ApiError('서버에서 올바르지 않은 JSON 응답을 받았습니다.', { status: response.status, code: 'INVALID_RESPONSE' })
  }
  if (!response.ok) {
    if (response.status === 401 && requestGeneration === csrfGeneration
        && path !== '/api/v1/auth/login' && path !== '/api/v1/auth/me') {
      clearCsrfToken()
      onSessionExpired?.()
    } else if (response.status === 403 && requestGeneration === csrfGeneration
        && path.startsWith('/api/v1/') && !path.startsWith('/api/v1/auth/')) {
      // VIEWER permission denial must not force logout; an expired session must.
      await checkSessionAfterForbidden(requestGeneration)
      if (requestGeneration === csrfGeneration) clearCsrfToken()
    }
    throw new ApiError(payload.message ?? `요청에 실패했습니다. (${response.status})`, {
      status: response.status,
      code: payload.errorCode ?? 'REQUEST_FAILED',
      traceId: payload.traceId ?? null,
    })
  }
  if (!payload || !Object.prototype.hasOwnProperty.call(payload, 'data')) {
    throw new ApiError('API 응답에 data 필드가 없습니다.', { status: response.status, code: 'INVALID_RESPONSE' })
  }
  return payload.data
}

export const get = (path, query, options = {}) => api(`${path}${params(query)}`, options)
export const post = (path, body) => api(path, { method: 'POST', body })

export const loginForm = (username, password) => api('/api/v1/auth/login', {
  method: 'POST', body: new URLSearchParams({ username, password }),
})
export const logoutSession = () => api('/api/v1/auth/logout', { method: 'POST' })
