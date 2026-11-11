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
