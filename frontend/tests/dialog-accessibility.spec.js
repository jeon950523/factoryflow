import { test, expect } from '@playwright/test'

const reply = (route, data, status = 200) => route.fulfill({
  status, contentType: 'application/json', body: JSON.stringify({ data }),
})

test('admin account drawer traps keyboard focus and cannot close during save', async ({ page }) => {
  let beginWrite, finishWrite
  const started = new Promise(resolve => { beginWrite = resolve })
  const releaseWrite = new Promise(resolve => { finishWrite = resolve })

  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/auth/me') {
      return reply(route, { username: 'fixture-admin', role: 'ADMIN' })
    }
    if (url.pathname === '/api/v1/auth/csrf') {
      return reply(route, { token: 'test-csrf', headerName: 'X-CSRF-TOKEN' })
    }
    if (url.pathname === '/api/v1/admin/users') {
      if (route.request().method() === 'POST') {
        beginWrite()
        await releaseWrite
        return reply(route, { id: 1, username: 'qaadmin' }, 201)
      }
      return reply(route, [])
    }
    return route.fulfill({ status: 404, contentType: 'application/json',
      body: JSON.stringify({ errorCode: 'NOT_MOCKED', message: url.pathname }) })
  })

  await page.goto('/users')
  const opener = page.getByRole('button', { name: '계정 발급', exact: true }).first()
  await opener.click()
  const dialog = page.getByRole('dialog', { name: '계정 발급' })
  const username = dialog.locator('input[autocomplete="off"]')
  await expect(username).toBeFocused()
  await username.fill('qaadmin')
  await dialog.locator('input[type="password"]').fill('test-password-123')
  const submit = dialog.locator('button[type="submit"]')
  await submit.click()
  await started
  await expect(submit).toBeDisabled()
  await page.keyboard.press('Escape')
  await expect(dialog).toBeVisible()
  await expect(dialog.getByRole('button', { name: '닫기' })).toBeDisabled()

  finishWrite()
  await expect(dialog).toHaveCount(0)
  await expect(opener).toBeFocused()
})
