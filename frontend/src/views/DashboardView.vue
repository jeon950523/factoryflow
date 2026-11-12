<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { ArrowRight, RefreshCw } from 'lucide-vue-next'
import { get, isDemoMode } from '../lib/api.js'
import { lookupItemNames, itemNameOrId } from '../lib/itemNames.js'
import { number, dateTime } from '../lib/format.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'

const loading = ref(true)
const error = ref('')
const totals = ref({ items: 0, draft: 0, reserved: 0, active: 0, qc: 0, completed: 0 })
const orders = ref([])
const inventory = ref([])
const itemNames = ref({})
const nameWarning = ref('')
const workStatuses = ['DRAFT', 'RESERVED', 'IN_PROGRESS', 'AWAITING_QC', 'COMPLETED']

// The API has no full-inventory alert endpoint. Counts below refer only to the
// displayed first 8 items; never report them as a warehouse-wide shortage alert.
const zeroAvailableShown = computed(() => inventory.value.filter(item => item.available === 0).length)
const actionTotal = computed(() => totals.value.draft + totals.value.reserved + totals.value.qc)
const queue = computed(() => [
  { status: 'DRAFT', label: '자재 예약 대기', detail: '필요 자재와 LOT 가용재고를 확인하고 예약', count: totals.value.draft, tone: 'neutral' },
  { status: 'RESERVED', label: '생산 시작 대기', detail: '예약된 원자재를 소모하고 생산 진행', count: totals.value.reserved, tone: 'blue' },
  { status: 'AWAITING_QC', label: '품질검사 대기', detail: '합격·불합격 수량 확정 후 완제품 입고', count: totals.value.qc, tone: 'amber' },
])

