import { test, expect } from '@playwright/test'

const reply = (route, status, body) => route.fulfill({
  status, contentType: 'application/json', body: JSON.stringify(body),
})

test('anonymous user is redirected to login rather than seeing manufacturing data', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route =>
    reply(route, 401, { errorCode: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' }))
  await page.goto('/orders')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await expect(page.getByRole('heading', { name: '관리자 로그인' })).toBeVisible()
  await expect(page.locator('.sidebar')).toHaveCount(0)
})

test('viewer may browse but cannot access account screen or create inventory', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route =>
    reply(route, 200, { data: { username: 'viewer-example', role: 'VIEWER' } }))
  await page.route('**/api/v1/inventory?*', route =>
    reply(route, 200, { data: { content: [], totalElements: 0, totalPages: 0 } }))
  await page.goto('/inventory')
  await expect(page.locator('.topbar')).toContainText('조회 전용')
  await expect(page.getByRole('button', { name: '원자재 입고' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: '계정 관리' })).toHaveCount(0)
  await page.goto('/users')
  await expect(page).toHaveURL('http://127.0.0.1:5173/')
})

test('admin login submits CSRF-protected form and exposes account management', async ({ page }) => {
  await page.route('**/api/v1/**', route =>
    reply(route, 200, { data: { content: [], totalElements: 0, totalPages: 0 } }))
  await page.route('**/api/v1/auth/me', route =>
    reply(route, 401, { errorCode: 'UNAUTHENTICATED', message: '로그인이 필요합니다.' }))
  await page.route('**/api/v1/auth/csrf', route =>
    reply(route, 200, { data: { token: 'masked-csrf-test', headerName: 'X-CSRF-TOKEN' } }))
  let csrfWasSent = false
  await page.route('**/api/v1/auth/login', route => {
    csrfWasSent = route.request().headers()['x-csrf-token'] === 'masked-csrf-test'
    return reply(route, 200, { data: { username: 'ciadmin', role: 'ADMIN' } })
  })
  await page.goto('/login')
  await page.locator('input[name=username]').fill('ciadmin')
  await page.locator('input[name=password]').fill('some-test-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL('http://127.0.0.1:5173/')
  expect(csrfWasSent).toBe(true)
  await expect(page.getByRole('link', { name: '계정 관리' })).toBeVisible()
})

test('VIEWER sees the production order detail and material table but no write commands', async ({ page }) => {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname
    const item = { id: 105, name: '실습 완제품', sku: 'FG-VIEW', itemType: 'FINISHED_GOOD' }
    const material = { id: 205, name: '실습 원자재', sku: 'RM-VIEW', itemType: 'RAW_MATERIAL' }
    const order = { id: 21, orderNo: 'WO-VIEW-21', finishedItemId: 105,
      plannedQty: 5, status: 'DRAFT', createdAt: '2026-11-12T00:00:00Z' }
    const result = path === '/api/v1/auth/me'
      ? { username: 'viewer-example', role: 'VIEWER' }
      : path === '/api/v1/work-orders'
        ? { content: [order], totalElements: 1, totalPages: 1 }
        : path === '/api/v1/work-orders/21'
          ? { ...order, materials: [{ materialItemId: 205, requiredQty: 10 }] }
          : path === '/api/v1/items/lookup' ? [item, material] : null
    return result === null
      ? route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Unknown route' }) })
      : route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data: result }) })
  })
  await page.goto('/orders?id=21')
  const detail = page.locator('.detail-panel')
  await expect(detail).toContainText('WO-VIEW-21')
  await expect(detail.getByRole('table', { name: '선택 생산지시 소요 원자재' })).toContainText('실습 원자재')
  await expect(detail.getByText('현재 계정은 조회 전용입니다.')).toBeVisible()
  await expect(detail.getByRole('button', { name: '자재 예약' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '생산지시 생성' })).toHaveCount(0)
})

test('VIEWER can inspect inventory LOT values but cannot submit a stock receipt', async ({ page }) => {
  const summary = { itemId: 1, name: '조회 전용 강판', sku: 'RAW-VIEW-001',
    itemType: 'RAW_MATERIAL', onHand: 12, reserved: 2, available: 10 }
  const lot = { id: 10, itemId: 1, lotCode: 'VIEW-LOT-01', sourceType: 'RAW_RECEIPT',
    receivedAt: '2026-11-12T00:00:00Z', cumulativeInQty: 12, onHand: 12,
    reserved: 2, available: 10 }
  await page.route('**/api/v1/auth/me', route =>
    reply(route, 200, { data: { username: 'viewer-example', role: 'VIEWER' } }))
  await page.route('**/api/v1/inventory?*', route =>
    reply(route, 200, { data: { content: [summary], totalPages: 1, totalElements: 1 } }))
  await page.route('**/api/v1/inventory/lots?*', route =>
    reply(route, 200, { data: { content: [lot], totalPages: 1, totalElements: 1 } }))
  await page.goto('/inventory?itemId=1')
  await expect(page.getByRole('heading', { name: '조회 전용 강판 · LOT 상세' })).toBeVisible()
  await expect(page.getByRole('table', { name: '선택 품목 LOT별 재고' })).toContainText('VIEW-LOT-01')
  await expect(page.getByRole('button', { name: '원자재 입고', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '선택 원자재 입고' })).toHaveCount(0)
})
