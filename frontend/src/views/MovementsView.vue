<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeftRight, ArrowUpRight, RefreshCw } from 'lucide-vue-next'
import { get, isDemoMode } from '../lib/api.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { number, dateTime } from '../lib/format.js'
import AsyncState from '../components/AsyncState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'
import './movements-workbench.css'

const route = useRoute(), router = useRouter()
const movementGate = createLatestRequest(), itemGate = createLatestRequest()
const selectedId = ref(''), selectedItem = ref(null), page = ref(0)
const data = ref({ content: [], totalPages: 0, totalElements: 0 })
const loading = ref(false), error = ref(''), itemError = ref('')

const positiveId = value => {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return ''
  const id = Number(value)
  return Number.isSafeInteger(id) ? String(id) : ''
}
const pageNumber = value => {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return 0
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : 0
}
const selectedName = computed(() =>
  selectedItem.value?.name ?? (selectedId.value ? '품목 #' + selectedId.value : '품목 미선택'))
const inventoryLink = computed(() => ({ path: '/inventory', query: { itemId: selectedId.value } }))

const addedTypes = new Set(['RAW_RECEIPT', 'FINISHED_RECEIPT'])
const removedTypes = new Set(['RAW_CONSUMPTION', 'FINISHED_SHIPMENT'])
function quantityDelta(row) {
  const quantity = number(row.quantity)
  if (addedTypes.has(row.movementType)) return '+' + quantity
  if (removedTypes.has(row.movementType)) return '−' + quantity
  return quantity
}
function referenceLabel(type) {
  return {
    RECEIPT: '원자재 입고', WORK_ORDER: '생산지시',
    INSPECTION: '품질검사', SHIPMENT: '완제품 출고',
  }[type] ?? type ?? '—'
}
function referenceRoute(row) {
  const id = Number(row.referenceId)
  if (!Number.isSafeInteger(id) || id < 1) return null
  if (row.referenceType === 'WORK_ORDER') return { path: '/orders', query: { id: String(id) } }
  if (row.referenceType === 'SHIPMENT') return { path: '/shipments', query: { id: String(id) } }
  return null
}

async function loadItem() {
  const request = itemGate.begin()
  itemError.value = ''
  if (!selectedId.value) return
  try {
    const item = await get('/api/v1/items/' + selectedId.value, {}, { signal: request.signal })
    if (request.isCurrent()) selectedItem.value = item
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) itemError.value = e.message
  }
}

