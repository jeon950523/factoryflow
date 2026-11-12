import { test, expect } from '@playwright/test'

const product = { id: 105, sku: 'FG-105', name: '브래킷 105', itemType: 'FINISHED_GOOD' }
const raw = { id: 205, sku: 'RM-205', name: '철판 205', itemType: 'RAW_MATERIAL' }
const first = { id: 21, orderNo: 'WO-00021', finishedItemId: 105, plannedQty: 10, status: 'DRAFT' }
const second = { id: 22, orderNo: 'WO-00022', finishedItemId: 105, plannedQty: 20, status: 'RESERVED' }
const reply = (route, data) => route.fulfill({
  status: 200, contentType: 'application/json', body: JSON.stringify({ data }),
})

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/v1/auth/me') return reply(route, { username: 'fixture-admin', role: 'ADMIN' })
    if (path === '/api/v1/auth/csrf') return reply(route, { token: 'fixture-csrf', headerName: 'X-CSRF-TOKEN' })
    if (path === '/api/v1/items/lookup') return reply(route, [product, raw])
    if (path === '/api/v1/items') return reply(route, { content: [product, raw], totalPages: 1, totalElements: 2 })
    if (path === '/api/v1/items/105') return reply(route, product)
    if (path === '/api/v1/items/106') return reply(route,
      { id: 106, sku: 'FG-106', name: '브래킷 106', itemType: 'FINISHED_GOOD', active: true })
    return route.fulfill({ status: 404, contentType: 'application/json',
      body: JSON.stringify({ message: 'Unmocked API', errorCode: 'NOT_MOCKED' }) })
  })
})

test('fast second production order selection must win over delayed first response', async ({ page }) => {
  let started
  let release
  const firstStarted = new Promise(resolve => { started = resolve })
  const firstRelease = new Promise(resolve => { release = resolve })

  await page.route('**/api/v1/work-orders?*', route => reply(route, {
    content: [first, second], totalPages: 1, totalElements: 2,
  }))
  await page.route('**/api/v1/work-orders/21', async route => {
    started()
    await firstRelease
    try { await reply(route, { ...first, materials: [{ materialItemId: 205, requiredQty: 20 }] }) } catch { /* canceled */ }
  })
  await page.route('**/api/v1/work-orders/22', route => reply(route, {
    ...second, materials: [{ materialItemId: 205, requiredQty: 40 }],
  }))

  await page.goto('/orders')
  await page.getByRole('button', { name: 'WO-00021' }).click()
  await firstStarted
  await page.getByRole('button', { name: 'WO-00022' }).click()
  await expect(page.locator('.detail-panel')).toContainText('WO-00022')
  release()
  await expect(page.locator('.detail-panel')).not.toContainText('WO-00021')
  await expect(page.locator('.detail-panel')).toContainText('40 EA')
})

test('fast second shipment selection must not be overwritten by first detail response', async ({ page }) => {
  let started, release
  const firstStarted = new Promise(resolve => { started = resolve })
  const firstRelease = new Promise(resolve => { release = resolve })
  const a = { id: 55, requestKey: 'SHIP-55', finishedItemId: 105, quantity: 2 }
  const b = { id: 56, requestKey: 'SHIP-56', finishedItemId: 105, quantity: 5 }
  await page.route('**/api/v1/shipments?*', route => reply(route, {
    content: [a, b], totalPages: 1, totalElements: 2,
  }))
  await page.route('**/api/v1/shipments/55', async route => {
    started()
    await firstRelease
    try { await reply(route, { ...a, allocations: [{ lotId: 10, quantity: 2 }] }) } catch { /* canceled */ }
  })
  await page.route('**/api/v1/shipments/56', route => reply(route, {
    ...b, allocations: [{ lotId: 11, quantity: 5 }],
  }))
  await page.goto('/shipments')
  await page.getByRole('button', { name: 'SHIP-55' }).click()
  await firstStarted
  await page.getByRole('button', { name: 'SHIP-56' }).click()
  await expect(page.locator('.detail-panel')).toContainText('SHIP-56')
  release()
  await expect(page.locator('.detail-panel')).not.toContainText('SHIP-55')
  await expect(page.locator('.detail-panel')).toContainText('5 EA')
})

test('switching BOM item ignores prior slow BOM lookup', async ({ page }) => {
  let started, release
  const firstStarted = new Promise(resolve => { started = resolve })
  const firstRelease = new Promise(resolve => { release = resolve })
  const other = { id: 106, sku: 'FG-106', name: '브래킷 106', itemType: 'FINISHED_GOOD', active: true }
  await page.route('**/api/v1/items?*', route => reply(route, {
    content: [product, other], totalPages: 1, totalElements: 2,
  }))
  await page.route('**/api/v1/boms/105', async route => {
    started()
    await firstRelease
    try { await reply(route, { finishedItemId: 105, components: [{ rawItemId: 205, qtyPerUnit: 2 }] }) } catch { /* canceled */ }
  })
  await page.route('**/api/v1/boms/106', route => reply(route, {
    finishedItemId: 106, components: [{ rawItemId: 205, qtyPerUnit: 4 }],
  }))
  await page.goto('/items')
  await page.getByRole('button', { name: '브래킷 105' }).click()
  await firstStarted
  await page.getByRole('button', { name: '브래킷 106' }).click()
  await expect(page.locator('.detail-panel')).toContainText('브래킷 106')
  await expect(page.locator('.detail-panel')).toContainText('4')
  release()
  await expect(page.locator('.detail-panel')).not.toContainText('브래킷 105')
})
