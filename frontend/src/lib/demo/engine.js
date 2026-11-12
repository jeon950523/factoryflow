/**
 * Browser-only manufacturing walkthrough. This is deliberately NOT the Spring
 * domain implementation and must never be described as a MySQL transaction.
 * Pure command processing: POST returns a new state or throws without mutation.
 */
export class DemoError extends Error {
  constructor(status, code, message) {
    super(message)
    this.name = 'DemoError'
    this.status = status
    this.code = code
  }
}

const DAY = 86_400_000
const at = (now, daysAgo) => new Date(now - daysAgo * DAY).toISOString()
const copy = value => structuredClone(value)

export function createSeedState(now = Date.now()) {
  const items = [
    { id: 1, sku: 'RAW-PLATE-001', name: '산업용 강판', itemType: 'RAW_MATERIAL', unit: 'EA', active: true, createdAt: at(now, 8) },
    { id: 2, sku: 'RAW-BOLT-002', name: '조립 볼트', itemType: 'RAW_MATERIAL', unit: 'EA', active: true, createdAt: at(now, 8) },
    { id: 3, sku: 'RAW-RESIN-003', name: '절연 수지', itemType: 'RAW_MATERIAL', unit: 'EA', active: true, createdAt: at(now, 8) },
    { id: 4, sku: 'FG-CONTROL-100', name: '산업용 제어 박스', itemType: 'FINISHED_GOOD', unit: 'EA', active: true, createdAt: at(now, 7) },
  ]
  const materials = [
    { materialItemId: 1, requiredQty: 10 },
    { materialItemId: 2, requiredQty: 20 },
    { materialItemId: 3, requiredQty: 5 },
  ]
  const newMaterials = [
    { materialItemId: 1, requiredQty: 20 },
    { materialItemId: 2, requiredQty: 40 },
    { materialItemId: 3, requiredQty: 10 },
  ]
  return {
    schema: 1,
    nextIds: { item: 5, bom: 2, lot: 5, receipt: 4, order: 3, inspection: 2, shipment: 2, movement: 9 },
    items,
    boms: [{ id: 1, finishedItemId: 4, components: [
      { rawItemId: 1, qtyPerUnit: 2 },
      { rawItemId: 2, qtyPerUnit: 4 },
      { rawItemId: 3, qtyPerUnit: 1 },
    ], createdAt: at(now, 7) }],
    lots: [
      { id: 1, itemId: 1, lotCode: 'STEEL-A01', sourceType: 'RAW_RECEIPT', cumulativeInQty: 50, onHand: 40, reserved: 0, receivedAt: at(now, 6) },
      { id: 2, itemId: 2, lotCode: 'BOLT-B01', sourceType: 'RAW_RECEIPT', cumulativeInQty: 120, onHand: 100, reserved: 0, receivedAt: at(now, 6) },
      { id: 3, itemId: 3, lotCode: 'RESIN-C01', sourceType: 'RAW_RECEIPT', cumulativeInQty: 30, onHand: 25, reserved: 0, receivedAt: at(now, 6) },
      { id: 4, itemId: 4, lotCode: 'FG-WO-1', sourceType: 'QC_PASS', cumulativeInQty: 5, onHand: 3, reserved: 0, receivedAt: at(now, 3) },
    ],
    receipts: [
      { id: 1, requestKey: 'DEMO-REC-1', itemId: 1, inventoryLotId: 1, lotCode: 'STEEL-A01', quantity: 50, createdAt: at(now, 6) },
      { id: 2, requestKey: 'DEMO-REC-2', itemId: 2, inventoryLotId: 2, lotCode: 'BOLT-B01', quantity: 120, createdAt: at(now, 6) },
      { id: 3, requestKey: 'DEMO-REC-3', itemId: 3, inventoryLotId: 3, lotCode: 'RESIN-C01', quantity: 30, createdAt: at(now, 6) },
    ],
    orders: [
      { id: 1, orderNo: 'WO-DEMO-COMPLETE', clientRequestId: 'DEMO-WO-1', finishedItemId: 4, bomId: 1, plannedQty: 5, status: 'COMPLETED', createdAt: at(now, 5), materials },
      { id: 2, orderNo: 'WO-DEMO-READY', clientRequestId: 'DEMO-WO-2', finishedItemId: 4, bomId: 1, plannedQty: 10, status: 'DRAFT', createdAt: at(now, 1), materials: newMaterials },
    ],
    reservations: [],
    inspections: [{ id: 1, workOrderId: 1, passedQty: 5, failedQty: 0, inspectedAt: at(now, 3) }],
    shipments: [{ id: 1, requestKey: 'DEMO-SHIP-1', finishedItemId: 4, quantity: 2, createdAt: at(now, 2), allocations: [{ lotId: 4, quantity: 2 }] }],
    movements: [
      { id: 1, itemId: 1, inventoryLotId: 1, movementType: 'RAW_RECEIPT', quantity: 50, referenceType: 'RECEIPT', referenceId: 1, createdAt: at(now, 6) },
      { id: 2, itemId: 2, inventoryLotId: 2, movementType: 'RAW_RECEIPT', quantity: 120, referenceType: 'RECEIPT', referenceId: 2, createdAt: at(now, 6) },
      { id: 3, itemId: 3, inventoryLotId: 3, movementType: 'RAW_RECEIPT', quantity: 30, referenceType: 'RECEIPT', referenceId: 3, createdAt: at(now, 6) },
      { id: 4, itemId: 1, inventoryLotId: 1, movementType: 'RAW_CONSUMPTION', quantity: 10, referenceType: 'WORK_ORDER', referenceId: 1, createdAt: at(now, 4) },
      { id: 5, itemId: 2, inventoryLotId: 2, movementType: 'RAW_CONSUMPTION', quantity: 20, referenceType: 'WORK_ORDER', referenceId: 1, createdAt: at(now, 4) },
      { id: 6, itemId: 3, inventoryLotId: 3, movementType: 'RAW_CONSUMPTION', quantity: 5, referenceType: 'WORK_ORDER', referenceId: 1, createdAt: at(now, 4) },
      { id: 7, itemId: 4, inventoryLotId: 4, movementType: 'FINISHED_RECEIPT', quantity: 5, referenceType: 'INSPECTION', referenceId: 1, createdAt: at(now, 3) },
      { id: 8, itemId: 4, inventoryLotId: 4, movementType: 'FINISHED_SHIPMENT', quantity: 2, referenceType: 'SHIPMENT', referenceId: 1, createdAt: at(now, 2) },
    ],
  }
}