async function load() {
  const request = movementGate.begin()
  data.value = { content: [], totalPages: 0, totalElements: 0 }
  error.value = ''
  if (!selectedId.value) {
    loading.value = false
    return
  }
  loading.value = true
  try {
    const result = await get('/api/v1/stock-movements',
      { itemId: selectedId.value, page: page.value, size: 20 }, { signal: request.signal })
    if (request.isCurrent()) data.value = result
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}

function chooseItem(item) {
  if (item) selectedItem.value = item
  router.replace({
    path: '/movements',
    query: item ? { itemId: String(item.id) } : {},
  })
}
function changePage(next) {
  if (next < 0 || next >= data.value.totalPages) return
  router.replace({ path: '/movements',
    query: { itemId: selectedId.value, ...(next ? { page: String(next) } : {}) } })
}
function refresh() {
  loadItem()
  load()
}

// Keep URL, selected label and result page in sync without trusting invalid IDs.
watch(() => [route.query.itemId, route.query.page], ([idQuery, pageQuery]) => {
  const nextId = positiveId(idQuery)
  const changed = nextId !== selectedId.value
  selectedId.value = nextId
  page.value = pageNumber(pageQuery)
  if (changed) {
    itemGate.cancel()
    itemError.value = ''
    if (!selectedItem.value || String(selectedItem.value.id) !== nextId) {
      selectedItem.value = null
      if (nextId) loadItem()
    }
  }
  load()
}, { immediate: true })

onBeforeUnmount(() => { movementGate.cancel(); itemGate.cancel() })
</script>

<template>
  <div class="erp-movements">
    <div class="erp-movement-toolbar">
      <div class="erp-movement-filter">
        <span class="erp-movement-filter-label">조회 품목</span>
        <ItemSearchSelect :model-value="selectedId" :preselected-item="selectedItem"
          placeholder="품목 코드 또는 이름으로 검색" @selected="chooseItem" />
      </div>
      <div class="erp-movement-actions">
        <span class="erp-movement-mode">{{ isDemoMode ? '브라우저 체험 이력' : '실제 재고 이동 이력' }}</span>
        <button type="button" class="btn btn-subtle" :disabled="loading" @click="refresh">
          <RefreshCw :size="16" /> 이력 새로고침
        </button>
      </div>
    </div>

    <section class="erp-movement-sheet" aria-labelledby="movement-heading">
      <header class="erp-movement-head">
        <div>
          <h2 id="movement-heading">재고 이동 이력 <small v-if="selectedId">{{ number(data.totalElements) }}건</small></h2>
          <p>{{ selectedId ? selectedName + ' · LOT 단위 입출고/소모 기록' : '조회할 원자재 또는 완제품을 먼저 선택하세요.' }}</p>
        </div>
        <RouterLink v-if="selectedId" class="erp-movement-link" :to="inventoryLink">
          현재 LOT 재고 보기 <ArrowUpRight :size="15" aria-hidden="true" />
        </RouterLink>
      </header>
      <div v-if="itemError && selectedId" class="erp-movement-notice" role="status">
        품목 정보 조회에 실패했습니다. 이력은 품목 ID 기준으로 조회합니다. {{ itemError }}
      </div>
      <div v-if="!selectedId" class="erp-movement-empty">
        <ArrowLeftRight :size="25" :stroke-width="1.6" aria-hidden="true" />
        <p>위 검색에서 품목을 선택하면 원자재 입고·소모와 완제품 입고·출고의 기록을 확인할 수 있습니다.</p>
      </div>
      <AsyncState v-else :loading="loading" :error="error" :empty="!data.content.length"
        label="선택한 품목의 재고 이동 기록이 없습니다." @retry="load">
        <div class="table-scroll">
          <table class="data-table erp-movement-table" aria-label="선택 품목 재고 이동 이력">
            <thead><tr>
              <th scope="col">이력 ID</th>
              <th scope="col">처리일</th>
              <th scope="col">이동 유형</th>
              <th scope="col">LOT ID</th>
              <th scope="col" class="right">재고 변동 (EA)</th>
              <th scope="col">발생 업무 / 참조 ID</th>
              <th scope="col">업무 확인</th>
            </tr></thead>
            <tbody>
              <tr v-for="row in data.content" :key="row.id">
                <td class="erp-movement-id">#{{ row.id }}</td>
                <td>{{ dateTime(row.createdAt) }}</td>
                <td><StatusBadge :status="row.movementType" /></td>
                <td class="erp-movement-id">#{{ row.inventoryLotId }}</td>
                <td class="number-cell" :class="{ 'erp-movement-in': addedTypes.has(row.movementType), 'erp-movement-out': removedTypes.has(row.movementType) }">{{ quantityDelta(row) }}</td>
                <td><strong>{{ referenceLabel(row.referenceType) }}</strong><small class="cell-sub">#{{ row.referenceId }}</small></td>
                <td>
                  <RouterLink v-if="referenceRoute(row)" class="erp-movement-link"
                    :to="referenceRoute(row)" :aria-label="referenceLabel(row.referenceType) + ' #' + row.referenceId + ' 확인'">
                    상세 보기 <ArrowUpRight :size="14" aria-hidden="true" />
                  </RouterLink>
                  <span v-else class="erp-movement-unlinked">참조 ID 표시</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls :page="page" :total-pages="data.totalPages"
          :total-elements="data.totalElements" @change="changePage" />
      </AsyncState>
      <footer class="erp-movement-foot">
        재고 변동은 물리적 입고·소모·출고 사건만 포함합니다. 예약/해제는 포함하지 않습니다.
        LOT ID는 이동 당시 기록이며, 현재 LOT 잔량과 동일한 뜻이 아닙니다.
      </footer>
    </section>
  </div>
</template>
