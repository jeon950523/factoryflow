<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Plus, RefreshCw, X, ArrowRight, Layers } from 'lucide-vue-next'
import { ApiError, get, post, isDemoMode } from '../lib/api.js'
import { readPendingWrite, readSharedPendingWrite, claimPendingWrite, releasePendingWrite, observeSharedPendingWrite, isUncertainWriteError, isConfirmedWriteRejection } from '../lib/pendingWrite.js'
import { useAuthStore } from '../stores/auth.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { number, dateTime, requestKey } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'
import './inventory-workbench.css'

const route = useRoute(), router = useRouter()
const ui = useUiStore(), auth = useAuthStore()
const listGate = createLatestRequest(), lotGate = createLatestRequest()
let mounted = true
onBeforeUnmount(() => { mounted = false; listGate.cancel(); lotGate.cancel(); stopObservingPending() })

// URL filters use only fields understood by both Spring Boot and the demo engine.
const types = new Set(['RAW_MATERIAL', 'FINISHED_GOOD'])
const initialType = typeof route.query.itemType === 'string' && types.has(route.query.itemType)
  ? route.query.itemType : ''
const parsedItemId = Number(route.query.itemId)
const initialItemId = typeof route.query.itemId === 'string' && /^[1-9]\d*$/.test(route.query.itemId)
  && Number.isSafeInteger(parsedItemId) ? String(parsedItemId) : ''
const parsedPage = Number(route.query.page)
const initialPage = typeof route.query.page === 'string' && /^\d+$/.test(route.query.page)
  && Number.isSafeInteger(parsedPage) ? parsedPage : 0

const itemType = ref(initialType), page = ref(initialPage)
const list = ref({ content: [], totalPages: 0, totalElements: 0 })
const loading = ref(false), error = ref('')
const selectedId = ref(initialItemId)
const selectedSummary = ref(null), lotPage = ref(0)
const lots = ref({ content: [], totalPages: 0, totalElements: 0 })
const lotsLoading = ref(false), lotsError = ref('')
const lotSection = ref(null)
const pendingReceipt = ref(readPendingWrite('receipt', auth.user?.username))
const drawer = ref(Boolean(pendingReceipt.value)), saving = ref(false)
const receipt = ref(pendingReceipt.value
  ? { ...pendingReceipt.value } : { itemId: '', lotCode: '', quantity: 1, requestKey: requestKey('REC') })
const pendingPersisted = ref(true)
const otherTabConflict = ref(false)
let stopObservingPending = () => {}

// This is a visible-page metric, not an inventory shortage alert.
const zeroAvailableOnPage = computed(() => list.value.content.filter(row => row.available === 0).length)
const preselectedReceiptItem = computed(() => {
  const item = selectedSummary.value
  if (!item || Number(receipt.value.itemId) !== Number(item.itemId)) return null
  return { id: Number(item.itemId), sku: item.sku, name: item.name }
})
const selectedName = computed(() => selectedSummary.value?.name ??
  (selectedId.value ? `품목 #${selectedId.value}` : null))

function inventoryQuery({ type = itemType.value, pageNo = page.value, itemId = selectedId.value } = {}) {
  return {
    ...(type ? { itemType: type } : {}),
    ...(pageNo > 0 ? { page: String(pageNo) } : {}),
    ...(itemId ? { itemId: String(itemId) } : {}),
  }
}

async function load() {
  const request = listGate.begin()
  loading.value = true
  error.value = ''
  try {
    const data = await get('/api/v1/inventory', {
      itemType: itemType.value || undefined, page: page.value, size: 15,
    }, { signal: request.signal })
    if (!request.isCurrent()) return
    list.value = data
    const onPage = data.content.find(row => String(row.itemId) === selectedId.value)
    if (onPage) selectedSummary.value = onPage
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}

async function loadLots({ scroll = false } = {}) {
  const request = lotGate.begin()
  lots.value = { content: [], totalElements: 0, totalPages: 0 }
  lotsError.value = ''
  if (!selectedId.value) {
    lotsLoading.value = false
    return
  }
  lotsLoading.value = true
  try {
    const itemId = Number(selectedId.value)
    // A detail link can reference an item outside the current 15-row page;
    // query its real summary instead of inventing a name or stock totals.
    const [data, summary] = await Promise.all([
      get('/api/v1/inventory/lots',
        { itemId, page: lotPage.value, size: 15 }, { signal: request.signal }),
      selectedSummary.value ? Promise.resolve(null) : get('/api/v1/inventory',
        { itemId, page: 0, size: 1 }, { signal: request.signal })
        .catch(e => { if (isAbortError(e)) throw e; return null }),
    ])
    if (!request.isCurrent()) return
    lots.value = data
    if (summary?.content?.[0]) selectedSummary.value = summary.content[0]
    if (scroll) {
      await nextTick()
      if (request.isCurrent()) lotSection.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
    }
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) lotsError.value = e.message
  } finally {
    if (request.isCurrent()) lotsLoading.value = false
  }
}

