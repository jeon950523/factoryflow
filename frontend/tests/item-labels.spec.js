import { test, expect } from '@playwright/test'

// Browser-only smoke: real Vue rendering and clicks; API data is deliberately mocked.
// Spring/MySQL 8.4 integration is checked separately in Backend CI.
const items = [
  { id: 105, sku: 'FG-105', name: '브래킷 105', itemType: 'FINISHED_GOOD', unit: 'EA', active: true, createdAt: '2026-11-11T00:00:00' },
  { id: 205, sku: 'RM-205', name: '철판 205', itemType: 'RAW_MATERIAL', unit: 'EA', active: true, createdAt: '2026-11-11T00:00:00' },
]
const order = { id: 21, orderNo: 'WO-00021', finishedItemId: 105, plannedQty: 10, status: 'DRAFT', createdAt: '2026-11-11T00:00:00' }
const shipment = { id: 55, requestKey: 'SHIP-55', finishedItemId: 105, quantity: 2, createdAt: '2026-11-11T00:00:00' }

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    const name = url.pathname
    let data
    if (name === '/api/v1/items/lookup') {
      const ids = (url.searchParams.get('ids') ?? '').split(',').map(Number)
      data = items.filter(item => ids.includes(item.id))
    } else if (name === '/api/v1/items') {
      const type = url.searchParams.get('itemType')
      data = { content: type ? items.filter(item => item.itemType === type) : [items[0]],
        totalPages: 1, totalElements: 1 }
    } else if (name === '/api/v1/work-orders') {
      data = { content: [order], totalPages: 1, totalElements: 1 }
    } else if (name === '/api/v1/work-orders/21') {
      data = { ...order, materials: [{ materialItemId: 205, requiredQty: 20 }] }
    } else if (name === '/api/v1/boms/105') {
      data = { finishedItemId: 105, components: [{ rawItemId: 205, qtyPerUnit: 2 }] }
    } else if (name === '/api/v1/shipments') {
      data = { content: [shipment], totalPages: 1, totalElements: 1 }
    } else if (name === '/api/v1/shipments/55') {
      data = { ...shipment, allocations: [{ lotId: 77, quantity: 2 }] }
    } else {
      return route.fulfill({ status: 404, contentType: 'application/json',
        body: JSON.stringify({ message: 'Unmocked endpoint: ' + name, errorCode: 'MOCK_ENDPOINT_MISSING' }) })
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data }) })
  })
})

test('production order shows late-registered product and raw material names', async ({ page }) => {
  await page.goto('/orders')
  await expect(page.getByRole('button', { name: 'WO-00021' })).toBeVisible()
  await expect(page.locator('.data-table tbody')).toContainText('브래킷 105')
  await page.getByRole('button', { name: 'WO-00021' }).click()
  await expect(page.locator('.detail-panel')).toContainText('철판 205')
  await expect(page.locator('.detail-panel')).toContainText('RM-205')
})

test('shipment list and selected detail resolve product ID 105', async ({ page }) => {
  await page.goto('/shipments')
  await expect(page.getByRole('button', { name: 'SHIP-55' })).toBeVisible()
  await expect(page.locator('.data-table tbody')).toContainText('브래킷 105')
  await page.getByRole('button', { name: 'SHIP-55' }).click()
  await expect(page.locator('.detail-panel')).toContainText('브래킷 105')
})

test('BOM details resolve a material ID beyond first 100', async ({ page }) => {
  await page.goto('/items')
  await expect(page.getByRole('button', { name: '브래킷 105' })).toBeVisible()
  await page.getByRole('button', { name: '브래킷 105' }).click()
  await expect(page.locator('.detail-panel')).toContainText('철판 205')
  await expect(page.locator('.detail-panel')).toContainText('RM-205')
})

test('production form can select product ID 105 from server search', async ({ page }) => {
  await page.goto('/orders')
  await page.getByRole('button', { name: '생산지시 생성' }).click()
  await page.locator('.dialog .item-search-toggle').click()
  await expect(page.locator('.item-search-option')).toContainText('브래킷 105')
  await page.locator('.item-search-option').filter({ hasText: '브래킷 105' }).click()
  await expect(page.locator('.dialog .item-search-toggle')).toContainText('FG-105 · 브래킷 105')
})


test('item name lookup outage keeps production order list usable with ID fallback', async ({ page }) => {
  await page.route('**/api/v1/items/lookup?*', route =>
    route.fulfill({ status: 503, contentType: 'application/json',
      body: JSON.stringify({ message: 'lookup unavailable', errorCode: 'LOOKUP_UNAVAILABLE' }) }))
  await page.goto('/orders')
  await expect(page.getByRole('button', { name: 'WO-00021' })).toBeVisible()
  await expect(page.locator('.data-table tbody')).toContainText('완제품 #105')
})
