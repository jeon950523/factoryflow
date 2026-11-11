<script setup>
import { computed, onMounted, ref } from 'vue'
import { Plus, Search, Layers, Package, X, RefreshCw, ArrowRight } from 'lucide-vue-next'
import { get, post } from '../lib/api.js'
import { number, dateTime } from '../lib/format.js'
import { useUiStore } from '../stores/ui.js'
import StatusBadge from '../components/StatusBadge.vue'
import AsyncState from '../components/AsyncState.vue'
import PageControls from '../components/PageControls.vue'
import ItemSearchSelect from '../components/ItemSearchSelect.vue'

const ui = useUiStore()
const items = ref({ content: [], totalElements: 0, totalPages: 0 })
const page = ref(0)
const keyword = ref('')
const filter = ref('')
const busy = ref(false)
const error = ref('')
const drawer = ref('')
const selected = ref(null)
const selectedBom = ref(null)
const bomError = ref('')
const saving = ref(false)
const itemForm = ref({ sku: '', name: '', itemType: 'RAW_MATERIAL' })
const bomForm = ref({ components: [{ rawItemId: '', qtyPerUnit: 1 }] })
const allAvailable = ref([])
const chosenMaterialNames = ref({})
function rememberMaterial(item) {
  if (item) chosenMaterialNames.value[item.id] = item.name
}