async function choose(item) {
  const id = String(item.itemId)
  if (selectedId.value === id) {
    selectedSummary.value = item
    await loadLots({ scroll: true })
    return
  }
  await router.replace({ path: '/inventory', query: inventoryQuery({ itemId: id }) })
}

function changeType() {
  // Changing the item type clears stale LOT selection and returns to page one.
  router.replace({ path: '/inventory', query: inventoryQuery({ type: itemType.value, pageNo: 0, itemId: '' }) })
}

function changePage(next) {
  router.replace({ path: '/inventory', query: inventoryQuery({ pageNo: next, itemId: '' }) })
}
function changeLotPage(next) { lotPage.value = next; loadLots() }

function refreshOtherTabPending() {
  if (saving.value) return
  const shared = readSharedPendingWrite('receipt', auth.user?.username)
  if (shared) {
    // Do not replace a different unresolved tab-local intent with a remote key.
    const own = readPendingWrite('receipt', auth.user?.username)
    if ((own && JSON.stringify(own) !== JSON.stringify(shared))
      || (drawer.value && !pendingReceipt.value)) {
      otherTabConflict.value = true
      return
    }
    pendingReceipt.value = shared
    receipt.value = { ...shared }
    return
  }
  otherTabConflict.value = false
  const own = readPendingWrite('receipt', auth.user?.username)
  if (!own && pendingReceipt.value) {
    pendingReceipt.value = null
    drawer.value = false
    ui.notify('다른 탭에서 입고 요청 상태가 변경됐습니다. 재고와 이력을 확인하세요.')
    load(); loadLots()
  }
}

function openReceipt(itemId = '') {
  pendingReceipt.value = readPendingWrite('receipt', auth.user?.username)
  otherTabConflict.value = false
  receipt.value = pendingReceipt.value ? { ...pendingReceipt.value } : {
    itemId: itemId ? String(itemId) : '', lotCode: '', quantity: 1, requestKey: requestKey('REC'),
  }
  drawer.value = true
}
function closeReceipt() { if (!saving.value) drawer.value = false }

