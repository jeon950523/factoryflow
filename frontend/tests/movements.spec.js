import { test, expect } from '@playwright/test'

const raw = { id: 205, sku: 'RM-LATE-205', name: '뒤늦게 등록된 강판', itemType: 'RAW_MATERIAL' }
const finished = { id: 305, sku: 'FG-LATE-305', name: '완제품 컨트롤러', itemType: 'FINISHED_GOOD' }
const reply = (route, data) => route.fulfill({
  status: 200, contentType: 'application/json', body: JSON.stringify({ data }),
})
const event = (id, itemId, movementType, referenceType, referenceId, quantity) => ({
  id, itemId, inventoryLotId: itemId + 1, movementType, quantity, referenceType,
  referenceId, createdAt: '2026-11-12T00:00:00Z',
})

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/v1/auth/me') return reply(route, { username: 'fixture-viewer', role: 'VIEWER' })
    if (path === '/api/v1/items/205') return reply(route, raw)
    if (path === '/api/v1/items/305') return reply(route, finished)
    if (path === '/api/v1/items') {
      const keyword = url.searchParams.get('keyword') || ''
      const results = keyword ? [raw, finished].filter(item =>
        (item.name + item.sku).toLowerCase().includes(keyword.toLowerCase())) : [finished]
      return reply(route, { content: results, totalElements: results.length, totalPages: 1 })
    }
    if (path === '/api/v1/stock-movements') {
      const id = Number(url.searchParams.get('itemId'))
      const pageNo = Number(url.searchParams.get('page'))
      const rows = id === 205
        ? [event(pageNo ? 21 : 1, 205, 'RAW_CONSUMPTION', 'WORK_ORDER', 7, 10)]
        : [event(2, 305, 'FINISHED_SHIPMENT', 'SHIPMENT', 9, 2)]
      return reply(route, { content: rows, totalElements: id === 205 ? 21 : 1, totalPages: id === 205 ? 2 : 1 })
    }
    return route.fulfill({ status: 404, contentType: 'application/json',
      body: JSON.stringify({ errorCode: 'UNMOCKED', message: path }) })
  })
})

test('search can select a newly registered item without a first-100-item limit', async ({ page }) => {
  await page.goto('/movements')
  await expect(page.getByRole('table', { name: '선택 품목 재고 이동 이력' })).toHaveCount(0)
  await page.locator('.erp-movement-filter .item-search-toggle').click()
  await page.getByRole('searchbox', { name: '품목 코드 또는 이름 검색' }).fill('RM-LATE-205')
  await page.getByRole('button', { name: '검색', exact: true }).click()
  await page.locator('.item-search-option').filter({ hasText: '뒤늦게 등록된 강판' }).click()
  await expect(page).toHaveURL(/\/movements\?itemId=205/)
  const table = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(table).toContainText('−10')
  await expect(table).toContainText('생산지시')
  await expect(table.getByRole('link', { name: '생산지시 #7 확인' })).toHaveAttribute('href', '/orders?id=7')
  await expect(page.getByRole('link', { name: '현재 LOT 재고 보기' })).toHaveAttribute('href', '/inventory?itemId=205')
})

test('direct URL and pagination restore selected movement history after reload', async ({ page }) => {
  await page.goto('/movements?itemId=205&page=1')
  const table = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(page.getByRole('heading', { name: '재고 이동 이력 21건' })).toBeVisible()
  await expect(table).toContainText('#21')
  await page.reload()
  await expect(page).toHaveURL(/itemId=205&page=1/)
  await expect(table).toContainText('#21')
  await page.getByRole('button', { name: '선택한 품목 지우기' }).click()
  await expect(page).toHaveURL('http://127.0.0.1:5173/movements')
  await expect(table).toHaveCount(0)
})

test('invalid item query never sends a stock movement request', async ({ page }) => {
  let reads = 0
  await page.route('**/api/v1/stock-movements?*', route => { reads++; return route.continue() })
  await page.goto('/movements?itemId=invalid&page=-3')
  await expect(page.getByText('조회할 원자재 또는 완제품을 먼저 선택하세요.')).toBeVisible()
  expect(reads).toBe(0)
})

test('stale out-of-range page URL recovers to the last available movement page', async ({ page }) => {
  await page.goto('/movements?itemId=205&page=99')
  await expect(page).toHaveURL('http://127.0.0.1:5173/movements?itemId=205&page=1')
  await expect(page.getByRole('table', { name: '선택 품목 재고 이동 이력' })).toContainText('#21')
})
