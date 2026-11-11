<script setup>
import { computed, ref } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { LayoutDashboard, Boxes, Warehouse, ClipboardList, Truck, ArrowLeftRight, Factory, PanelLeftClose, PanelLeftOpen, RefreshCw, X, ChevronRight, ShieldAlert } from 'lucide-vue-next'
import { useUiStore } from './stores/ui.js'

const route = useRoute()
const ui = useUiStore()
const sidebarOpen = ref(true)
const reloadKey = ref(0)
const navGroups = [
  { title: '운영', entries: [{ path: '/', title: '운영 개요', icon: LayoutDashboard }] },
  { title: '기준 · 재고', entries: [{ path: '/items', title: '품목 · BOM', icon: Boxes }, { path: '/inventory', title: '재고 · 입고', icon: Warehouse }] },
  { title: '생산 · 출고', entries: [{ path: '/orders', title: '생산지시 · 품질', icon: ClipboardList }, { path: '/shipments', title: '완제품 출고', icon: Truck }] },
  { title: '추적', entries: [{ path: '/movements', title: '재고 이동 이력', icon: ArrowLeftRight }] },
]
const title = computed(() => route.meta.title ?? '운영 개요')
const subtitle = computed(() => route.meta.subtitle ?? '')
function refresh() { reloadKey.value += 1 }
</script>

<template>
  <div :class="['app-shell', { 'sidebar-collapsed': !sidebarOpen }]">
    <aside class="sidebar" aria-label="주 내비게이션">
      <RouterLink to="/" class="brand">
        <span class="brand-symbol"><Factory :size="23" :stroke-width="1.9" /></span>
        <span class="brand-copy"><strong>FactoryFlow</strong><small>OPERATIONS CONSOLE</small></span>
      </RouterLink>
      <div class="workspace-mark"><span class="workspace-avatar">F</span><span class="workspace-info"><strong>제조 운영 시스템</strong><small>단일 공장 · 단일 창고</small></span><ChevronRight :size="14" class="muted" /></div>
      <nav class="side-navigation">
        <div v-for="group in navGroups" :key="group.title" class="nav-group">
          <span class="nav-caption">{{ group.title }}</span>
          <RouterLink v-for="entry in group.entries" :key="entry.path" :to="entry.path" class="nav-link" :title="entry.title" :class="{ active: route.path === entry.path }">
            <component :is="entry.icon" :size="18" :stroke-width="1.9" /><span>{{ entry.title }}</span>
          </RouterLink>
        </div>
      </nav>
      <div class="sidebar-bottom"><div class="workspace-environment"><span class="env-indicator"></span><span>DEVELOPMENT</span></div><p>데이터는 실제 백엔드 API에서만 조회합니다.</p></div>
    </aside>
    <div class="main-area">
      <header class="topbar">
        <div class="topbar-left"><button type="button" class="icon-btn" :title="sidebarOpen ? '메뉴 접기' : '메뉴 열기'" @click="sidebarOpen = !sidebarOpen"><PanelLeftClose v-if="sidebarOpen" :size="19"/><PanelLeftOpen v-else :size="19" /></button><span class="breadcrumb-home">FactoryFlow</span><ChevronRight :size="14" class="breadcrumb-arrow" /><span class="breadcrumb-current">{{ title }}</span></div>
        <div class="topbar-right"><span class="preview-tag"><ShieldAlert :size="14" /> 내부 개발용 · 인증 미적용</span><button type="button" class="icon-btn" title="화면 새로고침" @click="refresh"><RefreshCw :size="18" /></button><span class="profile-avatar">FF</span></div>
      </header>
      <main class="page-content">
        <div class="page-heading"><div><div class="eyebrow">FACTORY OPERATIONS</div><h1>{{ title }}</h1><p>{{ subtitle }}</p></div><div class="heading-aside"><span class="outline-pill"><span class="pulse-dot"></span>실제 API 데이터</span></div></div>
        <RouterView :key="`${route.fullPath}-${reloadKey}`" />
      </main>
      <footer class="app-footer">FactoryFlow <span>·</span> Manufacturing operations, made clear. <span class="footer-side">© 2026 · Portfolio environment</span></footer>
    </div>
    <div v-if="ui.notice" :class="['toast', `toast-${ui.notice.tone}`]" role="status"><span>{{ ui.notice.message }}</span><button type="button" aria-label="알림 닫기" @click="ui.dismiss()"><X :size="16" /></button></div>
  </div>
</template>
