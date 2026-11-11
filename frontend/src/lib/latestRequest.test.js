import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createLatestRequest, isAbortError } from './latestRequest.js'

test('newer selection invalidates older result and aborts its fetch', () => {
  const gate = createLatestRequest()
  const old = gate.begin()
  assert.equal(old.isCurrent(), true)
  assert.equal(old.signal.aborted, false)
  const next = gate.begin()
  assert.equal(old.signal.aborted, true)
  assert.equal(old.isCurrent(), false)
  assert.equal(next.isCurrent(), true)
  gate.cancel()
  assert.equal(next.signal.aborted, true)
  assert.equal(next.isCurrent(), false)
})

test('aborted requests remain distinguishable from API failures', () => {
  assert.equal(isAbortError({ name: 'AbortError' }), true)
  assert.equal(isAbortError({ name: 'ApiError' }), false)
  assert.equal(isAbortError(null), false)
})