async function refresh() {
  loading.value = true
  error.value = ''
  nameWarning.value = ''
  try {
    const [items, recent, stock, ...counts] = await Promise.all([
      get('/api/v1/items', { page: 0, size: 1 }),
      get('/api/v1/work-orders', { page: 0, size: 6 }),
      get('/api/v1/inventory', { page: 0, size: 8 }),
      ...workStatuses.map(status => get('/api/v1/work-orders', { status, page: 0, size: 1 })),
    ])
    totals.value = {
      items: items.totalElements,
      draft: counts[0].totalElements,
      reserved: counts[1].totalElements,
      active: counts[2].totalElements,
      qc: counts[3].totalElements,
      completed: counts[4].totalElements,
    }
    orders.value = recent.content
    inventory.value = stock.content
    try {
      itemNames.value = await lookupItemNames(recent.content.map(order => order.finishedItemId))
    } catch {
      nameWarning.value = '일부 품목명을 조회하지 못해 품목 ID로 표시합니다.'
    }
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
onMounted(refresh)
</script>

<template>
  <AsyncState :loading="loading" :error="error" @retry="refresh">
    <div class="operations-summary" aria-label="제조 운영 요약">
      <div class="operation-stat operation-stat-primary">
        <span>처리 대기</span><strong>{{ number(actionTotal) }}<small>건</small></strong>
        <p>예약 대기 + 생산 시작 대기 + 검사 대기</p>
      </div>
      <div class="operation-stat">
        <span>생산 중</span><strong>{{ number(totals.active) }}<small>건</small></strong>
        <p>현재 생산 진행 중인 지시</p>
      </div>
      <div class="operation-stat">
        <span>생산 완료</span><strong>{{ number(totals.completed) }}<small>건</small></strong>
        <p>품질검사까지 완료한 지시</p>
      </div>
      <div class="operation-stat operation-stat-stock">
        <span>표시 품목 중 가용재고 0</span><strong>{{ number(zeroAvailableShown) }}<small>종</small></strong>
        <p>첫 {{ number(inventory.length) }}개 품목 기준 · 창고 전체 경보 아님</p>
      </div>
    </div>

    <div class="workbench-grid">
      <section class="surface-card work-queue">
        <div class="card-header compact">
          <div><h2>지금 처리할 업무</h2><p>작업할 상태를 선택하면 해당 생산지시 목록으로 이동합니다.</p></div>
          <button type="button" class="btn btn-subtle" @click="refresh"><RefreshCw :size="15"/> 새로고침</button>
        </div>
        <div class="queue-list">
          <RouterLink v-for="task in queue" :key="task.status"
            :to="{ path: '/orders', query: { status: task.status } }"
            :class="['queue-row', `queue-${task.tone}`]" :aria-label="`${task.label} ${task.count}건 보기`">
            <span class="queue-indicator" aria-hidden="true"></span>
            <span class="queue-copy"><strong>{{ task.label }}</strong><small>{{ task.detail }}</small></span>
            <span class="queue-count">{{ number(task.count) }}건</span>
            <ArrowRight :size="17" aria-hidden="true"/>
          </RouterLink>
        </div>
        <div class="card-note">{{ isDemoMode ? '현재 브라우저에 저장된 제조 데이터 기준입니다.' : '현재 등록된 전체 생산지시의 상태별 건수입니다.' }}</div>
      </section>

      <section class="surface-card recent-orders">
        <div class="card-header compact"><div><h2>최근 생산지시</h2><p>등록 순 최신 6건</p></div>
          <RouterLink to="/orders" class="text-link">전체 목록 <ArrowRight :size="15"/></RouterLink>
        </div>
        <p v-if="nameWarning" class="inline-error padded" role="status">{{ nameWarning }}</p>
        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>생산지시 / 등록일</th><th>완제품</th><th class="right">계획 수량</th><th>진행 상태</th></tr></thead>
            <tbody>
              <tr v-for="order in orders" :key="order.id">
                <td><RouterLink class="row-link" :to="{ path: '/orders', query: { id: order.id } }">{{ order.orderNo }}</RouterLink><small class="cell-sub">{{ dateTime(order.createdAt) }}</small></td>
                <td>{{ itemNameOrId(itemNames, order.finishedItemId, '완제품') }}</td>
                <td class="number-cell">{{ number(order.plannedQty) }} EA</td><td><StatusBadge :status="order.status"/></td>
              </tr>
              <tr v-if="!orders.length"><td class="table-empty" colspan="4">아직 등록된 생산지시가 없습니다.</td></tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>

    <section class="surface-card workbench-inventory">
      <div class="card-header compact">
        <div><h2>재고 현황</h2><p>첫 {{ number(inventory.length) }}개 품목 · 현재고 − 예약고 = 가용재고 · 전체 등록 {{ number(totals.items) }}종</p></div>
        <RouterLink class="text-link" to="/inventory">재고·LOT 상세 <ArrowRight :size="15"/></RouterLink>
      </div>
      <div class="table-scroll">
        <table class="data-table">
          <thead><tr><th>품목 / SKU</th><th>유형</th><th class="right">현재고</th><th class="right">예약고</th><th class="right">가용재고</th></tr></thead>
          <tbody>
            <tr v-for="item in inventory" :key="item.itemId">
              <td><strong>{{ item.name }}</strong><small class="cell-sub">{{ item.sku }}</small></td>
              <td><StatusBadge :status="item.itemType"/></td>
              <td class="number-cell">{{ number(item.onHand) }}</td>
              <td class="number-cell">{{ number(item.reserved) }}</td>
              <td class="number-cell" :class="{ 'number-alert': item.available === 0 }">{{ number(item.available) }}</td>
            </tr>
            <tr v-if="!inventory.length"><td colspan="5" class="table-empty">조회 가능한 재고 품목이 없습니다.</td></tr>
          </tbody>
        </table>
      </div>
      <div class="card-note">재고 부족 경보 기준은 아직 정의되지 않았습니다. 가용재고 0은 조회된 품목에 대해서만 표시합니다.</div>
    </section>
  </AsyncState>
</template>
