<script setup>
import { nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Plus, Search, Layers, X, RefreshCw, ArrowRight } from 'lucide-vue-next'
import './items-workbench.css'
import { get, post } from '../lib/api.js'
import { useAuthStore } from '../stores/auth.js'
import { createLatestRequest, isAbortError } from '../lib/latestRequest.js'
import { lookupItemNames, itemNameOrId, itemSkuOrId } from '../lib/itemNames.js'
import { number, dateTime } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'

const ui = useUiStore()
const route = useRoute(), router = useRouter()
const auth = useAuthStore()
const listGate = createLatestRequest(), detailGate = createLatestRequest()
onBeforeUnmount(() => { listGate.cancel(); detailGate.cancel() })
const items = ref({ content: [], totalElements: 0, totalPages: 0 })
const initialPage = Number(route.query.page)
const page = ref(typeof route.query.page === 'string' && /^\d+$/.test(route.query.page)
  && Number.isSafeInteger(initialPage) ? initialPage : 0)
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const filter = ref(['RAW_MATERIAL', 'FINISHED_GOOD'].includes(route.query.itemType)
  ? route.query.itemType : '')
const busy = ref(false)
const error = ref('')
const drawer = ref('')
const selected = ref(null)
const detailError = ref(''), detailSection = ref(null)
const selectedBom = ref(null)
const bomError = ref('')
const bomNameWarning = ref('')
const saving = ref(false)
const itemForm = ref({ sku: '', name: '', itemType: 'RAW_MATERIAL' })
const bomForm = ref({ components: [{ rawItemId: '', qtyPerUnit: 1 }] })
const itemNames = ref({})
function rememberMaterial(item) {
  if (item) itemNames.value = { ...itemNames.value, [item.id]: item }
}

