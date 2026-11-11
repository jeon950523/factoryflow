const countFormatter = new Intl.NumberFormat('ko-KR')
export const number = (value) => countFormatter.format(Number(value ?? 0))
export const dateTime = (value) => value ? new Date(value.endsWith('Z') ? value : `${value}Z`).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) : '—'

export const statusMap = {
  DRAFT: ['초안', 'neutral'], RESERVED: ['자재 예약', 'info'], IN_PROGRESS: ['생산 중', 'info'],
  AWAITING_QC: ['검사 대기', 'warning'], COMPLETED: ['완료', 'success'], CANCELED: ['취소', 'muted'],
  RAW_MATERIAL: ['원자재', 'neutral'], FINISHED_GOOD: ['완제품', 'success'],
  RAW_RECEIPT: ['원자재 입고', 'success'], RAW_CONSUMPTION: ['원자재 소모', 'warning'],
  FINISHED_RECEIPT: ['완제품 입고', 'success'], FINISHED_SHIPMENT: ['완제품 출고', 'info'],
  QC_PASS: ['품질 합격', 'success'],
}
export const statusLabel = (value) => statusMap[value]?.[0] ?? value ?? '—'
export const statusTone = (value) => statusMap[value]?.[1] ?? 'neutral'
export const requestKey = (prefix) => `${prefix}-${crypto.randomUUID()}`
