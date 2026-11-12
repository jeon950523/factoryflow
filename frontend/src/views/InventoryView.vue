<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Plus, RefreshCw, X, ArrowRight, Layers } from 'lucide-vue-next'
import { get, post, isDemoMode } from '../lib/api.js'
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
onBeforeUnmount(() => { listGate.cancel(); lotGate.cancel() })

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
const drawer = ref(false), saving = ref(false)
const receipt = ref({ itemId: '', lotCode: '', quantity: 1, requestKey: requestKey('REC') })

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

function openReceipt(itemId = '') {
  receipt.value = { itemId: itemId ? String(itemId) : '', lotCode: '', quantity: 1,
    requestKey: requestKey('REC') }
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
  saving.value = true
  try {
    const created = await post('/api/v1/stock-receipts',
      { ...receipt.value, itemId, quantity, lotCode })
    drawer.value = false
    ui.notify(`LOT ${created.lotCode} 입고를 처리했습니다.`)
    const id = String(created.itemId)
    // Keep the receipt's actual inventory item visible even when it was not
    // on the current list page or the current type filter was FINISHED_GOOD.
    const unchangedRoute = route.query.itemType === 'RAW_MATERIAL'
      && route.query.itemId === id && route.query.page === undefined
    if (!unchangedRoute) {
      await router.replace({ path: '/inventory',
        query: inventoryQuery({ type: 'RAW_MATERIAL', pageNo: 0, itemId: id }) })
    } else {
      // Refresh even if the item is not included in the visible first page.
      selectedSummary.value = null
      await Promise.all([load(), loadLots({ scroll: true })])
    }
  } catch (e) {
    // Keep the same idempotency request key on retry after a network failure.
    ui.notify(e.message, 'error')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  if (selectedId.value) await loadLots({ scroll: true })
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
        <Plus :size="17" /> 원자재 입고
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
        <button v-if="auth.canWrite && selectedSummary?.itemType === 'RAW_MATERIAL'"
          type="button" class="btn btn-outline" @click="openReceipt(selectedSummary.itemId)">
          <Plus :size="15" /> 선택 원자재 입고
        </button>
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
      <section class="dialog" role="dialog" aria-modal="true" aria-label="원자재 입고">
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
                placeholder="원자재 검색 · 선택" :disabled="saving" />
            </div>
            <label class="field-label">LOT 코드 <span>*</span>
              <input v-model.trim="receipt.lotCode" maxlength="80" required
                placeholder="LOT-20261111-001" :disabled="saving" />
            </label>
            <label class="field-label">입고 수량 (EA) <span>*</span>
              <input v-model.number="receipt.quantity" type="number" min="1" step="1" required :disabled="saving" />
            </label>
            <div class="info-hint">
              같은 원자재의 동일 LOT 코드에 추가 입고할 수 있습니다.
              요청이 실패해 재시도할 때는 중복 입고 방지를 위해 같은 요청 키를 유지합니다.
            </div>
          </div>
          <footer class="dialog-footer">
            <button type="button" class="btn btn-outline" :disabled="saving" @click="closeReceipt">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="saving || !receipt.itemId">
              {{ saving ? '처리 중...' : '입고 확정' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