async function submitReceipt() {
  if (saving.value) return
  const itemId = Number(receipt.value.itemId)
  const quantity = Number(receipt.value.quantity)
  const lotCode = String(receipt.value.lotCode ?? '').trim()
  if (!Number.isSafeInteger(itemId) || itemId < 1 || !Number.isSafeInteger(quantity)
      || quantity < 1 || !lotCode) {
    ui.notify('원자재, LOT 코드와 1 이상의 정수 입고 수량을 확인하세요.', 'error')
    return
  }
  const payload = pendingReceipt.value ?? {
    requestKey: receipt.value.requestKey, itemId, lotCode, quantity,
  }
  let created
  saving.value = true
  // Record the exact command BEFORE dispatch. A refresh while fetch is in flight
  // must not mint a second key for a command already committed on the server.
  const claimed = await claimPendingWrite('receipt', payload, auth.user?.username)
  if (!claimed.ok) {
    saving.value = false
    otherTabConflict.value = claimed.reason === 'OTHER_PENDING'
    const old = readPendingWrite('receipt', auth.user?.username)
    if (old && claimed.reason === 'OTHER_PENDING') {
      pendingReceipt.value = old
      receipt.value = { ...old }
    }
    ui.notify(claimed.reason === 'OTHER_PENDING'
      ? '다른 탭에 미확인 입고 요청이 있습니다. 새 요청을 전송하지 않고 원래 요청을 확인하세요.'
      : '입고 요청을 브라우저에 안전하게 저장하지 못해 전송하지 않았습니다. 저장소·브라우저 잠금 지원을 확인하세요.', 'error')
    return
  }
  otherTabConflict.value = false
  pendingPersisted.value = true
  try {
    created = await post('/api/v1/stock-receipts', payload)
    if (!Number.isSafeInteger(created?.id) || created?.requestKey?.toUpperCase() !== payload.requestKey.toUpperCase()
        || created.itemId !== payload.itemId || created.quantity !== payload.quantity) {
      throw new ApiError('입고 결과를 확인할 수 없습니다.', { status: 200, code: 'INVALID_RESPONSE' })
    }
  } catch (e) {
    if (isUncertainWriteError(e)) {
      pendingReceipt.value = payload
      ui.notify('입고 처리 여부를 확인할 수 없습니다. 입력을 변경하지 말고 동일 요청을 재시도하세요.', 'error')
    } else if (isConfirmedWriteRejection(e)) {
      // Definitive rejection: clear the shared intent before issuing another key.
      const released = await releasePendingWrite('receipt', payload, auth.user?.username)
      if (released.ok) {
        pendingReceipt.value = null
        receipt.value = { ...receipt.value, requestKey: requestKey('REC') }
        ui.notify(`${e.message} 입력을 수정해 새 요청으로 진행할 수 있습니다.`, 'error')
      } else {
        pendingReceipt.value = payload
        ui.notify('입고는 거절됐지만 복구 기록을 지우지 못했습니다. 새 요청을 만들지 마세요.', 'error')
      }
    } else {
      // 401/403, unknown 409, and other errors do not prove the write was absent.
      pendingReceipt.value = payload
      ui.notify(`입고 결과를 확정할 수 없습니다. 동일 요청을 재시도하세요. (${e.message})`, 'error')
    }
    return
  } finally {
    saving.value = false
  }
  // Keep the form locked while the shared intent is being released.
  saving.value = true
  const released = await releasePendingWrite('receipt', payload, auth.user?.username)
  saving.value = false
  if (!released.ok) {
    pendingReceipt.value = payload
    ui.notify('입고 결과는 확인했지만 복구 기록 삭제에 실패했습니다. 동일 요청만 다시 확인하세요.', 'error')
    return
  }
  pendingReceipt.value = null
  drawer.value = false
  ui.notify(`LOT ${created.lotCode} 입고를 처리했습니다.`)
  const id = String(created.itemId)
  // Navigation errors after a confirmed write must not be mistaken for POST failures.
  try {
    const unchangedRoute = route.query.itemType === 'RAW_MATERIAL'
      && route.query.itemId === id && route.query.page === undefined
    if (!unchangedRoute) {
      await router.replace({ path: '/inventory',
        query: inventoryQuery({ type: 'RAW_MATERIAL', pageNo: 0, itemId: id }) })
    } else {
      selectedSummary.value = null
      await Promise.all([load(), loadLots({ scroll: true })])
    }
  } catch {
    ui.notify('입고는 완료됐지만 목록을 새로고침하지 못했습니다. 재고 새로고침을 이용하세요.', 'error')
  }
}

onMounted(async () => {
  stopObservingPending = observeSharedPendingWrite('receipt', refreshOtherTabPending)
  refreshOtherTabPending()
  await load()
  // Do not start a new LOT request after a route change unmounted this view.
  if (mounted && selectedId.value) await loadLots({ scroll: true })
})
</script>

