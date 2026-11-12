<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, RefreshCw } from 'lucide-vue-next'
import { get, isDemoMode } from '../lib/api.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { lookupItemNames, itemNameOrId } from '../lib/itemNames.js'
import { number, dateTime } from '../lib/format.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'
import './dashboard-workbench.css'

const loading = ref(true)
const error = ref('')
const nameWarning = ref('')
const inventory = ref([])
const itemNames = ref({})
const totals = ref({ draft: 0, reserved: 0, active: 0, qc: 0, completed: 0 })
const workPages = ref({ DRAFT: [], RESERVED: [], AWAITING_QC: [] })
const requestGate = createLatestRequest()
onBeforeUnmount(() => requestGate.cancel())

// Only statuses that lead to an immediate operator action are in this table.
// Per-status pages are explicitly limited to six records; count figures are
// server-side totalElements, not the length of the displayed sample.
const workStates = [
  { key: 'DRAFT', countKey: 'draft', label: '자재 예약', next: '자재 예약' },
  { key: 'RESERVED', countKey: 'reserved', label: '생산 시작', next: '생산 시작' },
  { key: 'AWAITING_QC', countKey: 'qc', label: '품질검사', next: '품질검사 확정' },
]
const statuses = [...workStates.map(state => state.key), 'IN_PROGRESS', 'COMPLETED']
const actionable = computed(() => workStates.flatMap(state =>
  (workPages.value[state.key] ?? []).map(order => ({ ...order, nextAction: state.next }))
))
const actionTotal = computed(() => totals.value.draft + totals.value.reserved + totals.value.qc)
const zeroAvailableShown = computed(() => inventory.value.filter(item => item.available === 0).length)

