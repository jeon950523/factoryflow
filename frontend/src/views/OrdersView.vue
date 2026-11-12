<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Plus, RefreshCw, ClipboardList, X, ArrowRight, CheckCheck, CircleAlert } from 'lucide-vue-next'
import './orders-workbench.css'
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

const route = useRoute(), router = useRouter(), ui = useUiStore()
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
const requestedDetailId = ref(null), detailSection = ref(null)
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
const stages = [
  { label: '초안', key: 'DRAFT' },
  { label: '예약', key: 'RESERVED' },
  { label: '생산', key: 'IN_PROGRESS' },
  { label: '검사', key: 'AWAITING_QC' },
  { label: '완료', key: 'COMPLETED' },
]
const currentStage = computed(() => stages.findIndex(stage => stage.key === selected.value?.status))
const qcValid = computed(() => {
  if (!selected.value || selected.value.status !== 'AWAITING_QC') return false
  const passed = Number(qcForm.value.passedQty)
  const failed = Number(qcForm.value.failedQty)
  return [passed, failed].every(v => Number.isSafeInteger(v) && v >= 0)
    && passed + failed === Number(selected.value.plannedQty)
})
function nextAction(status) {
  return transitions[status]?.find(action => action.primary)?.label ?? '—'
}
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
  requestedDetailId.value = id
  const request = detailGate.begin()
  detailLoading.value = true; detailError.value = ''
  try {
    const detail = await get(`/api/v1/work-orders/${id}`, undefined, { signal: request.signal })
    if (!request.isCurrent()) return
    selected.value = detail
    qcForm.value = { passedQty: detail.plannedQty, failedQty: 0 }
    await nextTick()
    if (request.isCurrent()) detailSection.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
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
    showCreate.value = false
    ui.notify(`생산지시 ${created.orderNo}를 생성했습니다.`)
    createForm.value = { finishedItemId: '', plannedQty: 1, clientRequestId: requestKey('WO') }
    // Keep the new detail addressable after refresh even when created from a
    // filtered list. App.vue remounts RouterView when fullPath changes.
    await router.replace({ path: '/orders', query: { id: created.id } })
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
function changePage(next) {
  page.value = next
  selected.value = null
  requestedDetailId.value = null
  detailGate.cancel()
  detailLoading.value = false
  detailError.value = ''
  load()
}
function changeFilter() {
  page.value = 0
  selected.value = null
  requestedDetailId.value = null
  detailGate.cancel()
  detailLoading.value = false
  detailError.value = ''
  const selectedStatus = statusFilter.value
  const queryStatus = typeof route.query.status === 'string' ? route.query.status : ''
  if (selectedStatus !== queryStatus || route.query.id !== undefined) {
    // App.vue keys RouterView by route.fullPath, so this causes one fresh load.
    // The filter survives reloads and copied links; any selected order is cleared.
    router.replace({ path: '/orders', query: selectedStatus ? { status: selectedStatus } : {} })
    return
  }
  load()
}
onMounted(() => {
  load()
  if (route.query.id) choose(route.query.id)
})
</script>
<template>
  <div class="erp-orders">
    <div class="erp-orders-toolbar">
      <div class="erp-toolbar-controls">
        <label class="erp-filter-label" for="work-order-status">
          진행 상태
          <select id="work-order-status" v-model="statusFilter" class="select-filter" @change="changeFilter">
            <option value="">전체 상태</option>
            <option value="DRAFT">초안</option>
            <option value="RESERVED">자재 예약</option>
            <option value="IN_PROGRESS">생산 중</option>
            <option value="AWAITING_QC">검사 대기</option>
            <option value="COMPLETED">완료</option>
            <option value="CANCELED">취소</option>
          </select>
        </label>
        <button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16" /> 새로고침</button>
      </div>
      <button v-if="auth.canWrite" type="button" class="btn btn-primary" @click="showCreate = true">
        <Plus :size="17" /> 생산지시 생성
      </button>
    </div>

    <section class="erp-orders-sheet" aria-labelledby="erp-orders-heading">
      <header class="erp-sheet-head">
        <div>
          <h2 id="erp-orders-heading">생산지시 목록 <small aria-live="polite">{{ number(list.totalElements) }}건</small></h2>
          <p>번호를 선택하면 아래에서 원자재 소요량과 다음 처리 단계를 확인할 수 있습니다.</p>
        </div>
      </header>
      <AsyncState :loading="loading" :error="error" :empty="!list.content.length"
        label="해당 조건의 생산지시가 없습니다. 진행 상태 필터를 변경해 보세요." @retry="load">
        <div class="table-scroll">
          <table class="data-table erp-orders-table" aria-label="생산지시 목록">
            <thead>
              <tr>
                <th scope="col">생산지시 / 등록일</th>
                <th scope="col">완제품</th>
                <th scope="col" class="right">계획 수량</th>
                <th scope="col">진행 상태</th>
                <th scope="col">다음 작업</th>
                <th scope="col">상세</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="order in list.content" :key="order.id" :class="{ selected: selected?.id === order.id }">
                <td>
                  <button type="button" class="row-action" :aria-pressed="selected?.id === order.id"
                    @click="choose(order.id)">{{ order.orderNo }}</button>
                  <small class="cell-sub">{{ dateTime(order.createdAt) }}</small>
                </td>
                <td>{{ itemNameOrId(itemNames, order.finishedItemId, '완제품') }}</td>
                <td class="number-cell">{{ number(order.plannedQty) }} EA</td>
                <td><StatusBadge :status="order.status" /></td>
                <td>{{ nextAction(order.status) }}</td>
                <td><RouterLink class="erp-table-detail" :to="{ path: '/orders', query: { id: order.id } }"
                  :aria-label="order.orderNo + ' 상세 보기'">상세 보기 <ArrowRight :size="14" /></RouterLink></td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls :page="page" :total-pages="list.totalPages"
          :total-elements="list.totalElements" @change="changePage" />
      </AsyncState>
    </section>

    <section ref="detailSection" class="erp-orders-sheet detail-panel" aria-labelledby="erp-detail-heading">
      <header class="erp-sheet-head erp-detail-head">
        <div>
          <h2 id="erp-detail-heading">생산지시 상세 및 처리</h2>
          <p>선택한 생산지시의 작업 단계와 자재·품질 처리 내용을 확인합니다.</p>
        </div>
        <span v-if="selected" class="erp-detail-ref">{{ selected.orderNo }}</span>
      </header>
      <AsyncState :loading="detailLoading" :error="detailError"
        @retry="requestedDetailId && choose(requestedDetailId)">
        <div v-if="!selected" class="erp-detail-placeholder">
          <ClipboardList :size="22" :stroke-width="1.7" />
          <span>위 생산지시 목록에서 지시번호 또는 ‘상세 보기’를 선택해 주세요.</span>
        </div>
        <div v-else class="erp-detail-content">
          <div class="erp-order-meta">
            <div><small>생산지시번호</small><strong>{{ selected.orderNo }}</strong></div>
            <div><small>완제품</small><strong>{{ selectedItemName }}</strong></div>
            <div><small>계획 수량</small><strong class="erp-tabular">{{ number(selected.plannedQty) }} EA</strong></div>
            <div><small>진행 상태</small><StatusBadge :status="selected.status" /></div>
          </div>

          <div class="erp-stage-strip" aria-label="생산지시 진행 단계">
            <div v-for="(stage, idx) in stages" :key="stage.key"
              :class="['erp-stage', { complete: selected.status !== 'CANCELED' && idx < currentStage, current: selected.status !== 'CANCELED' && idx === currentStage }]">
              <span>{{ idx + 1 }}. {{ stage.label }}</span>
            </div>
          </div>
          <p v-if="selected.status === 'CANCELED'" class="erp-canceled-note">취소된 생산지시입니다. 예약된 자재는 취소 처리 시 해제됩니다.</p>

          <div class="erp-detail-layout">
            <section class="erp-material-section" aria-labelledby="erp-material-heading">
              <div class="erp-subheading">
                <h3 id="erp-material-heading">소요 원자재</h3>
                <p>BOM 기반 생산지시 생성 시점의 필요 수량</p>
              </div>
              <div class="table-scroll">
                <table class="erp-material-table" aria-label="선택 생산지시 소요 원자재">
                  <thead><tr>
                    <th scope="col">원자재 / SKU</th>
                    <th scope="col" class="erp-qty-col">필요 수량</th>
                  </tr></thead>
                  <tbody>
                    <tr v-for="material in selected.materials" :key="material.materialItemId">
                      <td><strong>{{ itemNameOrId(itemNames, material.materialItemId, '원자재') }}</strong>
                        <small>{{ itemSkuOrId(itemNames, material.materialItemId) }}</small></td>
                      <td class="erp-qty-col erp-tabular">{{ number(material.requiredQty) }} EA</td>
                    </tr>
                    <tr v-if="!selected.materials.length"><td colspan="2" class="erp-material-empty">등록된 소요 원자재가 없습니다.</td></tr>
                  </tbody>
                </table>
              </div>
              <p class="erp-note">실제 LOT별 예약·소모 상세는 이 화면의 조회 API에서 제공하지 않습니다.</p>
            </section>

            <section class="erp-action-section" aria-labelledby="erp-action-heading">
              <div class="erp-subheading">
                <h3 id="erp-action-heading">현재 단계의 처리</h3>
                <p>생산지시 상태에 따라 실행 가능한 작업만 표시됩니다.</p>
              </div>
              <div v-if="selected.status === 'AWAITING_QC'" class="qc-inline erp-qc-form">
                <p class="erp-qc-intro"><CircleAlert :size="17" />합격 수량만 완제품 LOT 재고로 반영됩니다.</p>
                <div class="form-row">
                  <label class="field-label">합격 수량<input v-model.number="qcForm.passedQty" type="number" min="0" step="1" :disabled="acting" /></label>
                  <label class="field-label">불합격 수량<input v-model.number="qcForm.failedQty" type="number" min="0" step="1" :disabled="acting" /></label>
                </div>
                <p :class="['erp-qc-total', { invalid: !qcValid }]" role="status">
                  입력 합계 {{ number(Number(qcForm.passedQty) + Number(qcForm.failedQty)) }} / 계획 {{ number(selected.plannedQty) }} EA
                  <span>{{ qcValid ? '합계 일치' : '수량 합계 또는 입력값 확인 필요' }}</span>
                </p>
              </div>

              <p v-if="!actions.length" class="small-empty">이 생산지시는 더 이상 실행할 작업이 없습니다.</p>
              <p v-if="actions.length && !auth.canWrite" class="erp-readonly">현재 계정은 조회 전용입니다. 작업 실행은 관리자만 가능합니다.</p>
              <div v-if="auth.canWrite && actions.length" class="action-stack">
                <button v-for="action in actions" :key="action.op" type="button"
                  :disabled="acting || (action.op === 'inspection' && !qcValid)"
                  :class="['btn', action.danger ? 'btn-danger-outline' : 'btn-primary']"
                  @click="runAction(action.op)">
                  <CheckCheck v-if="action.op === 'inspection'" :size="17" />
                  <ArrowRight v-else :size="17" />{{ action.label }}
                </button>
              </div>
              <p v-if="selected.status === 'DRAFT'" class="erp-note">자재 예약은 원자재 전량을 확보할 수 있을 때만 성공합니다.</p>
              <p v-else-if="selected.status === 'RESERVED'" class="erp-note">생산 시작 시 예약된 원자재가 소모됩니다.</p>
              <p v-else-if="selected.status === 'IN_PROGRESS'" class="erp-note">생산 완료 후 품질검사를 확정해야 완제품 재고가 반영됩니다.</p>
            </section>
          </div>
        </div>
      </AsyncState>
    </section>

    <div v-if="showCreate" class="dialog-backdrop" @click.self="showCreate = false">
      <section class="dialog" role="dialog" aria-modal="true" aria-label="생산지시 생성">
        <header class="dialog-header">
          <div><h2>생산지시 생성</h2></div>
          <button type="button" class="icon-btn" aria-label="닫기" @click="showCreate = false"><X :size="20" /></button>
        </header>
        <form @submit.prevent="createOrder">
          <div class="dialog-body">
            <div class="field-label"><span class="field-title">완제품 <em class="required-mark">*</em></span>
              <ItemSearchSelect v-model="createForm.finishedItemId" item-type="FINISHED_GOOD"
                placeholder="완제품 검색 · 선택" :disabled="saving" /></div>
            <label class="field-label">계획 수량 (EA) <span>*</span>
              <input v-model.number="createForm.plannedQty" type="number" min="1" step="1" required :disabled="saving" />
            </label>
            <div class="info-hint">생산지시 생성은 DRAFT 상태만 만듭니다. 원자재 예약은 생성 후 별도 작업으로 진행합니다. 선택한 완제품의 BOM이 등록되어 있어야 합니다.</div>
          </div>
          <footer class="dialog-footer">
            <button type="button" class="btn btn-outline" :disabled="saving" @click="showCreate = false">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="saving || !createForm.finishedItemId">{{ saving ? '생성 중...' : '생산지시 생성' }}</button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
