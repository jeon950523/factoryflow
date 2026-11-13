<script setup>
defineProps({ page: { type: Number, default: 0 }, totalPages: { type: Number, default: 0 }, totalElements: { type: Number, default: 0 } })
defineEmits(['change'])
</script>
<template>
  <div class="page-controls"><span>총 <strong>{{ totalElements.toLocaleString('ko-KR') }}</strong>건</span><nav class="pager" aria-label="페이지 이동"><button type="button" :disabled="page === 0" @click="$emit('change', page - 1)">이전</button><span aria-live="polite">{{ totalPages ? page + 1 : 0 }} / {{ totalPages }}</span><button type="button" :disabled="page + 1 >= totalPages" @click="$emit('change', page + 1)">다음</button></nav></div>
</template>
