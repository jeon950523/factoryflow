<script setup>
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Truck, Plus, RefreshCw, X, PackageCheck, ArrowRight } from 'lucide-vue-next'
import './shipments-workbench.css'
import { ApiError, get, post } from '../lib/api.js'
import { readPendingWrite, savePendingWrite, clearPendingWrite, isUncertainWriteError, isConfirmedWriteRejection } from '../lib/pendingWrite.js'
import { useAuthStore } from '../stores/auth.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { lookupItemNames, itemNameOrId } from '../lib/itemNames.js'
import { number, dateTime, requestKey } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'

const ui = useUiStore()
const route = useRoute(), router = useRouter()
const auth = useAuthStore()
const listGate = createLatestRequest(), detailGate = createLatestRequest()
onBeforeUnmount(() => { listGate.cancel(); detailGate.cancel() })
const requestedPage = Number(route.query.page)
const initialPage = typeof route.query.page === 'string' && /^\d+$/.test(route.query.page)
  && Number.isSafeInteger(requestedPage) ? requestedPage : 0
const page = ref(initialPage), list = ref({ content: [], totalPages: 0, totalElements: 0 })
const pendingShipment = ref(readPendingWrite('shipment'))
const loading = ref(false), error = ref(''), drawer = ref(Boolean(pendingShipment.value)), saving = ref(false)
const pendingPersisted = ref(true)
const itemNames = ref({}), selected = ref(null), detailLoading = ref(false), detailError = ref('')
const detailSection = ref(null), requestedId = ref(null)
const allocatedQty = computed(() => selected.value?.allocations?.reduce((sum, row) => sum + row.quantity, 0) ?? 0)
const shipment = ref(pendingShipment.value
  ? { ...pendingShipment.value } : { requestKey: requestKey('SHIP'), finishedItemId: '', quantity: 1 })
