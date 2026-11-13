import { test, expect } from '@playwright/test'

/**
 * No API mocks. Browser -> Vite proxy -> real Spring Boot -> MySQL 8.4.
 * Non-production CI database only; session login required before business operations.
 */
async function postData(request, path, payload) {
  const csrf = await getData(request, '/api/v1/auth/csrf')
  const response = await request.post(path, { data: payload, headers: { 'X-CSRF-TOKEN': csrf.token } })
  expect(response.status(), await response.text()).toBe(201)
  return (await response.json()).data
}

async function getData(request, path) {
  const response = await request.get(path)
  expect(response.ok(), await response.text()).toBeTruthy()
  return (await response.json()).data
}

async function loginAsAdmin(page) {
  await page.goto('/login')
  await page.locator('input[name=username]').fill(process.env.E2E_ADMIN_USERNAME)
  await page.locator('input[name=password]').fill(process.env.E2E_ADMIN_PASSWORD)
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page).toHaveURL('http://127.0.0.1:5173/')
  await expect(page.locator('.topbar')).toContainText(process.env.E2E_ADMIN_USERNAME)
}

async function selectItem(page, sku) {
  await page.locator('.dialog .item-search-toggle').click()
  await page.locator('.dialog .item-search-bar input').fill(sku)
  await page.locator('.dialog .item-search-bar button').click()
  await expect(page.locator('.dialog .item-search-option').filter({ hasText: sku })).toBeVisible()
  await page.locator('.dialog .item-search-option').filter({ hasText: sku }).click()
  await expect(page.locator('.dialog .item-search-toggle')).toContainText(sku)
}

test('real backend and MySQL: receipt → reserve → consume → QC → shipment', async ({ page }) => {
  await loginAsAdmin(page)
  const request = page.request
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

  // TASK-014: Verify the audit UI against those exact records persisted in MySQL.
  await page.goto('/movements?itemId=' + raw.id)
  const table = page.getByRole('table', { name: '선택 품목 재고 이동 이력' })
  await expect(table).toContainText('+25')
  await expect(table).toContainText('−20')
  const consumption = rawMoves.find(m => m.movementType === 'RAW_CONSUMPTION')
  await expect(table.getByRole('link', { name: '생산지시 #' + consumption.referenceId + ' 확인' }))
    .toHaveAttribute('href', '/orders?id=' + consumption.referenceId)
  await page.reload()
  await expect(table).toContainText('−20')

  await page.goto('/movements?itemId=' + finished.id)
  await expect(table).toContainText('+8')
  await expect(table).toContainText('−3')
  const shipped = productMoves.find(m => m.movementType === 'FINISHED_SHIPMENT')
  await table.getByRole('link', { name: '완제품 출고 #' + shipped.referenceId + ' 확인' }).click()
  await expect(page).toHaveURL(new RegExp('/shipments\\?id=' + shipped.referenceId + '


test('real database: shortage from Vue reservation button must preserve DRAFT and unreserved stock', async ({ page }) => {
  await loginAsAdmin(page)
  const request = page.request
  const id = Date.now().toString(36).toUpperCase()
  const raw = await postData(request, '/api/v1/items', {
    sku: 'RM-SHORT-' + id, name: '부족 재료 ' + id, itemType: 'RAW_MATERIAL',
  })
  const finished = await postData(request, '/api/v1/items', {
    sku: 'FG-SHORT-' + id, name: '부족 완제품 ' + id, itemType: 'FINISHED_GOOD',
  })
  await postData(request, '/api/v1/boms', {
    finishedItemId: finished.id, components: [{ rawItemId: raw.id, qtyPerUnit: 2 }],
  })
  await postData(request, '/api/v1/stock-receipts', {
    requestKey: 'REC-SHORT-' + id, itemId: raw.id, lotCode: 'LOT-SHORT-' + id, quantity: 3,
  })
  const order = await postData(request, '/api/v1/work-orders', {
    clientRequestId: 'WO-SHORT-' + id, finishedItemId: finished.id, plannedQty: 2,
  })

  await page.goto('/orders?id=' + order.id)
  await expect(page.locator('.detail-panel')).toContainText(order.orderNo)
  await page.locator('.detail-panel .action-stack').getByRole('button', { name: '자재 예약' }).click()
  await expect(page.locator('.toast')).toContainText('원자재가 부족하여 전량 예약할 수 없습니다.')
  const after = await getData(request, '/api/v1/work-orders/' + order.id)
  const rawInventory = (await getData(request, '/api/v1/inventory?itemId=' + raw.id)).content[0]
  expect(after.status).toBe('DRAFT')
  expect({ onHand: rawInventory.onHand, reserved: rawInventory.reserved, available: rawInventory.available })
    .toEqual({ onHand: 3, reserved: 0, available: 3 })
})
))
  await expect(page.getByRole('table', { name: 'LOT별 출고 배분 내역' })).toContainText('LOT #')
})


test('real database: shortage from Vue reservation button must preserve DRAFT and unreserved stock', async ({ page }) => {
  await loginAsAdmin(page)
  const request = page.request
  const id = Date.now().toString(36).toUpperCase()
  const raw = await postData(request, '/api/v1/items', {
    sku: 'RM-SHORT-' + id, name: '부족 재료 ' + id, itemType: 'RAW_MATERIAL',
  })
  const finished = await postData(request, '/api/v1/items', {
    sku: 'FG-SHORT-' + id, name: '부족 완제품 ' + id, itemType: 'FINISHED_GOOD',
  })
  await postData(request, '/api/v1/boms', {
    finishedItemId: finished.id, components: [{ rawItemId: raw.id, qtyPerUnit: 2 }],
  })
  await postData(request, '/api/v1/stock-receipts', {
    requestKey: 'REC-SHORT-' + id, itemId: raw.id, lotCode: 'LOT-SHORT-' + id, quantity: 3,
  })
  const order = await postData(request, '/api/v1/work-orders', {
    clientRequestId: 'WO-SHORT-' + id, finishedItemId: finished.id, plannedQty: 2,
  })

  await page.goto('/orders?id=' + order.id)
  await expect(page.locator('.detail-panel')).toContainText(order.orderNo)
  await page.locator('.detail-panel .action-stack').getByRole('button', { name: '자재 예약' }).click()
  await expect(page.locator('.toast')).toContainText('원자재가 부족하여 전량 예약할 수 없습니다.')
  const after = await getData(request, '/api/v1/work-orders/' + order.id)
  const rawInventory = (await getData(request, '/api/v1/inventory?itemId=' + raw.id)).content[0]
  expect(after.status).toBe('DRAFT')
  expect({ onHand: rawInventory.onHand, reserved: rawInventory.reserved, available: rawInventory.available })
    .toEqual({ onHand: 3, reserved: 0, available: 3 })
})
