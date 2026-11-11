<script setup>
import { onMounted, ref } from 'vue'
import { Truck, Plus, RefreshCw, X, PackageCheck } from 'lucide-vue-next'
import { get, post } from '../lib/api.js'
import { number, dateTime, requestKey } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'

const ui = useUiStore()
const page = ref(0), list = ref({ content: [], totalPages: 0, totalElements: 0 })
const loading = ref(false), error = ref(''), drawer = ref(false), saving = ref(false)
const finished = ref([]), selected = ref(null), detailLoading = ref(false), detailError = ref('')
const shipment = ref({ requestKey: requestKey('SHIP'), finishedItemId: '', quantity: 1 })
async function load() {
  loading.value = true; error.value = ''
  try {
    const [rows, products] = await Promise.all([get('/api/v1/shipments', { page: page.value, size: 15 }), get('/api/v1/items', { itemType: 'FINISHED_GOOD', page: 0, size: 100 })])
    list.value = rows; finished.value = products.content
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
async function choose(id) {
  detailLoading.value = true; detailError.value = ''
  try { selected.value = await get(`/api/v1/shipments/${id}`) }
  catch (e) { selected.value = null; detailError.value = e.message }
  finally { detailLoading.value = false }
}
function openCreate() { shipment.value = { requestKey: requestKey('SHIP'), finishedItemId: '', quantity: 1 }; drawer.value = true }
async function submit() {
  const quantity = Number(shipment.value.quantity)
  if (!Number.isSafeInteger(quantity) || quantity < 1 || !shipment.value.finishedItemId) { ui.notify('완제품과 올바른 출고 수량을 입력하세요.', 'error'); return }
  saving.value = true
  try {
    const created = await post('/api/v1/shipments', { ...shipment.value, finishedItemId: Number(shipment.value.finishedItemId), quantity })
    drawer.value = false; ui.notify('완제품 출고를 확정했습니다.')
    page.value = 0; await load(); await choose(created.id)
  } catch (e) { ui.notify(e.message, 'error') }
  finally { saving.value = false }
}
function changePage(p) { page.value = p; load() }
onMounted(load)
</script>
<template>
  <div class="toolbar-card"><div class="toolbar-left"><span class="toolbar-info"><Truck :size="17" /> 합격품 LOT에 대한 출고 이력</span><button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16"/> 새로고침</button></div><button type="button" class="btn btn-primary" @click="openCreate"><Plus :size="17" /> 출고 등록</button></div>
  <div class="section-grid order-workspace"><section class="surface-card"><div class="card-header compact"><div><span class="section-kicker">OUTBOUND SHIPMENTS</span><h2>완제품 출고 내역</h2><p>품질검사 합격품에서 차감된 출고 기록</p></div></div><AsyncState :loading="loading" :error="error" :empty="!list.content.length" label="품질검사를 완료한 후 출고를 등록하세요." @retry="load"><div class="table-scroll"><table class="data-table"><thead><tr><th>출고 요청</th><th>완제품</th><th class="right">출고 수량</th><th>처리일</th></tr></thead><tbody><tr v-for="row in list.content" :key="row.id" :class="{ selected: selected?.id === row.id }"><td><button type="button" class="row-action" @click="choose(row.id)">{{ row.requestKey }}</button><small class="cell-sub">출고 #{{ row.id }}</small></td><td>{{ finished.find(i => i.id === row.finishedItemId)?.name ?? `#${row.finishedItemId}` }}</td><td class="number-cell">{{ number(row.quantity) }}</td><td>{{ dateTime(row.createdAt) }}</td></tr></tbody></table></div><PageControls :page="page" :total-pages="list.totalPages" :total-elements="list.totalElements" @change="changePage" /></AsyncState></section>
    <section class="surface-card detail-panel"><div class="card-header compact"><div><span class="section-kicker">SHIPMENT TRACE</span><h2>출고 상세 · LOT 배분</h2><p>어떤 LOT에서 출고되었는지 확인</p></div></div><AsyncState :loading="detailLoading" :error="detailError" @retry="selected && choose(selected.id)"><div v-if="!selected" class="detail-placeholder"><PackageCheck :size="36" :stroke-width="1.5"/><h3>출고를 선택해 주세요</h3><p>출고 수량과 LOT별 배분 내역을 확인할 수 있습니다.</p></div><div v-else class="detail-body"><div class="detail-title"><span class="detail-avatar"><Truck :size="23" /></span><div><strong>출고 #{{ selected.id }}</strong><span>{{ selected.requestKey }}</span></div></div><dl class="key-values"><div><dt>완제품</dt><dd>{{ finished.find(i => i.id === selected.finishedItemId)?.name ?? `#${selected.finishedItemId}` }}</dd></div><div><dt>출고 수량</dt><dd>{{ number(selected.quantity) }} EA</dd></div><div><dt>처리일</dt><dd>{{ dateTime(selected.createdAt) }}</dd></div></dl><div class="subsection-heading"><div><span class="section-kicker">LOT ALLOCATION</span><h3>LOT 배분 기록</h3></div></div><div class="material-list"><div v-for="lot in selected.allocations" :key="lot.lotId" class="material-row"><span>LOT <strong>#{{ lot.lotId }}</strong></span><strong>{{ number(lot.quantity) }} EA</strong></div></div><div class="info-hint">출고 시 FIFO로 배분된 LOT 수량을 기록합니다. 출고 취소·반품은 현재 MVP 범위에 포함되지 않습니다.</div></div></AsyncState></section></div>
  <div v-if="drawer" class="dialog-backdrop" @click.self="drawer = false"><section class="dialog" role="dialog" aria-modal="true" aria-label="완제품 출고 등록"><header class="dialog-header"><div><span class="section-kicker">NEW SHIPMENT</span><h2>완제품 출고</h2></div><button type="button" class="icon-btn" aria-label="닫기" @click="drawer = false"><X :size="20" /></button></header><form @submit.prevent="submit"><div class="dialog-body"><label class="field-label">완제품 <span>*</span><ItemSearchSelect v-model="shipment.finishedItemId" item-type="FINISHED_GOOD" placeholder="출고할 완제품 검색 · 선택" :disabled="saving" /></label><label class="field-label">출고 수량 (EA) <span>*</span><input v-model.number="shipment.quantity" type="number" min="1" step="1" required /></label><div class="info-hint">실제 검사를 통과한 완제품 재고만 출고됩니다. 필요한 전체 수량을 확보하지 못하면 출고가 취소되며 일부만 출고되지 않습니다.</div></div><footer class="dialog-footer"><button type="button" class="btn btn-outline" @click="drawer = false">취소</button><button type="submit" class="btn btn-primary"  :disabled="saving || !shipment.finishedItemId">{{ saving ? '출고 중...' : '출고 확정' }}</button></footer></form></section></div>
</template>
