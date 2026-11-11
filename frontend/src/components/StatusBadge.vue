<script setup>
import { statusLabel, statusTone } from '../lib/format.js'
defineProps({ status: { type: String, required: true } })
</script>
<template><span :class="['badge', `badge-${statusTone(status)}`]"><span class="badge-dot"></span>{{ statusLabel(status) }}</span></template>
