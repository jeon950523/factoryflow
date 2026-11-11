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
let onSessionExpired = null

export function setSessionExpiredHandler(handler) { onSessionExpired = handler }
export function clearCsrfToken() { csrfToken = null; csrfPromise = null }

export async function ensureCsrfToken() {
  if (csrfToken) return csrfToken
  if (!csrfPromise) {
    csrfPromise = api('/api/v1/auth/csrf').then(result => {
      csrfToken = result.token
      return csrfToken
    }).finally(() => { csrfPromise = null })
  }
  return csrfPromise
}

export async function api(path, { method = 'GET', body, signal } = {}) {
  const formBody = body instanceof URLSearchParams
  const unsafe = !['GET','HEAD','OPTIONS'].includes(method.toUpperCase())
  const csrf = unsafe ? await ensureCsrfToken() : null
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
    if (response.status === 401 && path !== '/api/v1/auth/login' && path !== '/api/v1/auth/me') {
      clearCsrfToken()
      onSessionExpired?.()
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
