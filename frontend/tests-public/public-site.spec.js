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

test('TASK-017 published trace links resolve consistent BOM, LOT, production and shipment records', async ({ page }) => {
  const errors = scriptErrors(page)
  const response = await page.goto('/items?itemId=4', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  const bom = page.getByRole('table', { name: '완제품 BOM 구성표' })
  await bom.getByRole('link', { name: '산업용 강판 LOT 재고 확인' }).click()
  await expect(page).toHaveURL(/\/inventory\?itemType=RAW_MATERIAL&itemId=1$/)
  const lots = page.getByRole('table', { name: '선택 품목 LOT별 재고' })
  await expect(lots).toContainText('STEEL-A01')
  await page.getByRole('link', { name: '선택 품목 이동 이력' }).click()
  await expect(page).toHaveURL(/\/movements\?itemId=1$/)
  const movements = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(movements).toContainText('+50')
  await expect(movements).toContainText('−10')
  await movements.getByRole('link', { name: '생산지시 #1 확인' }).click()
  await expect(page).toHaveURL(/\/orders\?id=1$/)
  const order = page.locator('.erp-orders .detail-panel')
  await expect(order).toContainText('WO-DEMO-COMPLETE')
  await order.getByRole('link', { name: '완제품 LOT' }).click()
  await expect(page).toHaveURL(/\/inventory\?itemType=FINISHED_GOOD&itemId=4$/)
  await expect(lots).toContainText('FG-WO-1')
  await page.getByRole('link', { name: '선택 품목 이동 이력' }).click()
  await expect(movements).toContainText('−2')
  await movements.getByRole('link', { name: '완제품 출고 #1 확인' }).click()
  await expect(page).toHaveURL(/\/shipments\?id=1$/)
  const alloc = page.getByRole('table', { name: 'LOT별 출고 배분 내역' })
  await expect(alloc).toContainText('LOT #4')
  await expect(alloc).toContainText('2 EA')
  await page.getByRole('link', { name: '완제품 이동 이력' }).click()
  await expect(page).toHaveURL(/\/movements\?itemId=4$/)
  await page.screenshot({ path: 'public-qa/task-018-trace.png', fullPage: true })
  expect(errors).toEqual([])
})

test('TASK-017 selected order row keeps URL state after reload on deployed Vercel', async ({ page }) => {
  const errors = scriptErrors(page)
  await page.goto('/orders?status=DRAFT', { waitUntil: 'domcontentloaded' })
  await page.getByRole('table', { name: '생산지시 목록' })
    .getByRole('button', { name: 'WO-DEMO-READY', exact: true }).click()
  await expect(page).toHaveURL(/\/orders\?status=DRAFT&id=2$/)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('combobox', { name: '진행 상태' })).toHaveValue('DRAFT')
  await expect(page.locator('.erp-orders .detail-panel')).toContainText('WO-DEMO-READY')
  await page.screenshot({ path: 'public-qa/task-018-orders.png', fullPage: true })
  expect(errors).toEqual([])
})

test('public UI handles malformed movement URL, keyboard dialog and enlarged text without writes', async ({ page }) => {
  const errors = scriptErrors(page)
  const serverCalls = []
  page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/')) serverCalls.push(request.url())
  })
  const response = await page.goto('/movements?itemId=oops&page=-1', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  await expect(page.getByText('조회할 원자재 또는 완제품을 먼저 선택하세요.')).toBeVisible()

  await page.goto('/items?itemId=4', { waitUntil: 'domcontentloaded' })
  // CSS zoom is a rendering stress check, not a claim about Windows OS scaling.
  await page.evaluate(() => { document.documentElement.style.zoom = '125%' })
  await expect(page.getByRole('table', { name: '완제품 BOM 구성표' })).toBeVisible()
  await page.getByRole('button', { name: '품목 등록', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '품목 등록' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByPlaceholder('예: RM-SHEET-001')).toBeFocused()
  await page.screenshot({ path: 'public-qa/task-018-zoom125-dialog.png', fullPage: true })
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('button', { name: '품목 등록', exact: true })).toBeFocused()
  expect(serverCalls).toEqual([])
  expect(errors).toEqual([])
})