<template>
  <div class="erp-inventory">
    <div class="erp-inventory-toolbar">
      <div class="erp-inventory-filters">
        <label for="inventory-type-filter">품목 유형</label>
        <select id="inventory-type-filter" v-model="itemType" class="select-filter" @change="changeType">
          <option value="">전체 품목</option>
          <option value="RAW_MATERIAL">원자재</option>
          <option value="FINISHED_GOOD">완제품</option>
        </select>
        <button type="button" class="btn btn-subtle" @click="load">
          <RefreshCw :size="16" /> 재고 새로고침
        </button>
      </div>
      <button v-if="auth.canWrite" type="button" class="btn btn-primary" @click="openReceipt()">
        <Plus :size="17" /> {{ pendingReceipt ? '미확인 입고 요청 확인' : '원자재 입고' }}
      </button>
    </div>

    <section class="erp-inventory-sheet" aria-labelledby="inventory-sheet-heading">
      <header class="erp-inventory-head">
        <div>
          <h2 id="inventory-sheet-heading">품목별 재고 현황 <small>{{ number(list.totalElements) }}종</small></h2>
          <p>현재고 − 예약고 = 가용재고 · 현재 페이지 {{ number(list.content.length) }}종 중 가용재고 0: {{ number(zeroAvailableOnPage) }}종</p>
        </div>
      </header>
      <AsyncState :loading="loading" :error="error" :empty="!list.content.length"
        label="조건에 맞는 재고 품목이 없습니다. 품목 유형을 변경하거나 품목을 등록해 주세요." @retry="load">
        <div class="table-scroll">
          <table class="data-table erp-inventory-table" aria-label="품목별 재고 현황">
            <thead><tr>
              <th scope="col">품목명 / SKU</th>
              <th scope="col">유형</th>
              <th scope="col" class="right">현재고</th>
              <th scope="col" class="right">예약고</th>
              <th scope="col" class="right">가용재고</th>
              <th scope="col">상세</th>
            </tr></thead>
            <tbody>
              <tr v-for="item in list.content" :key="item.itemId"
                :class="{ selected: String(item.itemId) === selectedId }">
                <td>
                  <button type="button" class="row-action"
                    :aria-pressed="String(item.itemId) === selectedId" @click="choose(item)">{{ item.name }}</button>
                  <small class="cell-sub">{{ item.sku }}</small>
                </td>
                <td><StatusBadge :status="item.itemType" /></td>
                <td class="number-cell">{{ number(item.onHand) }}</td>
                <td class="number-cell">{{ number(item.reserved) }}</td>
                <td class="number-cell" :class="{ 'number-alert': item.available === 0 }">{{ number(item.available) }}</td>
                <td><button type="button" class="erp-lot-link" :aria-label="`${item.name} LOT 보기`"
                  @click="choose(item)">LOT 보기 <ArrowRight :size="14" /></button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls :page="page" :total-pages="list.totalPages"
          :total-elements="list.totalElements" @change="changePage" />
      </AsyncState>
    </section>

    <section ref="lotSection" class="erp-inventory-sheet erp-lot-section" aria-labelledby="lot-sheet-heading">
      <header class="erp-inventory-head">
        <div>
          <h2 id="lot-sheet-heading">{{ selectedName ? `${selectedName} · LOT 상세` : 'LOT 상세' }}</h2>
          <p>등록·입고 순서에 따라 LOT별 재고를 확인합니다. 이 표는 FIFO 출고의 실제 배분 결과를 표시하지 않습니다.</p>
        </div>
        <div class="erp-lot-heading-actions">
          <RouterLink v-if="selectedId" class="erp-lot-trace-link"
            :to="{ path: '/movements', query: { itemId: selectedId } }">
            선택 품목 이동 이력 <ArrowRight :size="14" aria-hidden="true" />
          </RouterLink>
          <button v-if="auth.canWrite && selectedSummary?.itemType === 'RAW_MATERIAL'"
            type="button" class="btn btn-outline" @click="openReceipt(selectedSummary.itemId)">
            <Plus :size="15" /> {{ pendingReceipt ? '미확인 입고 요청 확인' : '선택 원자재 입고' }}
          </button>
        </div>
      </header>

      <div v-if="!selectedId" class="erp-lot-empty">
        <Layers :size="21" :stroke-width="1.7" />
        <span>위 목록의 품목명 또는 LOT 보기를 선택하면 해당 품목의 상세 재고가 표시됩니다.</span>
      </div>
      <template v-else>
        <div v-if="selectedSummary" class="erp-lot-summary" aria-label="선택 품목 재고 요약">
          <span>품목 <strong>{{ selectedSummary.sku }}</strong></span>
          <span>현재고 <strong>{{ number(selectedSummary.onHand) }} EA</strong></span>
          <span>예약고 <strong>{{ number(selectedSummary.reserved) }} EA</strong></span>
          <span>가용재고 <strong>{{ number(selectedSummary.available) }} EA</strong></span>
        </div>
        <AsyncState :loading="lotsLoading" :error="lotsError" :empty="!lots.content.length"
          label="이 품목에 아직 등록된 LOT이 없습니다." @retry="loadLots">
          <div class="table-scroll">
            <table class="data-table erp-lot-table" aria-label="선택 품목 LOT별 재고">
              <thead><tr>
                <th scope="col">LOT 코드</th>
                <th scope="col">입고 출처</th>
                <th scope="col">등록 시각</th>
                <th scope="col" class="right">누적 입고</th>
                <th scope="col" class="right">현재고</th>
                <th scope="col" class="right">예약고</th>
                <th scope="col" class="right">가용재고</th>
              </tr></thead>
              <tbody>
                <tr v-for="lot in lots.content" :key="lot.id">
                  <td><strong>{{ lot.lotCode }}</strong><small class="cell-sub">LOT #{{ lot.id }}</small></td>
                  <td><StatusBadge :status="lot.sourceType" /></td>
                  <td>{{ dateTime(lot.receivedAt) }}</td>
                  <td class="number-cell">{{ number(lot.cumulativeInQty) }}</td>
                  <td class="number-cell">{{ number(lot.onHand) }}</td>
                  <td class="number-cell">{{ number(lot.reserved) }}</td>
                  <td class="number-cell" :class="{ 'number-alert': lot.available === 0 }">{{ number(lot.available) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <PageControls :page="lotPage" :total-pages="lots.totalPages"
            :total-elements="lots.totalElements" @change="changeLotPage" />
        </AsyncState>
      </template>
    </section>

    <div v-if="drawer" class="dialog-backdrop" @click.self="closeReceipt">
      <section v-dialog-focus="{ close: closeReceipt, locked: saving }" class="dialog" role="dialog" aria-modal="true" aria-label="원자재 입고">
        <header class="dialog-header">
          <div><h2>원자재 입고</h2><p>원자재 LOT를 신규 또는 추가 입고합니다.</p></div>
          <button type="button" class="icon-btn" aria-label="닫기" :disabled="saving" @click="closeReceipt">
            <X :size="20" />
          </button>
        </header>
        <form @submit.prevent="submitReceipt">
          <div class="dialog-body">
            <div class="field-label">
              <span class="field-title">원자재 <em class="required-mark">*</em></span>
              <ItemSearchSelect v-model="receipt.itemId" item-type="RAW_MATERIAL"
                :preselected-item="preselectedReceiptItem"
                placeholder="원자재 검색 · 선택" :disabled="saving || !!pendingReceipt" />
            </div>
            <label class="field-label">LOT 코드 <span>*</span>
              <input v-model.trim="receipt.lotCode" maxlength="80" required
                placeholder="LOT-20261111-001" :disabled="saving || !!pendingReceipt" />
            </label>
            <label class="field-label">입고 수량 (EA) <span>*</span>
              <input v-model.number="receipt.quantity" type="number" min="1" step="1" required :disabled="saving || !!pendingReceipt" />
            </label>
            <p v-if="otherTabConflict" class="info-hint" role="alert">다른 탭에 미확인 입고 요청이 있습니다. 새 요청 전송이 차단됩니다. 원래 요청을 확인하세요.</p>
            <p v-if="pendingReceipt" class="info-hint" role="alert">
              입고 결과를 확인하지 못했습니다. 중복 처리를 막기 위해 요청 키 {{ pendingReceipt.requestKey }}와 입력 내용을 고정했습니다.
              같은 요청을 재시도하면 이미 처리된 입고는 다시 반영되지 않습니다.
              {{ pendingPersisted ? '이 탭에서 창을 닫거나 새로고침해도 복원됩니다.' : '브라우저 저장이 제한되어 새로고침 시 복원되지 않을 수 있습니다.' }}
            </p>
            <div v-else class="info-hint">
              같은 원자재의 동일 LOT 코드에 추가 입고할 수 있습니다.
              통신 실패 시 같은 요청 키로만 재시도하며, 처리 결과를 확인하기 전 새 입고를 만들지 마세요.
            </div>
          </div>
          <footer class="dialog-footer">
            <button type="button" class="btn btn-outline" :disabled="saving" @click="closeReceipt">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="saving || !receipt.itemId">
              {{ saving ? '처리 중...' : pendingReceipt ? '동일 요청 재시도' : '입고 확정' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
