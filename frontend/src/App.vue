<script setup>
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { LayoutDashboard, Boxes, Warehouse, ClipboardList, Truck, ArrowLeftRight, Factory, PanelLeftClose, PanelLeftOpen, RefreshCw, X, ChevronRight, ShieldAlert } from 'lucide-vue-next'
import { useUiStore } from './stores/ui.js'
import { useAuthStore } from './stores/auth.js'
import { Users, LogOut, RotateCcw } from 'lucide-vue-next'
import { isDemoMode } from './lib/api.js'
import { resetDemoData } from './lib/demo/store.js'

const route = useRoute(), router = useRouter()
const ui = useUiStore()
const auth = useAuthStore()
const sidebarOpen = ref(true)
const reloadKey = ref(0)
const navGroups = [
  { title: '운영', entries: [{ path: '/', title: '운영 개요', icon: LayoutDashboard }] },
  { title: '기준 · 재고', entries: [{ path: '/items', title: '품목 · BOM', icon: Boxes }, { path: '/inventory', title: '재고 · 입고', icon: Warehouse }] },
  { title: '생산 · 출고', entries: [{ path: '/orders', title: '생산지시 · 품질', icon: ClipboardList }, { path: '/shipments', title: '완제품 출고', icon: Truck }] },
  { title: '추적', entries: [{ path: '/movements', title: '재고 이동 이력', icon: ArrowLeftRight }] },
  { title: '관리', entries: [{ path: '/users', title: '계정 관리', icon: Users, adminOnly: true }] },
]
const title = computed(() => route.meta.title ?? '운영 개요')
const subtitle = computed(() => route.meta.subtitle ?? '')
function refresh() { reloadKey.value += 1 }
async function resetDemo() {
  if (!window.confirm('이 브라우저의 체험용 품목·LOT·생산지시·출고 기록을 샘플 초기 상태로 되돌릴까요?')) return
  try {
    await resetDemoData()
    reloadKey.value += 1
    ui.notify('이 브라우저의 데모 데이터를 초기화했습니다.')
  } catch (error) {
    ui.notify(error.message || '데모 데이터를 초기화하지 못했습니다.', 'error')
  }
}
async function signOut() {
  try { await auth.logout() } catch { ui.notify('로그아웃 과정에서 오류가 발생했습니다.', 'error') }
  await router.replace('/login')
}
</script>

<template>
  <div v-if="route.name === 'login'" class="login-page"><RouterView /></div>
  <div v-else :class="['app-shell', { 'sidebar-collapsed': !sidebarOpen }]">
    <aside class="sidebar" aria-label="주 내비게이션">
      <RouterLink to="/" class="brand">
        <span class="brand-symbol"><Factory :size="23" :stroke-width="1.9" /></span>
        <span class="brand-copy"><strong>FactoryFlow</strong><small>OPERATIONS CONSOLE</small></span>
      </RouterLink>
      <div class="workspace-mark"><span class="workspace-avatar">F</span><span class="workspace-info"><strong>제조 운영 시스템</strong><small>단일 공장 · 단일 창고</small></span><ChevronRight :size="14" class="muted" /></div>
      <nav class="side-navigation">
        <div v-for="group in navGroups" :key="group.title" class="nav-group">
          <span class="nav-caption">{{ group.title }}</span>
          <RouterLink v-for="entry in group.entries.filter(x => !x.adminOnly || (!isDemoMode && auth.user?.role === 'ADMIN'))" :key="entry.path" :to="entry.path" class="nav-link" :title="entry.title" :class="{ active: route.path === entry.path }">
            <component :is="entry.icon" :size="18" :stroke-width="1.9" /><span>{{ entry.title }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="sidebar-bottom"><div class="workspace-environment"><span class="env-indicator"></span><span>{{ isDemoMode ? 'BROWSER DEMO' : 'DEVELOPMENT' }}</span></div><p>{{ isDemoMode ? '데이터는 이 브라우저에만 저장됩니다. 실제 서버·MySQL은 사용하지 않습니다.' : '데이터는 실제 백엔드 API에서 조회합니다.' }}</p></div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left"><button type="button" class="icon-btn" :title="sidebarOpen ? '메뉴 접기' : '메뉴 열기'" @click="sidebarOpen = !sidebarOpen"><PanelLeftClose v-if="sidebarOpen" :size="19"/><PanelLeftOpen v-else :size="19" /></button><span class="breadcrumb-home">FactoryFlow</span><ChevronRight :size="14" class="breadcrumb-arrow" /><span class="breadcrumb-current">{{ title }}</span></div>
        <div class="topbar-right"><span class="preview-tag"><ShieldAlert :size="14" /> {{ isDemoMode ? '체험 사용자 · 서버 인증 없음' : (auth.user?.role === 'ADMIN' ? '관리자' : '조회 전용') + ' · ' + auth.user?.username }}</span><button type="button" class="icon-btn" title="화면 새로고침" @click="refresh"><RefreshCw :size="18" /></button><button v-if="!isDemoMode" type="button" class="icon-btn" title="로그아웃" aria-label="로그아웃" @click="signOut"><LogOut :size="18" /></button><span class="profile-avatar">{{ auth.user?.username?.slice(0, 2)?.toUpperCase() || 'FF' }}</span></div>
      </header>
      <main class="page-content">
        <div v-if="isDemoMode" class="demo-banner" role="note">
          <div><strong>브라우저 체험 모드</strong><span>샘플 데이터는 이 브라우저의 IndexedDB에만 저장됩니다. 실제 서버 인증·MySQL 트랜잭션·다중 사용자 동기화는 수행하지 않습니다.</span><small class="demo-hint">체험 순서: 품목·BOM 확인 → 재고·입고 → 생산지시 WO-DEMO-READY → 자재 예약 → 생산 시작 → 품질검사 → 출고</small></div>
          <button type="button" class="btn btn-outline" @click="resetDemo"><RotateCcw :size="15" /> 샘플 초기화</button>
        </div>
        <div class="page-heading"><div><div class="eyebrow">FACTORY OPERATIONS</div><h1>{{ title }}</h1><p>{{ subtitle }}</p></div><div class="heading-aside"><span class="outline-pill"><span class="pulse-dot"></span>{{ isDemoMode ? '로컬 시뮬레이션 데이터' : '실제 API 데이터' }}</span></div></div>
        <RouterView :key="`${route.fullPath}-${reloadKey}`" />
      </main>
      <footer class="app-footer">FactoryFlow <span>·</span> Manufacturing operations, made clear. <span class="footer-side">© 2026 · Portfolio environment</span></footer>
    </div>
    <div v-if="ui.notice" :class="['toast', `toast-${ui.notice.tone}`]" role="status"><span>{{ ui.notice.message }}</span><button type="button" aria-label="알림 닫기" @click="ui.dismiss()"><X :size="16" /></button></div>
  </div>
</template>
