import { test, expect } from '@playwright/test'

test('IndexedDB stock movement trail shows real movement direction and survives reload', async ({ page }) => {
  await page.goto('/movements?itemId=1')
  const table = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(page.getByRole('heading', { name: /재고 이동 이력/ })).toBeVisible()
  await expect(page.locator('.erp-movement-filter .item-search-toggle')).toContainText('산업용 강판')
  await expect(table).toContainText('+50')
  await expect(table).toContainText('−10')
  await expect(table.getByRole('link', { name: '생산지시 #1 확인' })).toHaveAttribute('href', '/orders?id=1')
  await page.reload()
  await expect(table).toContainText('−10')
  await expect(page).toHaveURL(/\/movements\?itemId=1/)
})

test('finished shipment audit reference links to recorded FIFO allocation, not current balance', async ({ page }) => {
  await page.goto('/movements?itemId=4')
  const table = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(table).toContainText('−2')
  await expect(table).toContainText('완제품 출고')
  await table.getByRole('link', { name: '완제품 출고 #1 확인' }).click()
  await expect(page).toHaveURL(/\/shipments\?id=1/)
  await expect(page.getByRole('table', { name: 'LOT별 출고 배분 내역' })).toContainText('LOT #4')
})
