<script setup>
defineProps({ loading: Boolean, error: String, empty: Boolean, label: { type: String, default: '표시할 데이터가 없습니다.' } })
defineEmits(['retry'])
</script>
<template>
  <div v-if="loading" class="state-box" role="status"><span class="spinner"></span><span>데이터를 불러오는 중입니다.</span></div>
  <div v-else-if="error" class="state-box state-error" role="alert"><strong>데이터를 불러오지 못했습니다.</strong><p>{{ error }}</p><button type="button" class="btn btn-outline" @click="$emit('retry')">다시 시도</button></div>
  <div v-else-if="empty" class="state-box" role="status"><span class="state-icon">○</span><strong>아직 등록된 데이터가 없습니다</strong><p>{{ label }}</p></div>
  <slot v-else />
</template>
