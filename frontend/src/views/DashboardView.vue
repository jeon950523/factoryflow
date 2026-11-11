<script setup>
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Boxes, ClipboardList, Factory, CircleCheck, ArrowUpRight, ArrowRight, Warehouse, Truck, RefreshCw } from 'lucide-vue-next'
import { get } from '../lib/api.js'
import { number, dateTime } from '../lib/format.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'

const loading = ref(true)
const error = ref('')
const totals = ref({ items: 0, draft: 0, reserved: 0, active: 0, qc: 0, completed: 0 })
const orders = ref([])
const inventory = ref([])
const workStatuses = ['DRAFT', 'RESERVED', 'IN_PROGRESS', 'AWAITING_QC', 'COMPLETED']

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const [items, recent, stock, ...counts] = await Promise.all([
      get('/api/v1/items', { page: 0, size: 1 }),
      get('/api/v1/work-orders', { page: 0, size: 6 }),
      get('/api/v1/inventory', { page: 0, size: 8 }),
      ...workStatuses.map(status => get('/api/v1/work-orders', { status, page: 0, size: 1 })),
    ])
    totals.value = { items: items.totalElements, draft: counts[0].totalElements, reserved: counts[1].totalElements, active: counts[2].totalElements, qc: counts[3].totalElements, completed: counts[4].totalElements }
    orders.value = recent.content
    inventory.value = stock.content
  } catch (e) { error.value = e.message }
  finally { loading.value = false }
}
onMounted(refresh)
</script>

<template>
  <AsyncState :loading="loading" :error="error" @retry="refresh">
    <div class="metrics-grid">
      <div class="metric-card"><div class="metric-head"><span>등록 품목</span><span class="metric-icon teal"><Boxes :size="18" /></span></div><div class="metric-value">{{ number(totals.items) }}<span class="unit">종</span></div><p>원자재 및 완제품 전체</p></div>
      <div class="metric-card"><div class="metric-head"><span>생산 중</span><span class="metric-icon blue"><Factory :size="18" /></span></div><div class="metric-value">{{ number(totals.active) }}<span class="unit">건</span></div><p>현재 작업 진행 중</p></div>
      <div class="metric-card"><div class="metric-head"><span>검사 대기</span><span class="metric-icon amber"><ClipboardList :size="18" /></span></div><div class="metric-value">{{ number(totals.qc) }}<span class="unit">건</span></div><p>품질검사 확정 필요</p></div>
      <div class="metric-card"><div class="metric-head"><span>생산 완료</span><span class="metric-icon green"><CircleCheck :size="18" /></span></div><div class="metric-value">{{ number(totals.completed) }}<span class="unit">건</span></div><p>품질검사까지 확정된 지시</p></div>
    </div>
    <div class="section-grid dashboard-columns">
      <section class="surface-card"><div class="card-header"><div><span class="section-kicker">PRODUCTION OVERVIEW</span><h2>생산지시 진행 현황</h2><p>상태별 실제 등록 건수</p></div><RouterLink class="text-link" to="/orders">전체 보기 <ArrowUpRight :size="15" /></RouterLink></div>
        <div class="progress-list"><div v-for="row in [{ key: 'DRAFT', name: '생산 대기', count: totals.draft, color: '#9aa5b1' }, { key: 'RESERVED', name: '자재 예약', count: totals.reserved, color: '#3e9bd3' }, { key: 'IN_PROGRESS', name: '생산 진행', count: totals.active, color: '#1f9587' }, { key: 'AWAITING_QC', name: '품질검사 대기', count: totals.qc, color: '#e3ab5b' }, { key: 'COMPLETED', name: '생산 완료', count: totals.completed, color: '#4daa83' }]" :key="row.key" class="progress-row"><span class="progress-name"><i :style="{ background: row.color }"></i>{{ row.name }}</span><div class="progress-track"><div :style="{ background: row.color, width: `${Math.max(row.count ? 5 : 0, (row.count / Math.max(1, totals.draft + totals.reserved + totals.active + totals.qc + totals.completed)) * 100)}%` }"></div></div><strong>{{ number(row.count) }}</strong></div></div>
        <div class="card-note">상태별 수치는 전체 생산지시 데이터를 기준으로 조회합니다.</div>
      </section>
      <section class="surface-card quick-panel"><div class="card-header"><div><span class="section-kicker">QUICK ACTIONS</span><h2>빠른 작업</h2><p>주요 운영 업무로 이동</p></div></div>
        <RouterLink class="quick-action" to="/items"><span class="quick-icon"><Boxes :size="19" /></span><span><strong>품목 및 BOM 관리</strong><small>원자재·완제품 등록과 구성 설정</small></span><ArrowRight :size="18" /></RouterLink>
        <RouterLink class="quick-action" to="/inventory"><span class="quick-icon"><Warehouse :size="19" /></span><span><strong>원자재 입고 처리</strong><small>LOT 수량 입고 및 가용재고 확인</small></span><ArrowRight :size="18" /></RouterLink>
        <RouterLink class="quick-action" to="/orders"><span class="quick-icon"><ClipboardList :size="19" /></span><span><strong>생산지시 및 품질검사</strong><small>상태에 맞는 작업 진행</small></span><ArrowRight :size="18" /></RouterLink>
        <RouterLink class="quick-action" to="/shipments"><span class="quick-icon"><Truck :size="19" /></span><span><strong>완제품 출고</strong><small>합격품 FIFO 출고 확정</small></span><ArrowRight :size="18" /></RouterLink>
      </section>
    </div>
    <div class="section-grid dashboard-columns lower-grid">
      <section class="surface-card"><div class="card-header"><div><span class="section-kicker">RECENT ORDERS</span><h2>최근 생산지시</h2><p>최신 6건</p></div><RouterLink class="text-link" to="/orders">자세히 보기 <ArrowUpRight :size="15" /></RouterLink></div><div class="table-scroll"><table class="data-table"><thead><tr><th>생산지시</th><th>완제품 ID</th><th>계획 수량</th><th>상태</th></tr></thead><tbody><tr v-for="order in orders" :key="order.id"><td><RouterLink class="row-link" :to="{ path: '/orders', query: { id: order.id } }">{{ order.orderNo }}</RouterLink><small class="cell-sub">{{ dateTime(order.createdAt) }}</small></td><td>#{{ order.finishedItemId }}</td><td class="number-cell">{{ number(order.plannedQty) }}</td><td><StatusBadge :status="order.status" /></td></tr><tr v-if="!orders.length"><td colspan="4" class="table-empty">아직 생산지시가 없습니다.</td></tr></tbody></table></div></section>
      <section class="surface-card"><div class="card-header"><div><span class="section-kicker">STOCK SNAPSHOT</span><h2>품목 재고 요약</h2><p>첫 8개 품목 · 가용 수량</p></div><RouterLink class="text-link" to="/inventory">재고 전체 보기 <ArrowUpRight :size="15" /></RouterLink></div><div class="table-scroll"><table class="data-table"><thead><tr><th>품목</th><th>현재고</th><th>예약</th><th>가용</th></tr></thead><tbody><tr v-for="item in inventory" :key="item.itemId"><td><strong>{{ item.name }}</strong><small class="cell-sub">{{ item.sku }}</small></td><td class="number-cell">{{ number(item.onHand) }}</td><td class="number-cell">{{ number(item.reserved) }}</td><td class="number-cell" :class="{ 'number-alert': item.available === 0 }">{{ number(item.available) }}</td></tr><tr v-if="!inventory.length"><td colspan="4" class="table-empty">재고 정보가 없습니다.</td></tr></tbody></table></div></section>
    </div>
  </AsyncState>
</template>