async function load() {
  const request = listGate.begin()
  loading.value = true; error.value = ''
  try {
    const rows = await get('/api/v1/shipments', { page: page.value, size: 15 }, { signal: request.signal })
    if (!request.isCurrent()) return
    list.value = rows
    try {
      const names = await lookupItemNames(rows.content.map(row => row.finishedItemId), { signal: request.signal })
      if (!request.isCurrent()) return
      itemNames.value = { ...itemNames.value, ...names }
    } catch (e) {
      if (request.isCurrent() && !isAbortError(e)) ui.notify('완제품명 조회에 실패했습니다. 품목 ID로 표시합니다.', 'error')
    }
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}
async function choose(id) {
  requestedId.value = id
  const request = detailGate.begin()
  detailLoading.value = true; detailError.value = ''
  selected.value = null
  try {
    const detail = await get(`/api/v1/shipments/${id}`, undefined, { signal: request.signal })
    if (!request.isCurrent()) return
    selected.value = detail
    await nextTick()
    if (request.isCurrent()) detailSection.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
    try {
      const names = await lookupItemNames([detail.finishedItemId], { signal: request.signal })
      if (!request.isCurrent()) return
      itemNames.value = { ...itemNames.value, ...names }
    } catch (e) {
      if (request.isCurrent() && !isAbortError(e)) ui.notify('완제품명 조회에 실패했습니다. 품목 ID로 표시합니다.', 'error')
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
function openCreate() {
  shipment.value = pendingShipment.value ? { ...pendingShipment.value } : {
    requestKey: requestKey('SHIP'), finishedItemId: '', quantity: 1,
  }
  drawer.value = true
}
async function submit() {
  if (saving.value) return
  const quantity = Number(shipment.value.quantity)
  const finishedItemId = Number(shipment.value.finishedItemId)
  if (!Number.isSafeInteger(quantity) || quantity < 1
      || !Number.isSafeInteger(finishedItemId) || finishedItemId < 1) {
    ui.notify('완제품과 올바른 출고 수량을 입력하세요.', 'error')
    return
  }
  const payload = pendingShipment.value ?? {
    requestKey: shipment.value.requestKey, finishedItemId, quantity,
  }
  let created
  saving.value = true
  try {
    created = await post('/api/v1/shipments', payload)
    if (!Number.isSafeInteger(created?.id) || created?.requestKey?.toUpperCase() !== payload.requestKey.toUpperCase()
        || created.finishedItemId !== payload.finishedItemId || created.quantity !== payload.quantity) {
      throw new ApiError('출고 결과를 확인할 수 없습니다.', { status: 200, code: 'INVALID_RESPONSE' })
    }
  } catch (e) {
    if (isUncertainWriteError(e)) {
      pendingShipment.value = payload
      pendingPersisted.value = savePendingWrite('shipment', payload)
      ui.notify('출고 처리 여부를 확인할 수 없습니다. 동일 요청으로만 재시도하세요.', 'error')
    } else if (pendingShipment.value && isConfirmedWriteRejection(e)) {
      // A definitive rejected command did not commit. A corrected attempt is a NEW request.
      pendingShipment.value = null
      clearPendingWrite('shipment')
      shipment.value = {
        ...shipment.value, requestKey: requestKey('SHIP'),
      }
      ui.notify(`${e.message} 입력을 수정해 새 요청으로 진행할 수 있습니다.`, 'error')
    } else {
      ui.notify(e.message, 'error')
    }
    return
  } finally {
    saving.value = false
  }
  pendingShipment.value = null
  clearPendingWrite('shipment')
  drawer.value = false
  ui.notify('완제품 출고를 확정했습니다.')
  try {
    await router.replace({ path: '/shipments', query: { id: String(created.id) } })
  } catch {
    ui.notify('출고는 완료됐지만 상세 화면을 열지 못했습니다. 이력 새로고침을 이용하세요.', 'error')
  }
}
function changePage(next) {
  if (next < 0 || next >= list.value.totalPages) return
  router.replace({ path: '/shipments', query: next ? { page: String(next) } : {} })
}
function shipmentLink(id) {
  return { path: '/shipments', query: {
    ...(page.value > 0 ? { page: String(page.value) } : {}),
    id: String(id),
  } }
}
function openShipment(row) {
  if (String(route.query.id ?? '') === String(row.id)) {
    choose(row.id)
    return
  }
  router.replace(shipmentLink(row.id))
}
function closeDrawer() { if (!saving.value) drawer.value = false }
onMounted(() => {
  load()
  const id = route.query.id
  if (typeof id === 'string' && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id))) choose(Number(id))
})
</script>
<template>
  <div class="erp-shipments">
    <div class="erp-shipment-toolbar">
      <div class="erp-shipment-helper">
        <Truck :size="17" />
        <span>검사 합격 완제품을 출고하고, 출고 당시의 LOT별 차감량을 추적합니다.</span>
      </div>
      <div class="erp-shipment-toolbar-actions">
        <button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16" /> 이력 새로고침</button>
        <button v-if="auth.canWrite" type="button" class="btn btn-primary" @click="openCreate">
          <Plus :size="17" /> {{ pendingShipment ? '미확인 출고 요청 확인' : '출고 등록' }}
        </button>
      </div>
    </div>

    <section class="erp-shipment-sheet" aria-labelledby="shipments-list-title">
      <div class="erp-shipment-heading">
        <div><h2 id="shipments-list-title">완제품 출고 이력 <small>{{ number(list.totalElements) }}건</small></h2>
          <p>출고 기록을 선택하면 실제로 차감된 LOT ID와 배분 수량을 아래에서 확인할 수 있습니다.</p></div>
      </div>
      <AsyncState :loading="loading" :error="error" :empty="!list.content.length"
        label="출고 이력이 없습니다. 품질검사를 완료한 완제품을 출고해 보세요." @retry="load">
        <div class="table-scroll">
          <table class="data-table erp-shipment-table" aria-label="완제품 출고 이력">
            <thead><tr>
              <th scope="col">출고 요청 / 번호</th>
              <th scope="col">완제품</th>
              <th scope="col" class="right">출고 수량</th>
              <th scope="col">처리일</th>
              <th scope="col">상세</th>
            </tr></thead>
            <tbody>
              <tr v-for="row in list.content" :key="row.id" :class="{ selected: selected?.id === row.id }">
                <td><button type="button" class="row-action" :aria-pressed="selected?.id === row.id"
                  @click="openShipment(row)">{{ row.requestKey }}</button>
                  <small class="cell-sub">출고 #{{ row.id }}</small></td>
                <td>{{ itemNameOrId(itemNames, row.finishedItemId, '완제품') }}</td>
                <td class="number-cell">{{ number(row.quantity) }} EA</td>
                <td>{{ dateTime(row.createdAt) }}</td>
                <td><RouterLink class="erp-shipment-open"
                  :aria-label="`${row.requestKey} 상세 보기`"
                  :to="shipmentLink(row.id)">
                  상세 보기 <ArrowRight :size="14" /></RouterLink></td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls :page="page" :total-pages="list.totalPages"
          :total-elements="list.totalElements" @change="changePage" />
      </AsyncState>
    </section>

    <section ref="detailSection" class="erp-shipment-sheet detail-panel" aria-labelledby="shipment-detail-title">
      <div class="erp-shipment-heading">
        <div><h2 id="shipment-detail-title">출고 상세 · LOT 배분</h2>
          <p>출고 확정 당시 배분된 LOT 식별번호와 수량 기록입니다.</p></div>
        <span v-if="selected" class="erp-shipment-ref">출고 #{{ selected.id }}</span>
      </div>
      <AsyncState :loading="detailLoading" :error="detailError"
        @retry="requestedId && choose(requestedId)">
        <div v-if="!selected" class="erp-shipment-empty">
          <PackageCheck :size="22" :stroke-width="1.7" />
          <span>위 출고 이력에서 요청번호 또는 ‘상세 보기’를 선택해 주세요.</span>
        </div>
        <div v-else>
          <div class="erp-shipment-meta" aria-label="선택 출고 기본 정보">
            <div><small>출고 번호</small><strong>#{{ selected.id }}</strong></div>
            <div><small>출고 요청 키</small><strong>{{ selected.requestKey }}</strong></div>
            <div><small>완제품</small><strong>{{ itemNameOrId(itemNames, selected.finishedItemId, '완제품') }}</strong></div>
            <div><small>출고 수량</small><strong>{{ number(selected.quantity) }} EA</strong></div>
            <div><small>처리 시각</small><strong>{{ dateTime(selected.createdAt) }}</strong></div>
          </div>

          <div class="erp-shipment-allocation">
            <div class="erp-shipment-subheading">
              <div><h3>LOT별 출고 배분 기록</h3>
                <p>검사 합격품을 FIFO 기준으로 배분해 차감한 원장 기록입니다. 코드 대신 LOT ID를 제공합니다.</p></div>
              <div class="erp-shipment-trace-links">
                <RouterLink class="erp-shipment-lookup" :to="{ path: '/inventory',
                  query: { itemType: 'FINISHED_GOOD', itemId: String(selected.finishedItemId) } }">
                  해당 완제품 LOT 현황 <ArrowRight :size="14" />
                </RouterLink>
                <RouterLink class="erp-shipment-lookup" :to="{ path: '/movements',
                  query: { itemId: String(selected.finishedItemId) } }">
                  완제품 이동 이력 <ArrowRight :size="14" />
                </RouterLink>
              </div>
            </div>
            <div class="table-scroll">
              <table class="data-table erp-shipment-allocation-table" aria-label="LOT별 출고 배분 내역">
                <thead><tr>
                  <th scope="col">LOT 식별번호</th>
                  <th scope="col" class="right">출고 차감 수량</th>
                </tr></thead>
                <tbody>
                  <tr v-for="allocation in selected.allocations" :key="allocation.lotId">
                    <td>LOT #{{ allocation.lotId }}</td>
                    <td class="number-cell">{{ number(allocation.quantity) }} EA</td>
                  </tr>
                  <tr v-if="!selected.allocations?.length"><td colspan="2" class="table-empty">LOT 배분 기록이 없습니다.</td></tr>
                </tbody>
                <tfoot><tr><th scope="row">LOT 차감 합계</th>
                  <td class="number-cell">{{ number(allocatedQty) }} EA</td></tr></tfoot>
              </table>
            </div>
            <p v-if="allocatedQty !== selected.quantity" class="erp-shipment-warning" role="alert">
              출고 수량과 LOT 배분 합계가 일치하지 않습니다. 출고 기록을 확인해 주세요.
            </p>
            <p class="erp-shipment-note">출고 취소·반품은 현재 MVP 범위가 아닙니다. 이 표는 출고 당시 배분 내역이며 현재 LOT 잔량은 재고 화면에서 따로 확인합니다.</p>
          </div>
        </div>
      </AsyncState>
    </section>

    <div v-if="drawer" class="dialog-backdrop" @click.self="closeDrawer">
      <section v-dialog-focus="{ close: closeDrawer, locked: saving }" class="dialog" role="dialog" aria-modal="true" aria-label="완제품 출고 등록">
        <header class="dialog-header">
          <div><h2>완제품 출고 등록</h2><p>검사 합격 재고가 충분할 때만 확정됩니다.</p></div>
          <button type="button" class="icon-btn" :disabled="saving" aria-label="닫기" @click="closeDrawer"><X :size="20" /></button>
        </header>
        <form @submit.prevent="submit">
          <div class="dialog-body">
            <div class="field-label">
              <span class="field-title">완제품 <em class="required-mark">*</em></span>
              <ItemSearchSelect v-model="shipment.finishedItemId" item-type="FINISHED_GOOD"
                placeholder="출고할 완제품 검색 · 선택" :disabled="saving || !!pendingShipment" />
            </div>
            <label class="field-label">출고 수량 (EA) <span>*</span>
              <input v-model.number="shipment.quantity" type="number" min="1" step="1" required :disabled="saving || !!pendingShipment" />
            </label>
            <p v-if="pendingShipment" class="info-hint" role="alert">
              출고 결과가 미확인 상태입니다. 중복 출고를 막기 위해 요청 키 {{ pendingShipment.requestKey }}와 수량을 고정했습니다.
              이 요청으로만 다시 전송하세요.
              {{ pendingPersisted ? '같은 탭의 닫기·새로고침 후에도 요청이 복원됩니다.' : '브라우저 저장이 제한되어 새로고침 시 복원되지 않을 수 있습니다.' }}
            </p>
            <p v-else class="info-hint">검사 합격 재고만 출고할 수 있으며, 부족하면 전체 거절됩니다. 응답이 유실되면 새 요청을 만들지 말고 동일 요청으로 재시도하세요.</p>
          </div>
          <footer class="dialog-footer">
            <button type="button" class="btn btn-outline" :disabled="saving" @click="closeDrawer">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="saving || !shipment.finishedItemId">
              {{ saving ? '출고 중...' : pendingShipment ? '동일 요청 재시도' : '출고 확정' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
