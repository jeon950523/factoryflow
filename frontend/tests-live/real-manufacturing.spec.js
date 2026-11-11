import { test, expect } from '@playwright/test'

/**
 * No API mocks. Browser -> Vite proxy -> real Spring Boot -> MySQL 8.4.
 * Non-production CI database only; this application has no authentication yet.
 */
async function postData(request, path, payload) {
  const response = await request.post(path, { data: payload })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).data
}

async function getData(request, path) {
  const response = await request.get(path)
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

async function selectItem(page, sku) {
  await page.locator('.dialog .item-search-toggle').click()
  await page.locator('.dialog .item-search-bar input').fill(sku)
  await page.locator('.dialog .item-search-bar button').click()
  await expect(page.locator('.dialog .item-search-option').filter({ hasText: sku })).toBeVisible()
  await page.locator('.dialog .item-search-option').filter({ hasText: sku }).click()
  await expect(page.locator('.dialog .item-search-toggle')).toContainText(sku)
}

test('real backend and MySQL: receipt → reserve → consume → QC → shipment', async ({ page, request }) => {
  const id = Date.now().toString(36).toUpperCase()
  const rawSku = 'RM-LIVE-' + id
  const finishedSku = 'FG-LIVE-' + id
  const raw = await postData(request, '/api/v1/items',
    { sku: rawSku, name: '실DB 철판 ' + id, itemType: 'RAW_MATERIAL' })
  const finished = await postData(request, '/api/v1/items',
    { sku: finishedSku, name: '실DB 브래킷 ' + id, itemType: 'FINISHED_GOOD' })
  await postData(request, '/api/v1/boms',
    { finishedItemId: finished.id, components: [{ rawItemId: raw.id, qtyPerUnit: 2 }] })

  // The human-facing commands after initial master-data setup must all go through Vue buttons.
  await page.goto('/inventory')
  await page.getByRole('button', { name: '원자재 입고' }).click()
  await selectItem(page, rawSku)
  await page.locator('.dialog input[placeholder^="LOT-"]').fill('LOT-LIVE-' + id)
  await page.locator('.dialog input[type="number"]').fill('25')
  await page.locator('.dialog button[type="submit"]').click()
  await expect(page.locator('.dialog')).toHaveCount(0)
  let rawInventory = (await getData(request, '/api/v1/inventory?itemId=' + raw.id)).content[0]
  expect(rawInventory.onHand).toBe(25)
  expect(rawInventory.available).toBe(25)

  await page.goto('/orders')
  await page.getByRole('button', { name: '생산지시 생성' }).click()
  await selectItem(page, finishedSku)
  await page.locator('.dialog input[type="number"]').fill('10')
  await page.locator('.dialog button[type="submit"]').click()
  await expect(page.locator('.dialog')).toHaveCount(0)
  await expect(page.locator('.detail-panel')).toContainText('초안')
  await page.locator('.detail-panel .action-stack').getByRole('button', { name: '자재 예약' }).click()
  await expect(page.locator('.detail-panel .action-stack')).toContainText('생산 시작')
  rawInventory = (await getData(request, '/api/v1/inventory?itemId=' + raw.id)).content[0]
  expect(rawInventory.onHand).toBe(25)
  expect(rawInventory.reserved).toBe(20)
  await page.locator('.detail-panel .action-stack').getByRole('button', { name: '생산 시작' }).click()
  await expect(page.locator('.detail-panel .action-stack')).toContainText('생산 완료')
  await page.locator('.detail-panel .action-stack').getByRole('button', { name: '생산 완료' }).click()
  await expect(page.locator('.detail-panel .action-stack')).toContainText('품질검사 확정')
  await page.locator('.detail-panel .qc-inline input[type="number"]').nth(0).fill('8')
  await page.locator('.detail-panel .qc-inline input[type="number"]').nth(1).fill('2')
  await page.locator('.detail-panel .action-stack').getByRole('button', { name: '품질검사 확정' }).click()
  await expect(page.locator('.detail-panel')).toContainText('완료')

  await page.goto('/shipments')
  await page.getByRole('button', { name: '출고 등록' }).click()
  await selectItem(page, finishedSku)
  await page.locator('.dialog input[type="number"]').fill('3')
  await page.locator('.dialog button[type="submit"]').click()
  await expect(page.locator('.dialog')).toHaveCount(0)
  await expect(page.locator('.detail-panel')).toContainText('실DB 브래킷 ' + id)

  rawInventory = (await getData(request, '/api/v1/inventory?itemId=' + raw.id)).content[0]
  const finishedInventory = (await getData(request, '/api/v1/inventory?itemId=' + finished.id)).content[0]
  expect({ onHand: rawInventory.onHand, reserved: rawInventory.reserved, available: rawInventory.available })
    .toEqual({ onHand: 5, reserved: 0, available: 5 })
  expect({ onHand: finishedInventory.onHand, reserved: finishedInventory.reserved, available: finishedInventory.available })
    .toEqual({ onHand: 5, reserved: 0, available: 5 })

  const rawMoves = (await getData(request, '/api/v1/stock-movements?itemId=' + raw.id)).content
  const productMoves = (await getData(request, '/api/v1/stock-movements?itemId=' + finished.id)).content
  expect(rawMoves.some(m => m.movementType === 'RAW_RECEIPT' && m.quantity === 25)).toBe(true)
  expect(rawMoves.some(m => m.movementType === 'RAW_CONSUMPTION' && m.quantity === 20)).toBe(true)
  expect(productMoves.some(m => m.movementType === 'FINISHED_RECEIPT' && m.quantity === 8)).toBe(true)
  expect(productMoves.some(m => m.movementType === 'FINISHED_SHIPMENT' && m.quantity === 3)).toBe(true)
})
