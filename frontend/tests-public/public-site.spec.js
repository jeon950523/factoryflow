import { test, expect } from '@playwright/test'

// Runs against the public, read-only IndexedDB demo, not the local Spring server.
// A clean browser context ensures seeded sample data and no real server writes.
const scriptErrors = page => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  return errors
}

test('public movement page loads by direct URL and renders the TASK-014 audit table', async ({ page }) => {
  const errors = scriptErrors(page)
  const apiCalls = []
  page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/')) apiCalls.push(request.url())
  })
  const response = await page.goto('/movements?itemId=1', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  await expect(page.getByText('브라우저 체험 모드')).toBeVisible()
  await expect(page.locator('.erp-movement-sheet')).toBeVisible()
  await expect(page.locator('.erp-movement-filter .item-search-toggle')).toContainText('산업용 강판')
  const table = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(table).toContainText('+50')
  await expect(table).toContainText('−10')
  await expect(table.getByRole('link', { name: '생산지시 #1 확인' }))
    .toHaveAttribute('href', '/orders?id=1')
  await page.screenshot({ path: 'public-qa/movements.png', fullPage: true })
  expect(errors).toEqual([])
  expect(apiCalls).toEqual([])
})

test('public item/BOM and shipment pages render the TASK-013 table-based layout', async ({ page }) => {
  const errors = scriptErrors(page)
  let response = await page.goto('/items?itemId=4', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  await expect(page.getByRole('table', { name: '품목 기준 정보 목록' })).toBeVisible()
  await expect(page.getByRole('table', { name: '완제품 BOM 구성표' })).toContainText('산업용 강판')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: 'public-qa/items.png', fullPage: true })

  response = await page.goto('/shipments?id=1', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  await expect(page.getByRole('table', { name: 'LOT별 출고 배분 내역' })).toContainText('LOT #4')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: 'public-qa/shipments.png', fullPage: true })
  expect(errors).toEqual([])
})

test('public movement search can be operated with keyboard without a mouse', async ({ page }) => {
  const response = await page.goto('/movements', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  const toggle = page.locator('.erp-movement-filter .item-search-toggle')
  await toggle.focus()
  await expect(toggle).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(toggle).toHaveAttribute('aria-expanded', 'true')
  const input = page.getByRole('searchbox', { name: '품목 코드 또는 이름 검색' })
  await input.fill('RAW-PLATE-001')
  await input.press('Enter')
  const matching = page.locator('.erp-movement-filter .item-search-option')
    .filter({ hasText: 'RAW-PLATE-001' })
  await expect(matching).toBeVisible()
  await matching.focus()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/movements\?itemId=1/)
  await expect(page.getByRole('table', { name: '선택 품목 재고 이동 이력' }))
    .toContainText('원자재 소모')
})
