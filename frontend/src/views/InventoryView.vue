<script setup>
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { Plus, RefreshCw, Warehouse, X, Layers } from 'lucide-vue-next'
import { get, post, isDemoMode } from '../lib/api.js'
import { useAuthStore } from '../stores/auth.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { number, dateTime, requestKey } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'

const ui = useUiStore()
const auth = useAuthStore()
const listGate = createLatestRequest(), lotGate = createLatestRequest()
onBeforeUnmount(() => { listGate.cancel(); lotGate.cancel() })
const page = ref(0), list = ref({ content: [], totalPages: 0, totalElements: 0 })
const loading = ref(false), error = ref('')
const itemFilter = ref(''), lotPage = ref(0), lots = ref({ content: [], totalPages: 0, totalElements: 0 })
const lotsLoading = ref(false), lotsError = ref('')
const drawer = ref(false), saving = ref(false)
const receipt = ref({ itemId: '', lotCode: '', quantity: 1, requestKey: requestKey('REC') })
const unavailableOnPage = computed(() => list.value.content.filter(item => item.available === 0).length)
const selectedItem = computed(() => list.value.content.find(item => String(item.itemId) === itemFilter.value))

async function load() {
  const request = listGate.begin()
  loading.value = true; error.value = ''
  try {
    const data = await get('/api/v1/inventory', { page: page.value, size: 15 }, { signal: request.signal })
    if (request.isCurrent()) list.value = data
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}
async function loadLots() {
  const request = lotGate.begin()
  lots.value = { content: [], totalElements: 0, totalPages: 0 }
  if (!itemFilter.value) return
  lotsLoading.value = true; lotsError.value = ''
  try {
    const data = await get('/api/v1/inventory/lots',
      { itemId: itemFilter.value, page: lotPage.value, size: 15 }, { signal: request.signal })
    if (request.isCurrent()) lots.value = data
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) lotsError.value = e.message
  } finally {
    if (request.isCurrent()) lotsLoading.value = false
  }
}
async function choose(item) { itemFilter.value = String(item.itemId); lotPage.value = 0; await loadLots() }
function openReceipt() { receipt.value = { itemId: '', lotCode: '', quantity: 1, requestKey: requestKey('REC') }; drawer.value = true }
async function submitReceipt() {
  const quantity = Number(receipt.value.quantity)
  if (!Number.isSafeInteger(quantity) || quantity < 1 || !receipt.value.itemId) { ui.notify('원자재와 올바른 양의 정수 수량을 선택하세요.', 'error'); return }
  saving.value = true
  try {
    const created = await post('/api/v1/stock-receipts', { ...receipt.value, itemId: Number(receipt.value.itemId), quantity })
    drawer.value = false; ui.notify(`LOT ${created.lotCode} 입고를 처리했습니다.`)
    page.value = 0; await load()
    itemFilter.value = String(created.itemId); lotPage.value = 0; await loadLots()
  } catch (e) { ui.notify(e.message, 'error') }
  finally { saving.value = false }
}
function changePage(next) { page.value = next; load() }
function changeLotPage(next) { lotPage.value = next; loadLots() }
onMounted(load)
</script>
<template>
  <div class="toolbar-card"><div class="toolbar-left"><span class="toolbar-info"><Warehouse :size="17" /> 품목별 {{ isDemoMode ? '체험용' : '실제' }} 현재고와 예약고</span><button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16"/> 새로고침</button></div><button v-if="auth.canWrite" type="button" class="btn btn-primary" @click="openReceipt"><Plus :size="17" /> 원자재 입고</button></div>
  <section class="surface-card"><div class="card-header compact"><div><h2>품목별 재고 현황 <small class="header-count">{{ number(list.totalElements) }}종</small></h2><p>가용재고 = 현재고 − 예약고 · 현재 페이지에서 가용 0: {{ number(unavailableOnPage) }}종</p></div></div><AsyncState :loading="loading" :error="error" :empty="!list.content.length" label="먼저 품목을 등록하고 원자재를 입고해 주세요." @retry="load"><div class="table-scroll"><table class="data-table"><thead><tr><th>품목</th><th>유형</th><th class="right">현재고</th><th class="right">예약고</th><th class="right">가용재고</th><th>LOT</th></tr></thead><tbody><tr v-for="item in list.content" :key="item.itemId" :class="{ selected: String(item.itemId) === itemFilter }"><td><button type="button" class="row-action" @click="choose(item)">{{ item.name }}</button><small class="cell-sub">{{ item.sku }}</small></td><td><StatusBadge :status="item.itemType" /></td><td class="number-cell">{{ number(item.onHand) }}</td><td class="number-cell">{{ number(item.reserved) }}</td><td class="number-cell" :class="{ 'number-alert': item.available === 0 }">{{ number(item.available) }}</td><td><button type="button" class="lot-open" :aria-label="`${item.name} LOT 보기`" @click="choose(item)">LOT 보기 →</button></td></tr></tbody></table></div><PageControls :page="page" :total-pages="list.totalPages" :total-elements="list.totalElements" @change="changePage" /></AsyncState></section>
  <section class="surface-card lower-panel"><div class="card-header compact"><div><h2>{{ selectedItem ? `${selectedItem.name} · LOT 상세` : itemFilter ? `품목 #${itemFilter} · LOT 상세` : 'LOT 상세' }}</h2><p>입고 순서, 예약 수량 및 가용 재고를 확인합니다.</p></div></div><div v-if="!itemFilter" class="detail-placeholder compact-placeholder"><Layers :size="30"/><p>위의 품목을 선택하면 LOT별 재고가 표시됩니다.</p></div><AsyncState v-else :loading="lotsLoading" :error="lotsError" :empty="!lots.content.length" label="해당 품목에 입고된 LOT이 없습니다." @retry="loadLots"><div class="table-scroll"><table class="data-table"><thead><tr><th>LOT 코드</th><th>출처</th><th>입고일</th><th class="right">누적 입고</th><th class="right">현재고</th><th class="right">예약</th><th class="right">가용</th></tr></thead><tbody><tr v-for="lot in lots.content" :key="lot.id"><td><strong>{{ lot.lotCode }}</strong><small class="cell-sub">#{{ lot.id }}</small></td><td><StatusBadge :status="lot.sourceType" /></td><td>{{ dateTime(lot.receivedAt) }}</td><td class="number-cell">{{ number(lot.cumulativeInQty) }}</td><td class="number-cell">{{ number(lot.onHand) }}</td><td class="number-cell">{{ number(lot.reserved) }}</td><td class="number-cell">{{ number(lot.available) }}</td></tr></tbody></table></div><PageControls :page="lotPage" :total-pages="lots.totalPages" :total-elements="lots.totalElements" @change="changeLotPage" /></AsyncState></section>
  <div v-if="drawer" class="dialog-backdrop" @click.self="drawer = false"><section class="dialog" role="dialog" aria-modal="true" aria-label="원자재 입고"><header class="dialog-header"><div><h2>원자재 입고</h2></div><button type="button" class="icon-btn" aria-label="닫기" @click="drawer = false"><X :size="20"/></button></header><form @submit.prevent="submitReceipt"><div class="dialog-body"><div class="field-label"><span class="field-title">원자재 <em class="required-mark">*</em></span><ItemSearchSelect v-model="receipt.itemId" item-type="RAW_MATERIAL" placeholder="원자재 검색 · 선택" :disabled="saving" /></div><label class="field-label">LOT 코드 <span>*</span><input v-model.trim="receipt.lotCode" maxlength="80" required placeholder="LOT-20261111-001" /></label><label class="field-label">입고 수량 (EA) <span>*</span><input v-model.number="receipt.quantity" type="number" min="1" step="1" required /></label><div class="info-hint">동일 LOT 코드로 추가 입고할 수 있습니다. 요청을 다시 전송할 때는 중복 입고 방지를 위해 같은 요청 키를 유지합니다.</div></div><footer class="dialog-footer"><button type="button" class="btn btn-outline" @click="drawer = false">취소</button><button type="submit" class="btn btn-primary"  :disabled="saving || !receipt.itemId">{{ saving ? '처리 중...' : '입고 확정' }}</button></footer></form></section></div>
</template>