test('TASK-020 deployed raw receipt retry restores same pending request and never double-receives', async ({ page }) => {
  const errors = scriptErrors(page)
  const apiCalls = []
  page.on('request', req => {
    if (new URL(req.url()).pathname.startsWith('/api/v1/')) apiCalls.push(req.url())
  })
  const response = await page.goto('/inventory', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  await expect(page.getByText('브라우저 체험 모드')).toBeVisible()

  const payload = { requestKey: 'REC-T021-PUBLIC-01', itemId: 1, lotCode: 'STEEL-A01', quantity: 2 }
  await page.evaluate(value => {
    sessionStorage.setItem('factoryflow:pending-raw-receipt:v1', JSON.stringify(value))
  }, payload)
  await page.reload({ waitUntil: 'domcontentloaded' })

  const dialog = page.getByRole('dialog', { name: '원자재 입고' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('alert')).toContainText(payload.requestKey)
  await expect(dialog.getByRole('spinbutton', { name: '입고 수량 (EA)' })).toBeDisabled()
  await dialog.getByRole('button', { name: '취소' }).click()
  await page.getByRole('button', { name: '미확인 입고 요청 확인' }).click()
  await expect(dialog.getByRole('alert')).toContainText(payload.requestKey)
  await dialog.getByRole('button', { name: '동일 요청 재시도' }).click()
  await expect(dialog).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('factoryflow:pending-raw-receipt:v1'))).toBeNull()

  await page.goto('/inventory?itemType=RAW_MATERIAL&itemId=1')
  await expect(page.locator('.erp-lot-summary')).toContainText('현재고 42 EA')

  // Simulate an acknowledged receipt whose browser falsely retained the pending marker.
  // The demo's matching requestKey must replay, not append another receipt.
  await page.evaluate(value => {
    sessionStorage.setItem('factoryflow:pending-raw-receipt:v1', JSON.stringify(value))
  }, payload)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '동일 요청 재시도' }).click()
  await expect(dialog).toHaveCount(0)
  await expect(page.locator('.erp-lot-summary')).toContainText('현재고 42 EA')

  await page.goto('/movements?itemId=1')
  const movementRows = page.getByRole('table', { name: '선택 품목 재고 이동 이력' }).locator('tbody tr')
  await expect(movementRows.filter({ hasText: '+2' })).toHaveCount(1)
  await page.screenshot({ path: 'public-qa/task-021-receipt-retry.png', fullPage: true })
  expect(apiCalls).toEqual([])
  expect(errors).toEqual([])
})

test('TASK-020 deployed shipment retry restores pending request and never double-deducts FIFO stock', async ({ page }) => {
  const errors = scriptErrors(page)
  const apiCalls = []
  page.on('request', req => {
    if (new URL(req.url()).pathname.startsWith('/api/v1/')) apiCalls.push(req.url())
  })
  const response = await page.goto('/shipments', { waitUntil: 'domcontentloaded' })
  expect(response?.status()).toBe(200)
  await expect(page.getByText('브라우저 체험 모드')).toBeVisible()

  const payload = { requestKey: 'SHIP-T021-PUBLIC-01', finishedItemId: 4, quantity: 1 }
  await page.evaluate(value => {
    sessionStorage.setItem('factoryflow:pending-finished-shipment:v1', JSON.stringify(value))
  }, payload)
  await page.reload({ waitUntil: 'domcontentloaded' })

  const dialog = page.getByRole('dialog', { name: '완제품 출고 등록' })
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('alert')).toContainText(payload.requestKey)
  await expect(dialog.getByRole('spinbutton', { name: '출고 수량 (EA)' })).toBeDisabled()
  await dialog.getByRole('button', { name: '취소' }).click()
  await page.getByRole('button', { name: '미확인 출고 요청 확인' }).click()
  await dialog.getByRole('button', { name: '동일 요청 재시도' }).click()
  await expect(dialog).toHaveCount(0)
  expect(await page.evaluate(() => sessionStorage.getItem('factoryflow:pending-finished-shipment:v1'))).toBeNull()
  await expect(page.getByRole('table', { name: 'LOT별 출고 배분 내역' })).toContainText('1 EA')

  await page.goto('/inventory?itemType=FINISHED_GOOD&itemId=4')
  await expect(page.locator('.erp-lot-summary')).toContainText('현재고 2 EA')

  await page.goto('/shipments')
  await page.evaluate(value => {
    sessionStorage.setItem('factoryflow:pending-finished-shipment:v1', JSON.stringify(value))
  }, payload)
  await page.reload({ waitUntil: 'domcontentloaded' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: '동일 요청 재시도' }).click()
  await expect(dialog).toHaveCount(0)

  await page.goto('/inventory?itemType=FINISHED_GOOD&itemId=4')
  await expect(page.locator('.erp-lot-summary')).toContainText('현재고 2 EA')
  await page.goto('/movements?itemId=4')
  const movementRows = page.getByRole('table', { name: '선택 품목 재고 이동 이력' }).locator('tbody tr')
  await expect(movementRows.filter({ hasText: '−1' })).toHaveCount(1)
  await page.screenshot({ path: 'public-qa/task-021-shipment-retry.png', fullPage: true })
  expect(apiCalls).toEqual([])
  expect(errors).toEqual([])
})
