<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Factory, LockKeyhole } from 'lucide-vue-next'
import { useAuthStore } from '../stores/auth.js'
import { isDemoMode } from '../lib/api.js'

const username = ref('')
const password = ref('')
const pending = ref(false)
const error = ref('')
const auth = useAuthStore(), router = useRouter(), route = useRoute()

async function submit() {
  if (pending.value) return
  pending.value = true; error.value = ''
  try {
    await auth.login(username.value.trim(), password.value)
    password.value = ''
    const target = route.query.redirect
    await router.replace(typeof target === 'string' && target.startsWith('/') && !target.startsWith('//') ? target : '/')
  } catch (e) {
    error.value = e.message || '로그인에 실패했습니다.'
  } finally { pending.value = false }
}
</script>
<template>
  <section class="login-card" aria-label="관리자 로그인">
    <div class="login-brand"><Factory :size="28" /> FactoryFlow</div>
    <div><h1>{{ isDemoMode ? '브라우저 체험' : '관리자 로그인' }}</h1><p>{{ isDemoMode ? '데모에서는 별도의 로그인 없이 샘플 데이터를 사용합니다.' : '제조 운영 데이터는 로그인한 계정의 권한에 따라 조회하거나 수정할 수 있습니다.' }}</p></div>
    <form v-if="!isDemoMode" @submit.prevent="submit">
      <label>아이디<input v-model="username" name="username" autocomplete="username" required maxlength="32" autofocus /></label>
      <label>비밀번호<input v-model="password" name="password" autocomplete="current-password" type="password" required /></label>
      <p v-if="error" class="login-error" role="alert">{{ error }}</p>
      <button type="submit" class="btn btn-primary" :disabled="pending"><LockKeyhole :size="17" /> {{ pending ? '로그인 중...' : '로그인' }}</button>
    </form>
    <p>{{ isDemoMode ? '데모 데이터는 실제 계정·서버 인증과 연결되지 않습니다.' : '계정은 관리자에 의해 발급됩니다. 브라우저에는 비밀번호나 인증 토큰을 저장하지 않습니다.' }}</p>
  </section>
</template>