async function load() {
  const request = listGate.begin()
  busy.value = true; error.value = ''
  try {
    const result = await get('/api/v1/items',
      { page: page.value, size: 15, itemType: filter.value || undefined, keyword: keyword.value || undefined },
      { signal: request.signal })
    if (request.isCurrent()) items.value = result
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) error.value = e.message
  } finally {
    if (request.isCurrent()) busy.value = false
  }
}
async function pick(item) {
  const request = detailGate.begin()
  selected.value = item; selectedBom.value = null
  detailError.value = ''; bomError.value = ''; bomNameWarning.value = ''
  await nextTick()
  if (request.isCurrent()) detailSection.value?.scrollIntoView({ behavior: 'auto', block: 'start' })
  if (item.itemType !== 'FINISHED_GOOD') return
  try {
    const bom = await get(`/api/v1/boms/${item.id}`, undefined, { signal: request.signal })
    if (!request.isCurrent()) return
    selectedBom.value = bom
    try {
      const names = await lookupItemNames(bom.components.map(component => component.rawItemId), { signal: request.signal })
      if (!request.isCurrent()) return
      itemNames.value = { ...itemNames.value, ...names }
    } catch (e) {
      if (request.isCurrent() && !isAbortError(e)) bomNameWarning.value = '원자재명 조회에 실패했습니다. 품목 ID로 표시합니다.'
    }
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e) && e.status !== 404) bomError.value = e.message
  }
}
async function createItem() {
  saving.value = true
  try {
    const created = await post('/api/v1/items', itemForm.value)
    ui.notify(`${created.name} 품목을 등록했습니다.`)
    drawer.value = ''; itemForm.value = { sku: '', name: '', itemType: 'RAW_MATERIAL' }
    await router.replace({ path: '/items', query: { itemId: String(created.id) } })
  } catch (e) { ui.notify(e.message, 'error') }
  finally { saving.value = false }
}
function openBom() { bomForm.value = { components: [{ rawItemId: '', qtyPerUnit: 1 }] }; drawer.value = 'bom' }
function addComponent() { bomForm.value.components.push({ rawItemId: '', qtyPerUnit: 1 }) }
async function createBom() {
  if (!selected.value) return
  const components = bomForm.value.components.map(c => ({ rawItemId: Number(c.rawItemId), qtyPerUnit: Number(c.qtyPerUnit) }))
  if (components.some(c => !Number.isSafeInteger(c.rawItemId) || c.rawItemId < 1 || !Number.isSafeInteger(c.qtyPerUnit) || c.qtyPerUnit < 1)) {
    ui.notify('각 원자재와 1 이상의 수량을 입력하세요.', 'error'); return
  }
  if (new Set(components.map(c => c.rawItemId)).size !== components.length) {
    ui.notify('동일 원자재를 중복 등록할 수 없습니다.', 'error'); return
  }
  saving.value = true
  try {
    selectedBom.value = await post('/api/v1/boms', { finishedItemId: selected.value.id, components })
    drawer.value = ''; ui.notify('BOM을 등록했습니다.'); await load()
  } catch (e) { ui.notify(e.message, 'error') }
  finally { saving.value = false }
}
function listQuery({ pageNo = page.value, itemId } = {}) {
  return {
    ...(filter.value ? { itemType: filter.value } : {}),
    ...(keyword.value ? { keyword: keyword.value } : {}),
    ...(pageNo > 0 ? { page: String(pageNo) } : {}),
    ...(itemId ? { itemId: String(itemId) } : {}),
  }
}
function changePage(next) {
  router.replace({ path: '/items', query: listQuery({ pageNo: next }) })
}
function search() {
  const query = listQuery({ pageNo: 0 })
  const current = route.query
  if (current.itemId === undefined && String(current.itemType ?? '') === String(query.itemType ?? '')
    && String(current.keyword ?? '') === String(query.keyword ?? '') && current.page === undefined) {
    selected.value = null
    detailGate.cancel()
    load()
    return
  }
  router.replace({ path: '/items', query })
}
function openItem(item) {
  if (String(route.query.itemId ?? '') === String(item.id)) {
    pick(item)
    return
  }
  router.replace({ path: '/items', query: listQuery({ itemId: item.id }) })
}
async function restoreItem() {
  const id = route.query.itemId
  if (typeof id !== 'string' || !/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) return
  const request = detailGate.begin()
  try {
    const item = await get(`/api/v1/items/${id}`, undefined, { signal: request.signal })
    if (request.isCurrent()) await pick(item)
  } catch (e) {
    if (request.isCurrent() && !isAbortError(e)) detailError.value = e.message
  }
}
function closeDrawer() { if (!saving.value) drawer.value = '' }
onMounted(() => { load(); restoreItem() })
</script>
<template>
  <div class="erp-items">
    <div class="erp-items-toolbar">
      <div class="erp-items-filter">
        <div class="search-field">
          <Search :size="17" />
          <input v-model.trim="keyword" aria-label="품목명 또는 코드 검색"
            placeholder="품목명 또는 코드 검색" @keyup.enter="search" />
          <button type="button" @click="search">검색</button>
        </div>
        <label class="erp-items-type-label" for="items-type">품목 유형
          <select id="items-type" v-model="filter" class="select-filter" @change="search">
            <option value="">전체 유형</option>
            <option value="RAW_MATERIAL">원자재</option>
            <option value="FINISHED_GOOD">완제품</option>
          </select>
        </label>
        <button type="button" class="btn btn-subtle" @click="load"><RefreshCw :size="16" /> 목록 새로고침</button>
      </div>
      <button v-if="auth.canWrite" type="button" class="btn btn-primary" @click="drawer = 'item'">
        <Plus :size="17" /> 품목 등록
      </button>
    </div>

    <section class="erp-items-sheet" aria-labelledby="items-list-title">
      <div class="erp-items-heading">
        <div><h2 id="items-list-title">품목 기준 정보 <small>{{ number(items.totalElements) }}종</small></h2>
          <p>품목을 선택하면 상세 속성과 등록된 BOM의 원자재 구성표를 확인할 수 있습니다.</p></div>
      </div>
      <AsyncState :loading="busy" :error="error" :empty="!items.content.length"
        label="해당 조건에 맞는 품목이 없습니다. 검색 조건을 바꾸거나 품목을 등록하세요." @retry="load">
        <div class="table-scroll">
          <table class="data-table erp-items-table" aria-label="품목 기준 정보 목록">
            <thead><tr>
              <th scope="col">품목명 / SKU</th>
              <th scope="col">품목 유형</th>
              <th scope="col">기본 단위</th>
              <th scope="col">사용 상태</th>
              <th scope="col">상세</th>
            </tr></thead>
            <tbody>
              <tr v-for="item in items.content" :key="item.id" :class="{ selected: selected?.id === item.id }">
                <td><button type="button" class="row-action"
                  :aria-pressed="selected?.id === item.id" @click="openItem(item)">{{ item.name }}</button>
                  <small class="cell-sub">{{ item.sku }}</small></td>
                <td><StatusBadge :status="item.itemType" /></td>
                <td>{{ item.unit || 'EA' }}</td>
                <td><span class="tiny-dot" :class="item.active ? 'active' : 'inactive'"></span>
                  {{ item.active ? '사용 중' : '비활성' }}</td>
                <td><RouterLink class="erp-items-open" :aria-label="`${item.name} 상세 보기`"
                  :to="{ path: '/items', query: listQuery({ itemId: item.id }) }">상세 보기 <ArrowRight :size="14" /></RouterLink></td>
              </tr>
            </tbody>
          </table>
        </div>
        <PageControls :page="page" :total-pages="items.totalPages"
          :total-elements="items.totalElements" @change="changePage" />
      </AsyncState>
    </section>

    <section ref="detailSection" class="erp-items-sheet erp-item-detail detail-panel" aria-labelledby="item-detail-title">
      <div class="erp-items-heading erp-item-detail-heading">
        <div><h2 id="item-detail-title">품목 상세 · BOM</h2>
          <p>선택 품목의 기본 정보와 완제품 1개당 소요 원자재를 확인합니다.</p></div>
        <span v-if="selected" class="erp-detail-ref">{{ selected.sku }}</span>
      </div>
      <div v-if="detailError" class="erp-item-error" role="alert">{{ detailError }}
        <button type="button" class="btn btn-subtle" @click="restoreItem">다시 조회</button>
      </div>
      <div v-else-if="!selected" class="erp-items-empty">
        <Layers :size="22" :stroke-width="1.7" />
        <span>위 목록에서 품목을 선택하면 상세 정보와 BOM 구성이 표시됩니다.</span>
      </div>
      <div v-else>
        <div class="erp-item-meta" aria-label="선택 품목 기본 정보">
          <div><small>품목명</small><strong>{{ selected.name }}</strong></div>
          <div><small>품목 코드</small><strong>{{ selected.sku }}</strong></div>
          <div><small>유형</small><StatusBadge :status="selected.itemType" /></div>
          <div><small>등록 시각</small><strong>{{ dateTime(selected.createdAt) }}</strong></div>
        </div>
        <div v-if="selected.itemType === 'FINISHED_GOOD'" class="erp-item-bom">
          <div class="erp-items-subheading">
            <div><h3>BOM 구성 원자재</h3><p>완제품 1개당 기준 소요량. 생산지시 때는 해당 BOM을 기준으로 필요 수량을 확정합니다.</p></div>
            <button v-if="auth.canWrite && !selectedBom" class="btn btn-outline btn-small" type="button" @click="openBom">
              <Plus :size="15" /> BOM 등록
            </button>
          </div>
          <p v-if="bomNameWarning" class="inline-error" role="status">{{ bomNameWarning }}</p>
          <p v-if="bomError" class="inline-error" role="alert">{{ bomError }}</p>
          <div v-else-if="!selectedBom" class="erp-items-empty erp-bom-empty">
            등록된 BOM이 없습니다. 생산지시 전에 원자재 구성을 등록해 주세요.
          </div>
          <div v-else class="table-scroll">
            <table class="data-table erp-bom-table" aria-label="완제품 BOM 구성표">
              <thead><tr>
                <th scope="col">원자재명 / SKU</th>
                <th scope="col">원자재 ID</th>
                <th scope="col" class="right">완제품 1개당 소요량</th>
              </tr></thead>
              <tbody>
                <tr v-for="component in selectedBom.components" :key="component.rawItemId">
                  <td><strong>{{ itemNameOrId(itemNames, component.rawItemId, '원자재') }}</strong>
                    <small class="cell-sub">{{ itemSkuOrId(itemNames, component.rawItemId) }}</small></td>
                  <td>#{{ component.rawItemId }}</td>
                  <td class="number-cell">{{ number(component.qtyPerUnit) }} EA</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="erp-items-note">BOM은 현재 등록만 지원하며 수정·버전 관리는 이 MVP 범위에 포함되지 않습니다.</p>
        </div>
        <div v-else class="erp-items-raw-note">원자재 품목입니다. 재고·입고 화면에서 LOT별 현재고를 확인할 수 있습니다.
          <RouterLink :to="{ path: '/inventory', query: { itemType: 'RAW_MATERIAL', itemId: String(selected.id) } }">
            재고·LOT 확인 <ArrowRight :size="14" />
          </RouterLink>
        </div>
      </div>
    </section>

    <div v-if="drawer" class="dialog-backdrop" @click.self="closeDrawer">
      <section class="dialog" role="dialog" aria-modal="true" :aria-label="drawer === 'item' ? '품목 등록' : 'BOM 등록'">
        <header class="dialog-header">
          <div><h2>{{ drawer === 'item' ? '품목 등록' : 'BOM 등록' }}</h2></div>
          <button type="button" class="icon-btn" aria-label="닫기" :disabled="saving" @click="closeDrawer"><X :size="20" /></button>
        </header>
        <form v-if="drawer === 'item'" @submit.prevent="createItem">
          <div class="dialog-body">
            <label class="field-label">품목 코드 <span>*</span>
              <input v-model.trim="itemForm.sku" required maxlength="64" placeholder="예: RM-SHEET-001" :disabled="saving" /></label>
            <label class="field-label">품목 이름 <span>*</span>
              <input v-model.trim="itemForm.name" required maxlength="160" placeholder="예: 강판 A" :disabled="saving" /></label>
            <label class="field-label">품목 유형 <span>*</span>
              <select v-model="itemForm.itemType" :disabled="saving">
                <option value="RAW_MATERIAL">원자재</option><option value="FINISHED_GOOD">완제품</option>
              </select></label>
            <p class="info-hint">중복 SKU는 등록할 수 없습니다. 등록 후 수정 기능은 현재 제공하지 않습니다.</p>
          </div>
          <footer class="dialog-footer">
            <button type="button" class="btn btn-outline" :disabled="saving" @click="closeDrawer">취소</button>
            <button type="submit" class="btn btn-primary" :disabled="saving">{{ saving ? '등록 중...' : '품목 등록' }}</button>
          </footer>
        </form>
        <form v-else @submit.prevent="createBom">
          <div class="dialog-body">
            <p class="info-hint">완제품 <strong>{{ selected?.name }}</strong>에 필요한 원자재를 등록합니다. BOM 수정·버전 관리는 지원하지 않습니다.</p>
            <div v-for="(component, idx) in bomForm.components" :key="idx" class="component-input-row">
              <div class="field-label"><span class="field-title">원자재</span>
                <ItemSearchSelect v-model="component.rawItemId" item-type="RAW_MATERIAL"
                  placeholder="원자재 검색 · 선택" :disabled="saving" @selected="rememberMaterial" /></div>
              <label class="field-label short">소요량
                <input v-model.number="component.qtyPerUnit" type="number" min="1" step="1" max="1000000" required :disabled="saving" /></label>
              <button type="button" class="btn-remove" :disabled="saving || bomForm.components.length === 1"
                @click="bomForm.components.splice(idx, 1)" aria-label="해당 원자재 삭제"><X :size="16" /></button>
            </div>
            <button type="button" class="btn btn-subtle" :disabled="saving || bomForm.components.length >= 100"
              @click="addComponent"><Plus :size="15" /> 자재 추가</button>
          </div>
          <footer class="dialog-footer">
            <button type="button" class="btn btn-outline" :disabled="saving" @click="closeDrawer">취소</button>
            <button type="submit" class="btn btn-primary"
              :disabled="saving || bomForm.components.some(c => !c.rawItemId)">
              {{ saving ? '저장 중...' : 'BOM 등록' }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </div>
</template>
