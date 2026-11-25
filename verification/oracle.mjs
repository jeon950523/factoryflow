import assert from 'node:assert/strict'

// Independent oracle derived from SSOT INV-01/02/03/10, not application code.
const signs = Object.freeze({ RAW_RECEIPT: 1, RAW_CONSUMPTION: -1,
  FINISHED_RECEIPT: 1, FINISHED_SHIPMENT: -1 })
export function reconcile(lots, movements) {
  const totals = { onHand: 0, reserved: 0, available: 0 }
  const ids = new Set(lots.map(lot => lot.id))
  assert.equal(ids.size, lots.length, 'duplicate LOT')
  const balances = new Map(lots.map(lot => [lot.id, 0]))
  const incoming = new Map(lots.map(lot => [lot.id, 0]))
  const movementIds = new Set()
  for (const row of movements) {
    assert.ok(!movementIds.has(row.id), 'duplicate movement')
    movementIds.add(row.id)
    assert.ok(ids.has(row.inventoryLotId), 'orphan movement')
    assert.ok(Object.hasOwn(signs, row.movementType), 'unknown movement type')
    assert.ok(Number.isSafeInteger(row.quantity) && row.quantity > 0, 'invalid movement quantity')
    const sign = signs[row.movementType]
    balances.set(row.inventoryLotId, balances.get(row.inventoryLotId) + sign * row.quantity)
    if (sign === 1) incoming.set(row.inventoryLotId, incoming.get(row.inventoryLotId) + row.quantity)
  }
  for (const lot of lots) {
    for (const key of ['onHand', 'reserved', 'available', 'cumulativeInQty']) {
      assert.ok(Number.isSafeInteger(lot[key]) && lot[key] >= 0, 'invalid LOT quantity: ' + key)
    }
    assert.ok(lot.reserved <= lot.onHand, 'reservation exceeds stock')
    assert.equal(lot.available, lot.onHand - lot.reserved, 'available mismatch')
    assert.equal(lot.onHand, balances.get(lot.id), 'ledger balance mismatch')
    assert.equal(lot.cumulativeInQty, incoming.get(lot.id), 'cumulative receipts mismatch')
    for (const key of Object.keys(totals)) totals[key] += lot[key]
  }
  return totals
}
