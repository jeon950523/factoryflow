import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'

export const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { title: '관리자 로그인' } },
  { path: '/users', name: 'users', component: () => import('../views/UsersView.vue'), meta: { title: '계정 관리', adminOnly: true } },
  { path: '/', name: 'dashboard', component: () => import('../views/DashboardView.vue'), meta: { title: '운영 개요', subtitle: '제조 운영 현황을 한눈에 확인합니다.' } },
  { path: '/items', name: 'items', component: () => import('../views/ItemsView.vue'), meta: { title: '품목 · BOM', subtitle: '원자재 및 완제품과 소요 자재 기준을 관리합니다.' } },
  { path: '/inventory', name: 'inventory', component: () => import('../views/InventoryView.vue'), meta: { title: '재고 · 입고', subtitle: 'LOT 재고, 예약량과 입고 기록을 확인합니다.' } },
  { path: '/orders', name: 'orders', component: () => import('../views/OrdersView.vue'), meta: { title: '생산지시', subtitle: '예약·생산·품질검사를 업무 상태에 따라 처리합니다.' } },
  { path: '/shipments', name: 'shipments', component: () => import('../views/ShipmentsView.vue'), meta: { title: '완제품 출고', subtitle: '품질검사 합격품만 FIFO로 출고합니다.' } },
  { path: '/movements', name: 'movements', component: () => import('../views/MovementsView.vue'), meta: { title: '재고 이동 이력', subtitle: '입고·소모·완제품 반영·출고의 변경 이력을 추적합니다.' } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

export const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (!auth.ready) await auth.bootstrap()
  if (to.name === 'login') return auth.user ? '/' : true
  if (!auth.user) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.adminOnly && auth.user.role !== 'ADMIN') return '/'
  return true
})