const invalid = (code, message) => { throw new DemoError(400, code, message) }
const conflict = (code, message) => { throw new DemoError(409, code, message) }
const notFound = (code, message) => { throw new DemoError(404, code, message) }
const positive = (value, name) => {
  const n = Number(value)
  if (!Number.isSafeInteger(n) || n < 1) invalid('INVALID_QUANTITY', name + '은(는) 1 이상의 정수여야 합니다.')
  return n
}
const nonnegative = (value, name) => {
  const n = Number(value)
  if (!Number.isSafeInteger(n) || n < 0) invalid('INVALID_QUANTITY', name + '은(는) 0 이상의 정수여야 합니다.')
  return n
}
const required = (value, name) => {
  if (typeof value !== 'string' || !value.trim()) invalid('INVALID_FIELD', name + '이(가) 필요합니다.')
  return value.trim()
}
const byId = (list, id, code, message) => list.find(value => value.id === Number(id)) ?? notFound(code, message)
const byItem = (state, id) => byId(state.items, id, 'ITEM_NOT_FOUND', '품목을 찾을 수 없습니다.')
const activeItem = (state, id, itemType) => {
  const item = byItem(state, id)
  if (!item.active || item.itemType !== itemType) invalid('INVALID_ITEM_TYPE', '선택한 품목의 유형이 올바르지 않습니다.')
  return item
}
const timestamp = () => new Date().toISOString()
const addMovement = (state, itemId, lotId, movementType, quantity, referenceType, referenceId) => {
  state.movements.push({
    id: state.nextIds.movement++, itemId, inventoryLotId: lotId, movementType, quantity,
    referenceType, referenceId, createdAt: timestamp(),
  })
}
const lotView = lot => ({ ...lot, available: lot.onHand - lot.reserved })
const orderView = order => ({ ...order, materials: order.materials.map(material => ({ ...material })) })
const shipmentView = shipment => ({ ...shipment, allocations: shipment.allocations.map(row => ({ ...row })) })
function pageOf(rows, params) {
  const page = params.has('page') ? nonnegative(params.get('page'), '페이지 번호') : 0
  const size = params.has('size') ? positive(params.get('size'), '페이지 크기') : 20
  if (size > 100) invalid('INVALID_PAGINATION', '페이지 크기는 100을 초과할 수 없습니다.')
  return { content: rows.slice(page * size, (page + 1) * size),
    page, size, totalElements: rows.length, totalPages: Math.ceil(rows.length / size) }
}
const oldestFirst = (a, b) => a.receivedAt.localeCompare(b.receivedAt) || a.id - b.id
const newestFirst = (a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id
const lotCandidates = (state, itemId) => state.lots.filter(lot => lot.itemId === itemId).sort(oldestFirst)
const available = lot => lot.onHand - lot.reserved

function getDemo(state, pathname, params) {
  if (pathname === '/api/v1/auth/me') {
    // This is a public, unauthenticated browser simulation, NOT a real account.
    return { username: 'demo', role: 'ADMIN' }
  }
  if (pathname === '/api/v1/items/lookup') {
    const ids = [...new Set((params.get('ids') ?? '').split(',').map(Number))]
      .filter(n => Number.isSafeInteger(n) && n > 0).slice(0, 100)
    return ids.map(id => state.items.find(item => item.id === id)).filter(Boolean)
  }
  if (pathname === '/api/v1/items') {
    const type = params.get('itemType'), keyword = (params.get('keyword') ?? '').toLowerCase().trim()
    return pageOf(state.items.filter(item => (!type || item.itemType === type) &&
      (!keyword || (item.sku + ' ' + item.name).toLowerCase().includes(keyword))), params)
  }
  let match = pathname.match(/^\/api\/v1\/items\/(\d+)$/)
  if (match) return byItem(state, match[1])
  match = pathname.match(/^\/api\/v1\/boms\/(\d+)$/)
  if (match) return state.boms.find(bom => bom.finishedItemId === Number(match[1]))
    ?? notFound('BOM_NOT_FOUND', 'BOM을 찾을 수 없습니다.')
  if (pathname === '/api/v1/inventory') {
    const id = params.get('itemId'), type = params.get('itemType')
    if (id) byItem(state, id)
    const rows = state.items.filter(item => (!id || item.id === Number(id)) && (!type || item.itemType === type))
      .map(item => {
        const lots = lotCandidates(state, item.id)
        const onHand = lots.reduce((total, lot) => total + lot.onHand, 0)
        const reserved = lots.reduce((total, lot) => total + lot.reserved, 0)
        return { itemId: item.id, sku: item.sku, name: item.name, itemType: item.itemType,
          onHand, reserved, available: onHand - reserved }
      })
    return pageOf(rows, params)
  }
  if (pathname === '/api/v1/inventory/lots') {
    const itemId = positive(params.get('itemId'), '품목 ID')
    byItem(state, itemId)
    return pageOf(lotCandidates(state, itemId).map(lotView), params)
  }
  match = pathname.match(/^\/api\/v1\/inventory\/lots\/(\d+)$/)
  if (match) return lotView(byId(state.lots, match[1], 'LOT_NOT_FOUND', 'LOT를 찾을 수 없습니다.'))
  if (pathname === '/api/v1/work-orders') {
    const status = params.get('status')
    return pageOf(state.orders.filter(order => !status || order.status === status)
      .sort(newestFirst).map(({ materials, ...summary }) => summary), params)
  }
  match = pathname.match(/^\/api\/v1\/work-orders\/(\d+)\/inspection$/)
  if (match) return state.inspections.find(row => row.workOrderId === Number(match[1]))
    ?? notFound('INSPECTION_NOT_FOUND', '품질검사 결과가 없습니다.')
  match = pathname.match(/^\/api\/v1\/work-orders\/(\d+)$/)
  if (match) return orderView(byId(state.orders, match[1], 'WORK_ORDER_NOT_FOUND', '생산지시를 찾을 수 없습니다.'))
  if (pathname === '/api/v1/shipments') {
    return pageOf(state.shipments.slice().sort(newestFirst)
      .map(({ allocations, ...summary }) => summary), params)
  }
  match = pathname.match(/^\/api\/v1\/shipments\/(\d+)$/)
  if (match) return shipmentView(byId(state.shipments, match[1], 'SHIPMENT_NOT_FOUND', '출고 기록을 찾을 수 없습니다.'))
  if (pathname === '/api/v1/stock-movements') {
    const itemId = positive(params.get('itemId'), '품목 ID')
    byItem(state, itemId)
    return pageOf(state.movements.filter(row => row.itemId === itemId).sort(newestFirst), params)
  }
  match = pathname.match(/^\/api\/v1\/stock-receipts\/(\d+)$/)
  if (match) return byId(state.receipts, match[1], 'RECEIPT_NOT_FOUND', '입고 기록이 없습니다.')
  if (pathname.startsWith('/api/v1/admin/')) {
    throw new DemoError(403, 'DEMO_NO_ACCOUNTS', '브라우저 데모에는 실제 사용자 계정이 없습니다.')
  }
  notFound('DEMO_ROUTE_NOT_FOUND', '데모에서 지원하지 않는 조회입니다.')
}

function postDemo(state, pathname, body) {
  if (!body || typeof body !== 'object') body = {}
  if (pathname === '/api/v1/items') {
    const sku = required(body.sku, 'SKU')
    const name = required(body.name, '품목명')
    if (!['RAW_MATERIAL', 'FINISHED_GOOD'].includes(body.itemType)) invalid('INVALID_ITEM_TYPE', '품목 유형을 선택해 주세요.')
    if (state.items.some(item => item.sku.toLowerCase() === sku.toLowerCase())) {
      conflict('DUPLICATE_SKU', '이미 등록된 SKU입니다.')
    }
    const item = { id: state.nextIds.item++, sku, name, itemType: body.itemType, unit: 'EA', active: true, createdAt: timestamp() }
    state.items.push(item)
    return item
  }
  if (pathname === '/api/v1/boms') {
    const item = activeItem(state, positive(body.finishedItemId, '완제품 ID'), 'FINISHED_GOOD')
    if (state.boms.some(bom => bom.finishedItemId === item.id)) conflict('BOM_ALREADY_EXISTS', '이미 BOM이 등록되어 있습니다.')
    if (!Array.isArray(body.components) || body.components.length < 1) invalid('EMPTY_BOM', 'BOM 구성 원자재가 필요합니다.')
    const seen = new Set()
    const components = body.components.map(row => {
      const raw = activeItem(state, positive(row.rawItemId, '원자재 ID'), 'RAW_MATERIAL')
      if (seen.has(raw.id)) invalid('DUPLICATE_BOM_COMPONENT', '원자재를 중복 등록할 수 없습니다.')
      seen.add(raw.id)
      return { rawItemId: raw.id, qtyPerUnit: positive(row.qtyPerUnit, '단위 소요량') }
    }).sort((a, b) => a.rawItemId - b.rawItemId)
    const bom = { id: state.nextIds.bom++, finishedItemId: item.id, components, createdAt: timestamp() }
    state.boms.push(bom)
    return bom
  }
  if (pathname === '/api/v1/stock-receipts') {
    const item = activeItem(state, positive(body.itemId, '원자재 ID'), 'RAW_MATERIAL')
    const lotCode = required(body.lotCode, 'LOT 코드')
    const quantity = positive(body.quantity, '입고 수량')
    const requestKey = required(body.requestKey, '요청 키')
    const prior = state.receipts.find(receipt => receipt.requestKey === requestKey)
    if (prior) {
      if (prior.itemId !== item.id || prior.lotCode !== lotCode || prior.quantity !== quantity) {
        conflict('IDEMPOTENCY_KEY_CONFLICT', '요청 키가 다른 내용으로 이미 사용되었습니다.')
      }
      return prior
    }
    let lot = state.lots.find(row => row.itemId === item.id && row.lotCode === lotCode)
    if (lot) {
      lot.cumulativeInQty += quantity
      lot.onHand += quantity
    } else {
      lot = { id: state.nextIds.lot++, itemId: item.id, lotCode, sourceType: 'RAW_RECEIPT',
        cumulativeInQty: quantity, onHand: quantity, reserved: 0, receivedAt: timestamp() }
      state.lots.push(lot)
    }
    const receipt = { id: state.nextIds.receipt++, requestKey, itemId: item.id, inventoryLotId: lot.id,
      lotCode, quantity, createdAt: timestamp() }
    state.receipts.push(receipt)
    addMovement(state, item.id, lot.id, 'RAW_RECEIPT', quantity, 'RECEIPT', receipt.id)
    return receipt
  }
  if (pathname === '/api/v1/work-orders') {
    const item = activeItem(state, positive(body.finishedItemId, '완제품 ID'), 'FINISHED_GOOD')
    const plannedQty = positive(body.plannedQty, '계획 수량')
    const clientRequestId = required(body.clientRequestId, '요청 키')
    const prior = state.orders.find(order => order.clientRequestId === clientRequestId)
    if (prior) {
      if (prior.finishedItemId !== item.id || prior.plannedQty !== plannedQty) {
        conflict('IDEMPOTENCY_KEY_CONFLICT', '요청 키가 다른 내용으로 이미 사용되었습니다.')
      }
      return orderView(prior)
    }
    const bom = state.boms.find(value => value.finishedItemId === item.id)
      ?? notFound('BOM_NOT_FOUND', '생산지시 전에 BOM을 등록하세요.')
    const materials = bom.components.map(c => {
      const quantity = c.qtyPerUnit * plannedQty
      if (!Number.isSafeInteger(quantity)) invalid('QUANTITY_OVERFLOW', '소요 자재 수량을 계산할 수 없습니다.')
      return { materialItemId: c.rawItemId, requiredQty: quantity }
    })
    const id = state.nextIds.order++
    const order = { id, orderNo: 'WO-DEMO-' + String(id).padStart(4, '0'), clientRequestId,
      finishedItemId: item.id, bomId: bom.id, plannedQty, status: 'DRAFT', createdAt: timestamp(), materials }
    state.orders.push(order)
    return orderView(order)
  }
  const operation = pathname.match(/^\/api\/v1\/work-orders\/(\d+)\/(reserve|cancel|start|finish|inspection)$/)
  if (operation) {
    const order = byId(state.orders, operation[1], 'WORK_ORDER_NOT_FOUND', '생산지시가 없습니다.')
    const command = operation[2]
    const expect = (status) => { if (order.status !== status) conflict('STATE_CONFLICT', '현재 상태에서 실행할 수 없는 명령입니다.') }
    if (command === 'reserve') {
      expect('DRAFT')
      const plan = []
      for (const requirement of order.materials) {
        let missing = requirement.requiredQty
        for (const lot of lotCandidates(state, requirement.materialItemId)) {
          if (missing === 0) break
          const qty = Math.min(available(lot), missing)
          if (qty > 0) { plan.push({ orderId: order.id, materialItemId: requirement.materialItemId, lotId: lot.id, quantity: qty, status: 'RESERVED' }); missing -= qty }
        }
        if (missing > 0) conflict('INSUFFICIENT_STOCK', '원자재가 부족하여 전량 예약할 수 없습니다.')
      }
      for (const allocation of plan) {
        byId(state.lots, allocation.lotId).reserved += allocation.quantity
        state.reservations.push(allocation)
      }
      order.status = 'RESERVED'
      return orderView(order)
    }
    if (command === 'cancel') {
      if (!['DRAFT', 'RESERVED'].includes(order.status)) conflict('STATE_CONFLICT', '취소할 수 없는 생산 단계입니다.')
      for (const reserved of state.reservations.filter(r => r.orderId === order.id && r.status === 'RESERVED')) {
        byId(state.lots, reserved.lotId).reserved -= reserved.quantity
        reserved.status = 'RELEASED'
      }
      order.status = 'CANCELED'
      return orderView(order)
    }
    if (command === 'start') {
      expect('RESERVED')
      const allocations = state.reservations.filter(r => r.orderId === order.id && r.status === 'RESERVED')
      for (const requirement of order.materials) {
        if (allocations.filter(a => a.materialItemId === requirement.materialItemId)
          .reduce((total, a) => total + a.quantity, 0) !== requirement.requiredQty) {
          conflict('RESERVATION_INTEGRITY_CONFLICT', '원자재 예약이 완전하지 않습니다.')
        }
      }
      for (const row of allocations) {
        const lot = byId(state.lots, row.lotId)
        if (lot.reserved < row.quantity || lot.onHand < row.quantity) {
          conflict('RESERVATION_INTEGRITY_CONFLICT', 'LOT의 예약 수량이 일치하지 않습니다.')
        }
        lot.reserved -= row.quantity
        lot.onHand -= row.quantity
        row.status = 'CONSUMED'
        addMovement(state, lot.itemId, lot.id, 'RAW_CONSUMPTION', row.quantity, 'WORK_ORDER', order.id)
      }
      order.status = 'IN_PROGRESS'
      return orderView(order)
    }
    if (command === 'finish') {
      expect('IN_PROGRESS')
      order.status = 'AWAITING_QC'
      return orderView(order)
    }
    if (command === 'inspection') {
      expect('AWAITING_QC')
      const passedQty = nonnegative(body.passedQty, '합격 수량')
      const failedQty = nonnegative(body.failedQty, '불합격 수량')
      if (passedQty + failedQty !== order.plannedQty) invalid('QC_QUANTITY_MISMATCH', '합격·불합격 합계가 생산 수량과 달라요.')
      const inspection = { id: state.nextIds.inspection++, workOrderId: order.id, passedQty, failedQty, inspectedAt: timestamp() }
      state.inspections.push(inspection)
      if (passedQty > 0) {
        const lot = { id: state.nextIds.lot++, itemId: order.finishedItemId, lotCode: 'FG-WO-' + order.id,
          sourceType: 'QC_PASS', cumulativeInQty: passedQty, onHand: passedQty, reserved: 0, receivedAt: timestamp() }
        state.lots.push(lot)
        addMovement(state, lot.itemId, lot.id, 'FINISHED_RECEIPT', passedQty, 'INSPECTION', inspection.id)
      }
      order.status = 'COMPLETED'
      return inspection
    }
  }
  if (pathname === '/api/v1/shipments') {
    const item = activeItem(state, positive(body.finishedItemId, '완제품 ID'), 'FINISHED_GOOD')
    const quantity = positive(body.quantity, '출고 수량')
    const requestKey = required(body.requestKey, '요청 키')
    const prior = state.shipments.find(s => s.requestKey === requestKey)
    if (prior) {
      if (prior.finishedItemId !== item.id || prior.quantity !== quantity) {
        conflict('IDEMPOTENCY_KEY_CONFLICT', '요청 키가 다른 내용으로 이미 사용되었습니다.')
      }
      return shipmentView(prior)
    }
    let missing = quantity
    const allocations = []
    for (const lot of lotCandidates(state, item.id).filter(lot => lot.sourceType === 'QC_PASS')) {
      if (!missing) break
      const qty = Math.min(available(lot), missing)
      if (qty > 0) { allocations.push({ lotId: lot.id, quantity: qty }); missing -= qty }
    }
    if (missing) conflict('INSUFFICIENT_FINISHED_STOCK', '출고 가능한 합격품 재고가 부족합니다.')
    const shipment = { id: state.nextIds.shipment++, requestKey, finishedItemId: item.id, quantity,
      createdAt: timestamp(), allocations }
    state.shipments.push(shipment)
    for (const allocation of allocations) {
      byId(state.lots, allocation.lotId).onHand -= allocation.quantity
      addMovement(state, item.id, allocation.lotId, 'FINISHED_SHIPMENT', allocation.quantity, 'SHIPMENT', shipment.id)
    }
    return shipmentView(shipment)
  }
  if (pathname.startsWith('/api/v1/auth/') || pathname.startsWith('/api/v1/admin/')) {
    throw new DemoError(403, 'DEMO_NO_AUTH', '브라우저 데모는 서버 인증이나 계정을 생성하지 않습니다.')
  }
  notFound('DEMO_ROUTE_NOT_FOUND', '데모에서 지원하지 않는 명령입니다.')
}

export function executeDemo(state, path, { method = 'GET', body } = {}) {
  const url = new URL(path, 'https://factoryflow.invalid')
  if (!url.pathname.startsWith('/api/v1/')) notFound('DEMO_ROUTE_NOT_FOUND', '데모 API 경로가 아닙니다.')
  const verb = method.toUpperCase()
  if (verb === 'GET') return { data: getDemo(state, url.pathname, url.searchParams), state, modified: false }
  if (verb !== 'POST') throw new DemoError(405, 'METHOD_NOT_ALLOWED', '데모는 해당 HTTP 명령을 지원하지 않습니다.')
  // Crucial: throw leaves the previously persisted snapshot unmodified.
  const next = copy(state)
  return { data: postDemo(next, url.pathname, body), state: next, modified: true }
}
