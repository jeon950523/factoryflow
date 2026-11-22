import { test, expect } from '@playwright/test'

// Browser-only request-boundary assertions; server commit/replay is covered by live MySQL CI.
const operations = [
  { kind: 'receipt', page: '/inventory', opener: '원자재 입고',
    endpoint: '/api/v1/stock-receipts', key: 'factoryflow:pending-raw-receipt:v1',
    item: { id: 205, sku: 'RM-PREFLIGHT', name: '검증 원자재', itemType: 'RAW_MATERIAL' } },
  { kind: 'shipment', page: '/shipments', opener: '출고 등록',
    endpoint: '/api/v1/shipments', key: 'factoryflow:pending-finished-shipment:v1',
    item: { id: 305, sku: 'FG-PREFLIGHT', name: '검증 완제품', itemType: 'FINISHED_GOOD' } },
]

const reply = (route, data, status = 200) => route.fulfill({
  status, contentType: 'application/json', body: JSON.stringify({ data }),
})

async function installApi(page, operation, onWrite) {
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname
    const item = operation.item
    if (route.request().method() === 'POST' && path === operation.endpoint) return onWrite(route)
    if (path === '/api/v1/auth/me') return reply(route, { username: 'fixture-admin', role: 'ADMIN' })
    if (path === '/api/v1/auth/csrf') return reply(route, { token: 'fixture-token' })
    if (path === '/api/v1/items') return reply(route,
      { content: [item], totalElements: 1, totalPages: 1 })
    if (path === '/api/v1/items/lookup') return reply(route, [item])
    if (path === '/api/v1/inventory') return reply(route, {
      content: [{ itemId: item.id, ...item, onHand: 10, reserved: 0, available: 10 }],
      totalElements: 1, totalPages: 1,
    })
    if (path === '/api/v1/inventory/lots') return reply(route, {
      content: [], totalElements: 0, totalPages: 0,
    })
    if (path === '/api/v1/shipments') return reply(route,
      { content: [], totalElements: 0, totalPages: 0 })
    if (path === '/api/v1/shipments/91') return reply(route, {
      id: 91, requestKey: 'SHIP-DUMMY', finishedItemId: item.id, quantity: 1, allocations: [],
    })
    return route.fulfill({ status: 404, contentType: 'application/json',
      body: JSON.stringify({ message: path, errorCode: 'UNMOCKED' }) })
  })
}

async function fillCommand(page, operation) {
  await page.goto(operation.page)
  await page.getByRole('button', { name: operation.opener, exact: true }).click()
  const dialog = page.locator('.dialog')
  await dialog.locator('.item-search-toggle').click()
  await dialog.locator('.item-search-option').filter({ hasText: operation.item.sku }).click()
  await dialog.locator('input[type=number]').fill('2')
  if (operation.kind === 'receipt') {
    await dialog.locator('input[placeholder^=LOT-]').fill('LOT-PREFLIGHT')
  }
  return dialog
}

for (const operation of operations) {
  test(`TASK-022 ${operation.kind} stores exact request before POST and clears on success`, async ({ page }) => {
    let observed
    await installApi(page, operation, async route => {
      const originalBody = route.request().postDataJSON()
      const stored = await page.evaluate(key => sessionStorage.getItem(key), operation.key)
      observed = { stored: stored ? JSON.parse(stored) : null, originalBody }
      return reply(route, { id: 91, ...originalBody, lotCode: originalBody.lotCode }, 201)
    })
    const dialog = await fillCommand(page, operation)
    await dialog.locator('button[type=submit]').click()
    await expect(dialog).toHaveCount(0)
    expect(observed?.stored).toEqual(observed?.originalBody)
    expect(await page.evaluate(key => sessionStorage.getItem(key), operation.key)).toBeNull()
  })

  test(`TASK-022 ${operation.kind} blocks POST if write-ahead storage is denied`, async ({ page }) => {
    await page.addInitScript(() => {
      const original = Storage.prototype.setItem
      Storage.prototype.setItem = function (key, value) {
        if (key.startsWith('factoryflow:pending-')) throw new DOMException('blocked', 'QuotaExceededError')
        return original.call(this, key, value)
      }
    })
    let posts = 0
    await installApi(page, operation, async route => {
      posts++
      return reply(route, { id: 91, ...route.request().postDataJSON() }, 201)
    })
    const dialog = await fillCommand(page, operation)
    await dialog.locator('button[type=submit]').click()
    await expect(dialog).toBeVisible()
    await expect(page.locator('.toast')).toContainText('전송하지 않았습니다')
    expect(posts).toBe(0)
    await expect(dialog.locator('button[type=submit]')).toBeEnabled()
  })
}
