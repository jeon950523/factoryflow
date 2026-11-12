<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Plus, RefreshCw, ClipboardList, X, ArrowRight, CheckCheck, CircleAlert } from 'lucide-vue-next'
import { get, post } from '../lib/api.js'
import { useAuthStore } from '../stores/auth.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { lookupItemNames, itemNameOrId, itemSkuOrId } from '../lib/itemNames.js'
import { number, dateTime, requestKey } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'

const route = useRoute(), ui = useUiStore()
const auth = useAuthStore()
const listGate = createLatestRequest(), detailGate = createLatestRequest()
onBeforeUnmount(() => { listGate.cancel(); detailGate.cancel() })
const loading = ref(false), error = ref(''), page = ref(0)
const statusValues = new Set(['DRAFT', 'RESERVED', 'IN_PROGRESS', 'AWAITING_QC', 'COMPLETED', 'CANCELED'])
// Dashboard queue links use ?status=...; do not pretend the API supports an ad-hoc search.
const initialStatus = typeof route.query.status === 'string' && statusValues.has(route.query.status)
  ? route.query.status : ''
const statusFilter = ref(initialStatus), list = ref({ content: [], totalPages: 0, totalElements: 0 })
const itemNames = ref({}), selected = ref(null), detailLoading = ref(false), detailError = ref('')
const showCreate = ref(false), saving = ref(false), acting = ref(false)
const createForm = ref({ finishedItemId: '', plannedQty: 1, clientRequestId: requestKey('WO') })
const qcForm = ref({ passedQty: 0, failedQty: 0 })
const transitions = {
  DRAFT: [{ op: 'reserve', label: '자재 예약', hint: 'FIFO LOT에서 필요량 확보', primary: true }, { op: 'cancel', label: '지시 취소', danger: true }],
  RESERVED: [{ op: 'start', label: '생산 시작', hint: '예약 원자재를 실제 소모', primary: true }, { op: 'cancel', label: '예약 취소', danger: true }],
  IN_PROGRESS: [{ op: 'finish', label: '생산 완료', hint: '품질검사 대기로 전환', primary: true }],
  AWAITING_QC: [{ op: 'inspection', label: '품질검사 확정', primary: true }],
}
const actions = computed(() => selected.value ? transitions[selected.value.status] ?? [] : [])
const selectedItemName = computed(() => itemNameOrId(itemNames.value, selected.value?.finishedItemId, '완제품'))
async function load() {
  const request = listGate.begin()
  loading.value = true; error.value = ''
  try {
    const orders = await get('/api/v1/work-orders',
      { status: statusFilter.value || undefined, page: page.value, size: 15 },
      { signal: request.signal })
    if (!request.isCurrent()) return
    list.value = orders
    try {
      const names = await lookupItemNames(orders.content.map(row => row.finishedItemId), { signal: request.signal })
      if (!request.isCurrent()) return
      itemNames.value = { ...itemNames.value, ...names }
    } catch (e) {
      if (request.isCurrent() && !isAbortError(e)) ui.notify('품목명 조회에 실패했습니다. 이름 대신 품목 ID로 표시합니다.', 'error')
    }
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}
async function choose(id) {
  const request = detailGate.begin()
  detailLoading.value = true; detailError.value = ''
  try {
    const detail = await get(`/api/v1/work-orders/${id}`, undefined, { signal: request.signal })
    if (!request.isCurrent()) return
    selected.value = detail
    qcForm.value = { passedQty: detail.plannedQty, failedQty: 0 }
    try {
      const names = await lookupItemNames([detail.finishedItemId,
        ...detail.materials.map(material => material.materialItemId)], { signal: request.signal })
      if (!request.isCurrent()) return
      itemNames.value = { ...itemNames.value, ...names }
    } catch (e) {
      if (request.isCurrent() && !isAbortError(e)) ui.notify('자재명 조회에 실패했습니다. 품목 ID로 표시합니다.', 'error')
    }
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) {
      selected.value = null
      detailError.value = e.message
    }
  } finally {
    if (request.isCurrent()) detailLoading.value = false
  }
}
async function createOrder() {
  const qty = Number(createForm.value.plannedQty)
  if (!Number.isSafeInteger(qty) || qty < 1 || !createForm.value.finishedItemId) { ui.notify('완제품과 올바른 생산 계획 수량을 입력하세요.', 'error'); return }
  saving.value = true
  try {
    const created = await post('/api/v1/work-orders', { ...createForm.value, finishedItemId: Number(createForm.value.finishedItemId), plannedQty: qty })
    showCreate.value = false; ui.notify(`생산지시 ${created.orderNo}를 생성했습니다.`)
    page.value = 0; statusFilter.value = ''; await load(); await choose(created.id)
    createForm.value = { finishedItemId: '', plannedQty: 1, clientRequestId: requestKey('WO') }
  } catch (e) { ui.notify(e.message, 'error') }
  finally { saving.value = false }
}
async function runAction(action) {
  if (!selected.value || acting.value) return
  if (action === 'cancel' && !window.confirm('생산지시를 취소하고 예약된 재고를 해제하시겠습니까?')) return
  acting.value = true
  const actionOrderId = selected.value.id
  try {
    if (action === 'inspection') {
      const passedQty = Number(qcForm.value.passedQty), failedQty = Number(qcForm.value.failedQty)
      if (![passedQty, failedQty].every(v => Number.isSafeInteger(v) && v >= 0) || passedQty + failedQty !== Number(selected.value.plannedQty)) {
        ui.notify('합격과 불합격 수량 합계가 생산 계획 수량과 같아야 합니다.', 'error'); return
      }
      await post(`/api/v1/work-orders/${actionOrderId}/inspection`, { passedQty, failedQty })
    } else {
      await post(`/api/v1/work-orders/${actionOrderId}/${action}`)
    }
    ui.notify(action === 'inspection' ? '품질검사를 확정하고 합격품을 재고에 반영했습니다.' : '생산지시 상태를 변경했습니다.')
    await Promise.all([selected.value?.id === actionOrderId ? choose(actionOrderId) : Promise.resolve(), load()])
  } catch (e) { ui.notify(e.message, 'error') }
  finally { acting.value = false }
}
function changePage(next) { page.value = next; load() }
function changeFilter() {
  page.value = 0
  selected.value = null
  detailGate.cancel()
  detailLoading.value = false
  detailError.value = ''
  load()
}
onMounted(async () => {
  await load()
  if (route.query.id) await choose(route.query.id)
})
</script>
<template>
  <div class="toolbar-card"><div class="toolbar-left"><label class="filter-field"><span>진행 상태</span><select v-model="statusFilter" class="select-filter" @change="changeFilter"><option value="">전체 상태</option><option value="DRAFT">초안</option><option value="RESERVED">자재 예약</option><option value="IN_PROGRESS">생산 중</option><option value="AWAITING_QC">검사 대기</option><option value="COMPLETED">완료</option><option value="CANCELED">취소</option></select></label><button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16"/> 새로고침</button></div><button v-if="auth.canWrite" type="button" class="btn btn-primary" @click="showCreate = true"><Plus :size="17" /> 생산지시 생성</button></div>
  <div class="section-grid order-workspace"><section class="surface-card"><div class="card-header compact"><div><h2>생산지시 목록 <small class="header-count" aria-live="polite">{{ number(list.totalElements) }}건</small></h2><p>지시번호를 선택하면 원자재 소요량과 다음 작업을 확인할 수 있습니다.</p></div></div><AsyncState :loading="loading" :error="error" :empty="!list.content.length" label="생산지시가 없습니다. 오른쪽 위에서 새 생산지시를 생성하세요." @retry="load"><div class="table-scroll"><table class="data-table"><thead><tr><th>지시번호 / 등록일</th><th>완제품</th><th class="right">계획 수량</th><th>진행 상태</th></tr></thead><tbody><tr v-for="order in list.content" :key="order.id" :class="{ selected: selected?.id === order.id }"><td><button type="button" class="row-action" :aria-pressed="selected?.id === order.id" @click="choose(order.id)">{{ order.orderNo }}</button><small class="cell-sub">{{ dateTime(order.createdAt) }}</small></td><td>{{ itemNameOrId(itemNames, order.finishedItemId, '완제품') }}</td><td class="number-cell">{{ number(order.plannedQty) }}</td><td><StatusBadge :status="order.status" /></td></tr></tbody></table></div><PageControls :page="page" :total-pages="list.totalPages" :total-elements="list.totalElements" @change="changePage" /></AsyncState></section>
    <section class="surface-card detail-panel"><div class="card-header compact"><div><h2>생산지시 상세</h2><p>선택한 지시의 원자재와 실행 가능한 업무</p></div></div><AsyncState :loading="detailLoading" :error="detailError" @retry="selected && choose(selected.id)"><div v-if="!selected" class="detail-placeholder"><ClipboardList :size="35" :stroke-width="1.5"/><h3>생산지시를 선택해 주세요</h3><p>지시별 필요 원자재와 작업 상태를 확인할 수 있습니다.</p></div><div v-else class="detail-body"><div class="detail-title"><span class="detail-avatar"><ClipboardList :size="24" /></span><div><strong>{{ selected.orderNo }}</strong><span>{{ selectedItemName }}</span></div></div><div class="status-overview"><StatusBadge :status="selected.status" /><span>계획 수량 <strong>{{ number(selected.plannedQty) }} EA</strong></span></div><div class="stage-track"><div v-for="(stage, idx) in [{ label: '초안', key: 'DRAFT' },{ label: '예약', key: 'RESERVED' },{ label: '생산', key: 'IN_PROGRESS' },{ label: '검사', key: 'AWAITING_QC' },{ label: '완료', key: 'COMPLETED' }]" :key="stage.key" :class="['stage-step', { done: idx <= ['DRAFT','RESERVED','IN_PROGRESS','AWAITING_QC','COMPLETED'].indexOf(selected.status) && selected.status !== 'CANCELED' }]"><span class="stage-number">{{ idx + 1 }}</span><small>{{ stage.label }}</small></div></div><div class="subsection-heading"><div><h3>필요 원자재</h3></div></div><div class="material-list"><div v-for="material in selected.materials" :key="material.materialItemId" class="material-row"><span><strong>{{ itemNameOrId(itemNames, material.materialItemId, "원자재") }}</strong> <small class="material-code">{{ itemSkuOrId(itemNames, material.materialItemId) }}</small></span><strong>{{ number(material.requiredQty) }} EA</strong></div></div><div class="subsection-heading"><div><h3>다음 실행 작업</h3></div></div><div v-if="selected.status === 'AWAITING_QC'" class="qc-inline"><div class="qc-explainer"><CircleAlert :size="17"/><span>검사 합격 수량만 완제품 재고로 반영됩니다. 두 수량의 합계는 {{ number(selected.plannedQty) }}이어야 합니다.</span></div><div class="form-row"><label class="field-label">합격 수량<input v-model.number="qcForm.passedQty" type="number" min="0" step="1" /></label><label class="field-label">불합격 수량<input v-model.number="qcForm.failedQty" type="number" min="0" step="1" /></label></div><strong class="quantity-total">총 {{ number(Number(qcForm.passedQty) + Number(qcForm.failedQty)) }} / {{ number(selected.plannedQty) }} EA</strong></div><p v-if="!actions.length" class="small-empty">이 생산지시는 더 이상 실행할 작업이 없습니다.</p><div v-if="auth.canWrite" class="action-stack"><button v-for="action in actions" :key="action.op" type="button" :disabled="acting" :class="['btn', action.danger ? 'btn-danger-outline' : 'btn-primary', 'btn-wide']" @click="runAction(action.op)"><CheckCheck v-if="action.op === 'inspection'" :size="18" /><ArrowRight v-else :size="18" />{{ action.label }}</button></div></div></AsyncState></section>
  </div>
  <div v-if="showCreate" class="dialog-backdrop" @click.self="showCreate = false"><section class="dialog" role="dialog" aria-modal="true" aria-label="생산지시 생성"><header class="dialog-header"><div><h2>생산지시 생성</h2></div><button type="button" class="icon-btn" aria-label="닫기" @click="showCreate = false"><X :size="20" /></button></header><form @submit.prevent="createOrder"><div class="dialog-body"><div class="field-label"><span class="field-title">완제품 <em class="required-mark">*</em></span><ItemSearchSelect v-model="createForm.finishedItemId" item-type="FINISHED_GOOD" placeholder="완제품 검색 · 선택" :disabled="saving" /></div><label class="field-label">계획 수량 (EA) <span>*</span><input v-model.number="createForm.plannedQty" type="number" min="1" step="1" required /></label><div class="info-hint">생산지시 생성은 DRAFT 상태만 만듭니다. 원자재 예약은 생성 후 별도 작업으로 진행합니다. 선택한 완제품의 BOM이 등록되어 있어야 합니다.</div></div><footer class="dialog-footer"><button type="button" class="btn btn-outline" @click="showCreate = false">취소</button><button type="submit" class="btn btn-primary"  :disabled="saving || !createForm.finishedItemId">{{ saving ? '생성 중...' : '생산지시 생성' }}</button></footer></form></section></div>
</template>
