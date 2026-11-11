import { test } from 'node:test'
import assert from 'node:assert/strict'
import { params, api, get, ApiError } from './api.js'

test('query params omit null and preserve zero', () => {
  assert.equal(params({ itemId: 12, page: 0, keyword: '' }), '?itemId=12&page=0')
  assert.equal(params({ value: '철판 A' }), '?value=%EC%B2%A0%ED%8C%90+A')
})

test('successful response returns data only', async () => {
  const prev = globalThis.fetch
  globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ data: { id: 12 } }) })
  try { assert.deepEqual(await api('/api/v1/items/12'), { id: 12 }) } finally { globalThis.fetch = prev }
})

test('business errors keep status and errorCode', async () => {
  const prev = globalThis.fetch
  globalThis.fetch = async () => ({ ok: false, status: 409, json: async () => ({ errorCode: 'STATE_CONFLICT', message: '상태 충돌', traceId: 'trace-1' }) })
  try {
    await assert.rejects(() => api('/api/v1/work-orders/1/start'), error => error instanceof ApiError && error.status === 409 && error.code === 'STATE_CONFLICT' && error.traceId === 'trace-1')
  } finally { globalThis.fetch = prev }
})

test('network failure is reported explicitly', async () => {
  const prev = globalThis.fetch
  globalThis.fetch = async () => { throw new TypeError('offline') }
  try { await assert.rejects(() => api('/api/v1/items'), error => error instanceof ApiError && error.code === 'NETWORK_ERROR') }
  finally { globalThis.fetch = prev }
})


test('GET forwards server search filters and AbortController signal', async () => {
  const previous = globalThis.fetch
  const controller = new AbortController()
  let requestedUrl = ''
  let requestedSignal = null
  globalThis.fetch = async (url, options) => {
    requestedUrl = url
    requestedSignal = options.signal
    return { ok: true, status: 200, json: async () => ({ data: { content: [], totalPages: 0 } }) }
  }
  try {
    await get('/api/v1/items', { itemType: 'RAW_MATERIAL', keyword: '철판', page: 10, size: 10 }, { signal: controller.signal })
    assert.match(requestedUrl, /itemType=RAW_MATERIAL/)
    assert.match(requestedUrl, /keyword=%EC%B2%A0%ED%8C%90/)
    assert.match(requestedUrl, /page=10/)
    assert.strictEqual(requestedSignal, controller.signal)
  } finally {
    globalThis.fetch = previous
  }
})

test('AbortError stays distinguishable from real connection failures', async () => {
  const previous = globalThis.fetch
  globalThis.fetch = async () => {
    const error = new Error('aborted')
    error.name = 'AbortError'
    throw error
  }
  try {
    await assert.rejects(() => get('/api/v1/items'), error => error.name === 'AbortError')
  } finally {
    globalThis.fetch = previous
  }
})
