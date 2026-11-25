import test from 'node:test'
import assert from 'node:assert/strict'
import { reconcile } from './oracle.mjs'

const lot = { id: 1, onHand: 7, reserved: 2, available: 5, cumulativeInQty: 10 }
const rows = [
  { id: 1, inventoryLotId: 1, movementType: 'RAW_RECEIPT', quantity: 10 },
  { id: 2, inventoryLotId: 1, movementType: 'RAW_CONSUMPTION', quantity: 3 },
]
test('valid independent ledger fixture', () => {
  assert.deepEqual(reconcile([lot], rows), { onHand: 7, reserved: 2, available: 5 })
})
test('negative controls: injected ledger and stock faults must be detected', () => {
  assert.throws(() => reconcile([{ ...lot, onHand: -1 }], rows), /invalid LOT quantity/)
  assert.throws(() => reconcile([{ ...lot, reserved: 8 }], rows), /reservation exceeds/)
  assert.throws(() => reconcile([{ ...lot, available: 6 }], rows), /available mismatch/)
  assert.throws(() => reconcile([{ ...lot, onHand: 8, available: 6 }], rows), /ledger balance mismatch/)
  assert.throws(() => reconcile([{ ...lot, cumulativeInQty: 11 }], rows), /cumulative receipts mismatch/)
  assert.throws(() => reconcile([lot], [...rows, rows[1]]), /duplicate movement/)
  assert.throws(() => reconcile([lot], [{ ...rows[0], inventoryLotId: 99 }, rows[1]]), /orphan movement/)
  assert.throws(() => reconcile([lot], [{ ...rows[0], movementType: 'UNKNOWN' }, rows[1]]), /unknown movement/)
})