async function refresh() {
  const request = requestGate.begin()
  loading.value = true
  error.value = ''
  nameWarning.value = ''
  try {
    // The existing API supports status filtering + paging, not an overall
    // pending-work queue. Fetch up to six per actionable status, plus two
    // count-only status pages, then display the actual coverage explicitly.
    const [stock, ...pages] = await Promise.all([
      get('/api/v1/inventory', { page: 0, size: 8 }, { signal: request.signal }),
      ...statuses.map(status => get('/api/v1/work-orders', {
        status, page: 0, size: workStates.some(s => s.key === status) ? 6 : 1,
      }, { signal: request.signal })),
    ])
    if (!request.isCurrent()) return

    inventory.value = stock.content
    totals.value = {
      draft: pages[0].totalElements,
      reserved: pages[1].totalElements,
      qc: pages[2].totalElements,
      active: pages[3].totalElements,
      completed: pages[4].totalElements,
    }
    const actionPages = { DRAFT: pages[0].content, RESERVED: pages[1].content, AWAITING_QC: pages[2].content }
    workPages.value = actionPages

    try {
      const ids = Object.values(actionPages).flat().map(order => order.finishedItemId)
      const names = await lookupItemNames(ids, { signal: request.signal })
      if (!request.isCurrent()) return
      itemNames.value = names
    } catch (e) {
      if (!request.isCurrent() || isAbortError(e)) return
      itemNames.value = {}
      nameWarning.value = '일부 품목명을 조회할 수 없어 품목 ID로 표시합니다.'
    }
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="dashboard-workbench">
    <AsyncState :loading="loading" :error="error" @retry="refresh">
      <section class="dashboard-status" role="group" aria-label="생산지시 상태별 건수">
        <div class="dashboard-status-total">
          <span>처리 대기</span>
          <strong>{{ number(actionTotal) }}<small>건</small></strong>
        </div>
        <nav class="dashboard-status-links" aria-label="업무 상태별 생산지시 바로가기">
          <RouterLink v-for="state in workStates" :key="state.key"
            :to="{ path: '/orders', query: { status: state.key } }"
            :aria-label="`${state.label} ${totals[state.countKey]}건 보기`">
            {{ state.label }}
            <strong>{{ number(totals[state.countKey]) }}</strong>
          </RouterLink>
        </nav>
        <div class="dashboard-status-secondary">
          <span>생산 중 <strong>{{ number(totals.active) }}</strong></span>
          <span>완료 <strong>{{ number(totals.completed) }}</strong></span>
        </div>
      </section>

      <section class="dashboard-section" aria-labelledby="work-list-heading">
        <div class="dashboard-section-heading">
          <div>
            <h2 id="work-list-heading">처리할 생산지시</h2>
            <p>자재 예약·생산 시작·품질검사 대기 · 전체 {{ number(actionTotal) }}건 중 {{ number(actionable.length) }}건 표시 (상태별 최대 6건)</p>
          </div>
          <button type="button" class="btn btn-subtle" @click="refresh">
            <RefreshCw :size="15" /> 새로고침
          </button>
        </div>
        <p v-if="nameWarning" class="inline-error padded" role="status">{{ nameWarning }}</p>
        <div class="table-scroll">
          <table class="data-table" aria-label="처리할 생산지시 목록">
            <thead><tr>
              <th scope="col">생산지시 / 등록일</th>
              <th scope="col">완제품</th>
              <th scope="col" class="right">계획 수량</th>
              <th scope="col">진행 상태</th>
              <th scope="col">다음 작업</th>
              <th scope="col">업무 처리</th>
            </tr></thead>
            <tbody>
              <tr v-for="order in actionable" :key="order.id">
                <td>
                  <RouterLink class="row-link" :to="{ path: '/orders', query: { id: order.id } }">{{ order.orderNo }}</RouterLink>
                  <small class="cell-sub">{{ dateTime(order.createdAt) }}</small>
                </td>
                <td>{{ itemNameOrId(itemNames, order.finishedItemId, '완제품') }}</td>
                <td class="number-cell">{{ number(order.plannedQty) }} EA</td>
                <td><StatusBadge :status="order.status" /></td>
                <td>{{ order.nextAction }}</td>
                <td>
                  <RouterLink class="dashboard-open-order" :to="{ path: '/orders', query: { id: order.id } }"
                    :aria-label="`${order.orderNo} 상세 및 처리 화면 열기`">상세·처리 <ArrowRight :size="14" /></RouterLink>
                </td>
              </tr>
              <tr v-if="!actionable.length"><td colspan="6" class="table-empty">현재 예약·생산 시작·품질검사 대기 중인 생산지시가 없습니다.</td></tr>
            </tbody>
          </table>
        </div>
        <div class="dashboard-section-foot">
          <span>{{ isDemoMode ? '브라우저 체험 데이터 기준' : '서버의 현재 등록 데이터 기준' }}</span>
          <RouterLink to="/orders">전체 생산지시 보기 <ArrowRight :size="14" /></RouterLink>
        </div>
      </section>

      <section class="dashboard-section" aria-labelledby="stock-list-heading">
        <div class="dashboard-section-heading">
          <div>
            <h2 id="stock-list-heading">재고 현황</h2>
            <p>첫 {{ number(inventory.length) }}개 품목 기준 · 가용재고 = 현재고 − 예약고</p>
          </div>
          <RouterLink class="dashboard-more" to="/inventory">전체 재고·LOT 보기 <ArrowRight :size="14" /></RouterLink>
        </div>
        <div class="table-scroll">
          <table class="data-table" aria-label="조회된 품목의 재고 현황">
            <thead><tr>
              <th scope="col">품목 / SKU</th>
              <th scope="col">유형</th>
              <th scope="col" class="right">현재고</th>
              <th scope="col" class="right">예약고</th>
              <th scope="col" class="right">가용재고</th>
            </tr></thead>
            <tbody>
              <tr v-for="item in inventory" :key="item.itemId">
                <td><strong>{{ item.name }}</strong><small class="cell-sub">{{ item.sku }}</small></td>
                <td><StatusBadge :status="item.itemType" /></td>
                <td class="number-cell">{{ number(item.onHand) }}</td>
                <td class="number-cell">{{ number(item.reserved) }}</td>
                <td class="number-cell" :class="{ 'number-alert': item.available === 0 }">{{ number(item.available) }}</td>
              </tr>
              <tr v-if="!inventory.length"><td colspan="5" class="table-empty">조회된 재고 품목이 없습니다.</td></tr>
            </tbody>
          </table>
        </div>
        <div class="dashboard-section-foot">
          <span>표시 품목 중 가용재고 0: {{ number(zeroAvailableShown) }}종 · 창고 전체 경보 아님</span>
        </div>
      </section>
    </AsyncState>
  </div>
</template>