async function load() {
  busy.value = true; error.value = ''
  try {
    const [listed, available] = await Promise.all([
      get('/api/v1/items', { page: page.value, size: 15, itemType: filter.value || undefined, keyword: keyword.value || undefined }),
      get('/api/v1/items', { size: 100, page: 0 }),
    ])
    items.value = listed
    allAvailable.value = available.content
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function pick(item) {
  selected.value = item; selectedBom.value = null; bomError.value = ''
  if (item.itemType !== 'FINISHED_GOOD') return
  try { selectedBom.value = await get(`/api/v1/boms/${item.id}`) }
  catch (e) { if (e.status !== 404) bomError.value = e.message }
}
async function createItem() {
  saving.value = true
  try {
    const created = await post('/api/v1/items', itemForm.value)
    ui.notify(`${created.name} 품목을 등록했습니다.`)
    drawer.value = ''; itemForm.value = { sku: '', name: '', itemType: 'RAW_MATERIAL' }
    page.value = 0; await load()
    await pick(created)
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
function changePage(next) { page.value = next; load() }
function search() { page.value = 0; load() }
onMounted(load)
</script>

<template>
  <div class="toolbar-card"><div class="toolbar-left"><div class="search-field"><Search :size="17"/><input v-model="keyword" placeholder="품목명 또는 코드 검색" @keyup.enter="search" /><button type="button" @click="search">검색</button></div><select v-model="filter" class="select-filter" @change="search"><option value="">전체 유형</option><option value="RAW_MATERIAL">원자재</option><option value="FINISHED_GOOD">완제품</option></select><button type="button" class="btn btn-subtle" title="목록 새로고침" @click="load"><RefreshCw :size="16" /></button></div><button class="btn btn-primary" type="button" @click="drawer = 'item'"><Plus :size="17"/> 품목 등록</button></div>
  <div class="section-grid item-workspace"><section class="surface-card"><div class="card-header compact"><div><span class="section-kicker">ITEM MASTER</span><h2>품목 기준 정보</h2><p>원자재와 완제품의 등록 현황</p></div></div><AsyncState :loading="busy" :error="error" :empty="!items.content.length" label="상단의 품목 등록을 눌러 시작하세요." @retry="load"><div class="table-scroll"><table class="data-table"><thead><tr><th>품목 코드 · 이름</th><th>유형</th><th>단위</th><th>상태</th></tr></thead><tbody><tr v-for="item in items.content" :key="item.id" :class="{ selected: selected?.id === item.id }"><td><button type="button" class="row-action" @click="pick(item)">{{ item.name }}</button><small class="cell-sub">{{ item.sku }}</small></td><td><StatusBadge :status="item.itemType" /></td><td>EA</td><td><span class="tiny-dot" :class="item.active ? 'active' : 'inactive'"></span>{{ item.active ? '사용 중' : '비활성' }}</td></tr></tbody></table></div><PageControls :page="page" :total-pages="items.totalPages" :total-elements="items.totalElements" @change="changePage" /></AsyncState></section>
    <section class="surface-card detail-panel"><div class="card-header compact"><div><span class="section-kicker">ITEM DETAIL</span><h2>품목 상세 · BOM</h2><p>목록에서 품목을 선택하세요.</p></div></div><div v-if="!selected" class="detail-placeholder"><Layers :size="36" :stroke-width="1.5"/><h3>품목을 선택해 주세요</h3><p>품목의 유형 및 BOM 구성 정보를 확인할 수 있습니다.</p></div><div v-else class="detail-body"><div class="detail-title"><span class="detail-avatar"><Package :size="24" /></span><div><strong>{{ selected.name }}</strong><span>{{ selected.sku }}</span></div></div><dl class="key-values"><div><dt>품목 ID</dt><dd>#{{ selected.id }}</dd></div><div><dt>유형</dt><dd><StatusBadge :status="selected.itemType" /></dd></div><div><dt>등록 일시</dt><dd>{{ dateTime(selected.createdAt) }}</dd></div><div><dt>기본 단위</dt><dd>EA</dd></div></dl><template v-if="selected.itemType === 'FINISHED_GOOD'"><div class="subsection-heading"><div><span class="section-kicker">BILL OF MATERIALS</span><h3>필요 원자재</h3></div><button v-if="!selectedBom" class="btn btn-outline btn-small" type="button" @click="openBom"><Plus :size="14" /> BOM 등록</button></div><p v-if="bomError" class="inline-error">{{ bomError }}</p><div v-else-if="!selectedBom" class="small-empty">등록된 BOM이 없습니다. 생산지시 전에 BOM을 등록해 주세요.</div><div v-else class="material-list"><div v-for="component in selectedBom.components" :key="component.rawItemId" class="material-row"><span><span class="material-code">#{{ component.rawItemId }}</span> {{ chosenMaterialNames[component.rawItemId] || allAvailable.find(item => item.id === component.rawItemId)?.name || `원자재 #${component.rawItemId}` }}</span><strong>{{ number(component.qtyPerUnit) }} <small>EA / 완제품 1개</small></strong></div></div></template><p v-else class="detail-footnote">원자재 품목입니다. 입고 후 생산지시의 BOM 구성으로 사용할 수 있습니다.</p></div></section>
  </div>
  <div v-if="drawer" class="dialog-backdrop" @click.self="drawer = ''"><section class="dialog" role="dialog" aria-modal="true" :aria-label="drawer === 'item' ? '품목 등록' : 'BOM 등록'"><header class="dialog-header"><div><span class="section-kicker">NEW ENTRY</span><h2>{{ drawer === 'item' ? '품목 등록' : 'BOM 등록' }}</h2></div><button type="button" class="icon-btn" aria-label="닫기" @click="drawer = ''"><X :size="20" /></button></header><form v-if="drawer === 'item'" @submit.prevent="createItem"><div class="dialog-body"><label class="field-label">품목 코드 <span>*</span><input v-model.trim="itemForm.sku" required maxlength="64" placeholder="예: RM-SHEET-001" /></label><label class="field-label">품목 이름 <span>*</span><input v-model.trim="itemForm.name" required maxlength="160" placeholder="예: 강판 A" /></label><label class="field-label">품목 유형 <span>*</span><select v-model="itemForm.itemType"><option value="RAW_MATERIAL">원자재</option><option value="FINISHED_GOOD">완제품</option></select></label><div class="info-hint">중복 SKU는 등록할 수 없으며 저장 후 수정 기능은 현재 제공하지 않습니다.</div></div><footer class="dialog-footer"><button type="button" class="btn btn-outline" @click="drawer = ''">취소</button><button type="submit" class="btn btn-primary" :disabled="saving">{{ saving ? '등록 중...' : '품목 등록' }}</button></footer></form><form v-else @submit.prevent="createBom"><div class="dialog-body"><div class="info-hint">완제품 <strong>{{ selected?.name }}</strong>에 필요한 원자재 수량을 등록합니다. 기존 BOM 수정은 다음 버전 범위입니다.</div><div v-for="(component, idx) in bomForm.components" :key="idx" class="component-input-row"><div class="field-label"><span class="field-title">원자재</span><ItemSearchSelect v-model="component.rawItemId" item-type="RAW_MATERIAL" placeholder="원자재 검색 · 선택" :disabled="saving" @selected="rememberMaterial" /></div><label class="field-label short">소요량<input v-model.number="component.qtyPerUnit" type="number" min="1" step="1" max="1000000" required /></label><button type="button" class="btn-remove" :disabled="bomForm.components.length === 1" @click="bomForm.components.splice(idx, 1)"><X :size="16"/></button></div><button type="button" class="btn btn-subtle" :disabled="bomForm.components.length >= 100" @click="addComponent"><Plus :size="15" /> 자재 추가</button></div><footer class="dialog-footer"><button type="button" class="btn btn-outline" @click="drawer = ''">취소</button><button type="submit" class="btn btn-primary"  :disabled="saving || bomForm.components.some(c => !c.rawItemId)">{{ saving ? '저장 중...' : 'BOM 등록' }}</button></footer></form></section></div>
</template>
