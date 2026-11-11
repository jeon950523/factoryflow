import { test } from 'node:test'
import assert from 'node:assert/strict'
import { lookupItemNames, itemNameOrId, itemSkuOrId } from './itemNames.js'

test('batch lookup resolves 101st item and removes duplicate IDs', async () => {
  const previous = globalThis.fetch
  const calls = []
  globalThis.fetch = async (url) => {
    calls.push(url)
    return { ok: true, status: 200, json: async () => ({
      data: [{ id: 105, sku: 'FG-105', name: '브래킷 105' }, { id: 2, sku: 'RM-002', name: '철판' }],
    }) }
  }
  try {
    const names = await lookupItemNames([105, 2, 105, null, -1])
    assert.equal(calls.length, 1)
    assert.match(calls[0], /ids=105%2C2/)
    assert.equal(itemNameOrId(names, 105, '완제품'), '브래킷 105')
    assert.equal(itemSkuOrId(names, 2), 'RM-002')
    assert.equal(itemNameOrId(names, 999, '원자재'), '원자재 #999')
  } finally {
    globalThis.fetch = previous
  }
})

test('batch lookup splits 105 identifiers into three requests of at most 50', async () => {
  const previous = globalThis.fetch
  const sizes = []
  globalThis.fetch = async url => {
    const csv = new URL(url, 'http://localhost').searchParams.get('ids')
    const ids = csv.split(',')
    sizes.push(ids.length)
    return { ok: true, status: 200, json: async () => ({ data: ids.map(id => ({
      id: Number(id), sku: 'SKU-' + id, name: '품목 ' + id,
    })) }) }
  }
  try {
    const names = await lookupItemNames(Array.from({ length: 105 }, (_, i) => i + 1))
    assert.deepEqual(sizes, [50, 50, 5])
    assert.equal(itemNameOrId(names, 105), '품목 105')
  } finally {
    globalThis.fetch = previous
  }
})

test('empty IDs do not issue any network request', async () => {
  const previous = globalThis.fetch
  globalThis.fetch = () => { throw Error('should not fetch') }
  try {
    assert.deepEqual(await lookupItemNames([null, '', 0, -1]), {})
  } finally {
    globalThis.fetch = previous
  }
})

test('AbortController is passed to each batch request', async () => {
  const previous = globalThis.fetch
  const controller = new AbortController()
  let received
  globalThis.fetch = async (_url, options) => {
    received = options.signal
    return { ok: true, status: 200, json: async () => ({ data: [] }) }
  }
  try {
    await lookupItemNames([101], { signal: controller.signal })
    assert.strictEqual(received, controller.signal)
  } finally {
    globalThis.fetch = previous
  }
})
