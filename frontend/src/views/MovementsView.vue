<script setup>
import { onMounted, ref } from 'vue'
import { ArrowLeftRight, RefreshCw } from 'lucide-vue-next'
import { get, isDemoMode } from '../lib/api.js'
import { number, dateTime } from '../lib/format.js'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PageControls from '../components/PageControls.vue'

const items = ref([]), itemId = ref(''), page = ref(0)
const data = ref({ content: [], totalPages: 0, totalElements: 0 })
const loading = ref(false), error = ref(''), itemsError = ref('')
async function loadItems() {
  try { items.value = (await get('/api/v1/items', { size: 100, page: 0 })).content }
  catch (e) { itemsError.value = e.message }
}
async function load() {
  data.value = { content: [], totalElements: 0, totalPages: 0 }
  if (!itemId.value) return
  loading.value = true; error.value = ''
  try { data.value = await get('/api/v1/stock-movements', { itemId: itemId.value, page: page.value, size: 20 }) }
  catch (e) { error.value = e.message }
  finally { loading.value = false }
}
function changeItem() { page.value = 0; load() }
function changePage(next) { page.value = next; load() }
onMounted(loadItems)
</script>
<template>
  <div class="toolbar-card"><div class="toolbar-left"><select v-model="itemId" class="select-filter long-filter" @change="changeItem"><option value="">이동 이력을 조회할 품목 선택</option><option v-for="item in items" :key="item.id" :value="item.id">{{ item.sku }} · {{ item.name }}</option></select><button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16" /> 새로고침</button></div><span class="toolbar-info">{{ isDemoMode ? '로컬 이동 시뮬레이션' : '실제 이동 이력' }} · 예약은 제외</span></div>
  <section class="surface-card"><div class="card-header compact"><div><span class="section-kicker">INVENTORY AUDIT TRAIL</span><h2>재고 이동 이력</h2><p>LOT 및 업무 요청 단위의 물리 재고 변경 기록</p></div></div><div v-if="itemsError" class="inline-error padded">{{ itemsError }}</div><div v-else-if="!itemId" class="detail-placeholder"><ArrowLeftRight :size="37" :stroke-width="1.5" /><h3>품목을 선택해 주세요</h3><p>원자재 입고·소모, 완제품 입고·출고 이력을 확인할 수 있습니다.</p></div><AsyncState v-else :loading="loading" :error="error" :empty="!data.content.length" label="선택한 품목의 이동 이력이 없습니다." @retry="load"><div class="table-scroll"><table class="data-table"><thead><tr><th>이동 유형</th><th>LOT ID</th><th class="right">수량</th><th>참조 유형</th><th>참조 ID</th><th>처리일</th></tr></thead><tbody><tr v-for="row in data.content" :key="row.id"><td><StatusBadge :status="row.movementType" /></td><td>#{{ row.inventoryLotId }}</td><td class="number-cell">{{ number(row.quantity) }}</td><td>{{ row.referenceType }}</td><td>#{{ row.referenceId }}</td><td>{{ dateTime(row.createdAt) }}</td></tr></tbody></table></div><PageControls :page="page" :total-pages="data.totalPages" :total-elements="data.totalElements" @change="changePage" /></AsyncState><div class="card-note">이동 이력에는 현재고를 실제로 바꾸는 사건만 기록됩니다. 재고 예약/해제는 물리 재고 이동이 아닙니다.</div></section>
</template>
