import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  ApiError, clearCsrfToken, ensureCsrfToken, get,
  loginForm, post, setSessionExpiredHandler,
} from './api.js'

const reply = (status, body) => ({
  status,
  ok: status >= 200 && status < 300,
  json: async () => body,
})

test('VIEWER 403 does not log out an authenticated user', async () => {
  const old = globalThis.fetch
  let expired = 0, probes = 0
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') return reply(200, { data: { token: 'csrf-viewer' } })
    if (url === '/api/v1/auth/me') {
      probes += 1
      return reply(200, { data: { username: 'viewer', role: 'VIEWER' } })
    }
    return reply(403, { errorCode: 'FORBIDDEN', message: '권한이 없습니다.' })
  }
  try {
    await assert.rejects(() => post('/api/v1/items', { sku: 'RM-1' }), e =>
      e instanceof ApiError && e.status === 403 && e.code === 'FORBIDDEN')
    assert.equal(probes, 1)
    assert.equal(expired, 0)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})

test('expired session on 403 is confirmed by /auth/me 401', async () => {
  const old = globalThis.fetch
  let expired = 0
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') return reply(200, { data: { token: 'csrf-expired' } })
    if (url === '/api/v1/auth/me') return reply(401, { errorCode: 'UNAUTHENTICATED' })
    return reply(403, { errorCode: 'FORBIDDEN', message: 'CSRF 만료' })
  }
  try {
    await assert.rejects(() => post('/api/v1/items', { sku: 'RM-1' }), e => e.status === 403)
    assert.equal(expired, 1)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})

test('failed session probe preserves the original 403', async () => {
  const old = globalThis.fetch
  let expired = 0
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') return reply(200, { data: { token: 'csrf' } })
    if (url === '/api/v1/auth/me') throw new TypeError('offline')
    return reply(403, { errorCode: 'FORBIDDEN', message: '권한 없음' })
  }
  try {
    await assert.rejects(() => post('/api/v1/items', { sku: 'RM-1' }),
      e => e.code === 'FORBIDDEN' && e.message === '권한 없음')
    assert.equal(expired, 0)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})

test('old CSRF GET cannot replace the token rotated by login', async () => {
  const old = globalThis.fetch
  clearCsrfToken()
  let finishOld, requests = 0
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') {
      requests += 1
      if (requests === 1) return new Promise(resolve => { finishOld = resolve })
      return reply(200, { data: { token: 'new-token' } })
    }
    return reply(201, { data: { id: 1 } })
  }
  try {
    const first = ensureCsrfToken()
    clearCsrfToken()
    assert.equal(await ensureCsrfToken(), 'new-token')
    finishOld(reply(200, { data: { token: 'old-token' } }))
    assert.equal(await first, 'new-token')
    assert.equal(await ensureCsrfToken(), 'new-token')
    assert.equal(requests, 2)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
  }
})

test('successful login only requires its initial CSRF fetch', async () => {
  const old = globalThis.fetch
  clearCsrfToken()
  let calls = 0
  globalThis.fetch = async url => {
    calls += 1
    if (url === '/api/v1/auth/csrf') return reply(200, { data: { token: 'csrf' } })
    return reply(200, { data: { username: 'admin', role: 'ADMIN' } })
  }
  try {
    assert.deepEqual(await loginForm('admin', 'password'), { username: 'admin', role: 'ADMIN' })
    assert.equal(calls, 2)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
  }
})

test('simultaneous 403s share one session probe and one logout', async () => {
  const old = globalThis.fetch
  let expired = 0, probes = 0
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') return reply(200, { data: { token: 'csrf' } })
    if (url === '/api/v1/auth/me') {
      probes += 1
      return reply(401, { errorCode: 'UNAUTHENTICATED' })
    }
    return reply(403, { errorCode: 'FORBIDDEN', message: 'CSRF 만료' })
  }
  try {
    await ensureCsrfToken()
    const results = await Promise.allSettled([
      post('/api/v1/items', { sku: 'RM-1' }),
      post('/api/v1/items', { sku: 'RM-2' }),
    ])
    assert.deepEqual(results.map(r => r.status), ['rejected', 'rejected'])
    assert.equal(probes, 1)
    assert.equal(expired, 1)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})

test('old session probe cannot expire a newly logged-in session', async () => {
  const old = globalThis.fetch
  let expired = 0, finishProbe
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async url => {
    if (url === '/api/v1/auth/csrf') return reply(200, { data: { token: 'csrf' } })
    if (url === '/api/v1/auth/me') return new Promise(resolve => { finishProbe = resolve })
    return reply(403, { errorCode: 'FORBIDDEN', message: '권한 없음' })
  }
  try {
    const operation = post('/api/v1/items', { sku: 'RM-1' })
    for (let i = 0; i < 20 && !finishProbe; i += 1) await Promise.resolve()
    assert.ok(finishProbe)
    clearCsrfToken()
    finishProbe(reply(401, { errorCode: 'UNAUTHENTICATED' }))
    await assert.rejects(() => operation, e => e.status === 403)
    assert.equal(expired, 0)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})

test('late 403 cannot clear the token of a newer session', async () => {
  const old = globalThis.fetch
  let resolveOld, csrfFetches = 0, probes = 0, expired = 0
  const writtenTokens = []
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async (url, options) => {
    if (url === '/api/v1/auth/csrf') {
      csrfFetches += 1
      return reply(200, { data: { token: `token-${csrfFetches}` } })
    }
    if (url === '/api/v1/auth/me') {
      probes += 1
      return reply(200, { data: { username: 'admin', role: 'ADMIN' } })
    }
    writtenTokens.push(options.headers['X-CSRF-TOKEN'])
    if (writtenTokens.length === 1) return new Promise(resolve => { resolveOld = resolve })
    return reply(201, { data: { id: writtenTokens.length } })
  }
  try {
    const older = post('/api/v1/items', { sku: 'old' })
    for (let i = 0; i < 20 && !resolveOld; i += 1) await Promise.resolve()
    assert.ok(resolveOld)
    clearCsrfToken()
    await post('/api/v1/items', { sku: 'new' })
    resolveOld(reply(403, { errorCode: 'FORBIDDEN', message: 'old forbidden' }))
    await assert.rejects(() => older, e => e.status === 403)
    await post('/api/v1/items', { sku: 'new-2' })
    assert.deepEqual(writtenTokens, ['token-1', 'token-2', 'token-2'])
    assert.equal(csrfFetches, 2)
    assert.equal(probes, 0)
    assert.equal(expired, 0)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})

test('late 401 cannot log out a newer session', async () => {
  const old = globalThis.fetch
  let resolveOld, expired = 0
  clearCsrfToken()
  setSessionExpiredHandler(() => { expired += 1 })
  globalThis.fetch = async url => {
    if (url === '/api/v1/items') return new Promise(resolve => { resolveOld = resolve })
    return reply(200, { data: [] })
  }
  try {
    const older = get('/api/v1/items')
    for (let i = 0; i < 20 && !resolveOld; i += 1) await Promise.resolve()
    assert.ok(resolveOld)
    clearCsrfToken()
    resolveOld(reply(401, { errorCode: 'UNAUTHENTICATED', message: 'old unauthorized' }))
    await assert.rejects(() => older, e => e.status === 401)
    assert.equal(expired, 0)
  } finally {
    globalThis.fetch = old
    clearCsrfToken()
    setSessionExpiredHandler(null)
  }
})
