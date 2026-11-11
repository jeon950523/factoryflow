<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ChevronDown, ChevronLeft, ChevronRight, Search, X } from 'lucide-vue-next'
import { get } from '../lib/api.js'

const props = defineProps({
  modelValue: { type: [Number, String], default: '' },
  itemType: { type: String, required: true },
  placeholder: { type: String, default: '품목 선택' },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'selected'])

const opened = ref(false)
const selected = ref(null)
const keyword = ref('')
const appliedKeyword = ref('')
const options = ref([])
const loading = ref(false)
const error = ref('')
const page = ref(0)
const totalPages = ref(0)
const totalElements = ref(0)
let requestNumber = 0
let activeController = null

const selectedLabel = computed(() => {
  if (selected.value && Number(props.modelValue) === selected.value.id) {
    return selected.value.sku + ' · ' + selected.value.name
  }
  return props.modelValue ? '선택한 품목 #' + props.modelValue : props.placeholder
})

function stopRequest() {
  requestNumber += 1
  activeController?.abort()
  activeController = null
  loading.value = false
}

async function searchPage(nextPage = 0) {
  if (!opened.value || props.disabled) return
  stopRequest()
  const sequence = requestNumber
  activeController = new AbortController()
  const signal = activeController.signal
  loading.value = true
  error.value = ''
  page.value = nextPage // 실패해도 재시도 시 사용자가 요청했던 페이지를 유지한다.
  try {
    const result = await get('/api/v1/items', {
      itemType: props.itemType,
      keyword: appliedKeyword.value || undefined,
      page: nextPage,
      size: 10,
    }, { signal })
    if (sequence !== requestNumber || !opened.value) return
    options.value = result.content
    page.value = nextPage
    totalPages.value = result.totalPages
    totalElements.value = result.totalElements
  } catch (e) {
    if (sequence !== requestNumber || e.name === 'AbortError') return
    error.value = e.message
    options.value = []
  } finally {
    if (sequence === requestNumber) loading.value = false
  }
}

function toggle() {
  if (props.disabled) return
  opened.value = !opened.value
  if (opened.value) searchPage(0)
  else stopRequest()
}

function applySearch() {
  appliedKeyword.value = keyword.value.trim()
  searchPage(0)
}

function choose(item) {
  if (item.itemType !== props.itemType) return
  selected.value = item
  emit('update:modelValue', item.id)
  emit('selected', item)
  opened.value = false
  stopRequest()
}

function clear() {
  selected.value = null
  emit('update:modelValue', '')
  emit('selected', null)
  opened.value = false
  stopRequest()
}

watch(() => props.modelValue, value => {
  if (value === '' || value === null || value === undefined || Number(value) !== selected.value?.id) {
    selected.value = null
  }
})
watch(() => props.itemType, () => {
  clear()
  appliedKeyword.value = ''
  keyword.value = ''
})
watch(() => props.disabled, disabled => {
  if (disabled && opened.value) {
    opened.value = false
    stopRequest()
  }
})
onBeforeUnmount(stopRequest)
</script>

<template>
  <div class="item-search-select" @keydown.esc.stop.prevent="opened && toggle()">
    <div class="item-search-summary">
      <button type="button" class="item-search-toggle" :class="{ 'has-value': !!modelValue }"
              :disabled="disabled" :aria-expanded="opened" :aria-label="placeholder + ': ' + selectedLabel" aria-haspopup="true" @click="toggle">
        <span>{{ selectedLabel }}</span><ChevronDown :size="16" aria-hidden="true" />
      </button>
      <button v-if="modelValue && !disabled" type="button" class="item-search-clear"
              aria-label="선택한 품목 지우기" @click="clear"><X :size="15" /></button>
    </div>
    <div v-if="opened" class="item-search-panel">
      <div class="item-search-bar">
        <input v-model="keyword" type="search" maxlength="100" aria-label="품목 코드 또는 이름 검색"
               placeholder="품목 코드 또는 이름 검색" @keyup.enter.prevent="applySearch" />
        <button type="button" class="btn btn-small btn-outline" :disabled="loading" @click="applySearch">
          <Search :size="14" /> 검색
        </button>
      </div>
      <p v-if="loading" class="item-search-message" role="status">품목을 검색하고 있습니다.</p>
      <div v-else-if="error" class="item-search-message" role="alert">
        {{ error }} <button type="button" class="row-link" @click="searchPage(page)">재시도</button>
      </div>
      <p v-else-if="!options.length" class="item-search-message">일치하는 품목이 없습니다.</p>
      <ul v-else class="item-search-options">
        <li v-for="item in options" :key="item.id">
          <button type="button" :aria-pressed="Number(modelValue) === item.id" class="item-search-option" @click="choose(item)">
            <strong>{{ item.name }}</strong><small>{{ item.sku }}</small>
          </button>
        </li>
      </ul>
      <div v-if="!loading && !error && totalPages > 0" class="item-search-pager">
        <span>총 {{ totalElements.toLocaleString('ko-KR') }}건 · {{ page + 1 }}/{{ totalPages }}쪽</span>
        <div>
          <button type="button" aria-label="이전 품목 검색 결과" :disabled="page === 0"
                  @click="searchPage(page - 1)"><ChevronLeft :size="15" /></button>
          <button type="button" aria-label="다음 품목 검색 결과" :disabled="page + 1 >= totalPages"
                  @click="searchPage(page + 1)"><ChevronRight :size="15" /></button>
        </div>
      </div>
    </div>
  </div>
</template>
