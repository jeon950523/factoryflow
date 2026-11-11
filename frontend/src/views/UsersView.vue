<script setup>
import { onMounted, ref } from 'vue'
import { Plus, Users } from 'lucide-vue-next'
import { get, post } from '../lib/api.js'
import { useUiStore } from '../stores/ui.js'

const ui = useUiStore()
const users = ref([])
const error = ref('')
const busy = ref(false)
const saving = ref(false)
const drawer = ref(false)
const form = ref({ username: '', password: '', role: 'VIEWER' })

async function load() {
  busy.value = true; error.value = ''
  try { users.value = await get('/api/v1/admin/users') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function createUser() {
  if (saving.value) return
  saving.value = true
  try {
    await post('/api/v1/admin/users', { ...form.value, username: form.value.username.trim() })
    form.value = { username: '', password: '', role: 'VIEWER' }
    drawer.value = false
    ui.notify('계정을 생성했습니다. 비밀번호는 화면이나 저장소에 표시되지 않습니다.')
    await load()
  } catch (e) { ui.notify(e.message, 'error') }
  finally { saving.value = false }
}
onMounted(load)
</script>
<template>
  <section class="surface-card user-table-panel">
    <div class="card-header"><div><span class="section-kicker">ACCOUNT ADMINISTRATION</span><h2>사용자 및 권한</h2><p>조회 전용 계정은 데이터를 조회할 수 있지만 제조 업무를 변경할 수 없습니다.</p></div><button class="btn btn-primary" type="button" @click="drawer = true"><Plus :size="17"/> 계정 발급</button></div>
    <p v-if="busy" class="item-search-message">계정 목록을 불러오는 중입니다.</p><p v-else-if="error" class="inline-error">{{ error }} <button type="button" @click="load">재시도</button></p>
    <div v-else class="table-scroll"><table class="data-table"><thead><tr><th>계정</th><th>권한</th><th>상태</th></tr></thead><tbody><tr v-for="user in users" :key="user.id"><td>{{ user.username }}</td><td>{{ user.role === 'ADMIN' ? '관리자 · 쓰기/조회' : '조회 전용' }}</td><td>{{ user.active ? '사용 중' : '비활성' }}</td></tr></tbody></table></div>
  </section>
  <div v-if="drawer" class="dialog-backdrop" @click.self="drawer = false"><section class="dialog" role="dialog" aria-modal="true" aria-label="계정 발급"><header class="dialog-header"><div><span class="section-kicker">NEW ACCOUNT</span><h2>계정 발급</h2></div><button class="icon-btn" type="button" aria-label="닫기" @click="drawer = false">×</button></header>
    <form @submit.prevent="createUser"><div class="dialog-body users-form">
      <label class="field-label">아이디<input v-model="form.username" pattern="[A-Za-z][A-Za-z0-9._-]{3,31}" minlength="4" maxlength="32" autocomplete="off" required /></label>
      <label class="field-label">권한<select v-model="form.role"><option value="VIEWER">조회 전용</option><option value="ADMIN">관리자</option></select></label>
      <label class="field-label full-row">초기 비밀번호<input v-model="form.password" minlength="12" maxlength="72" type="password" autocomplete="new-password" required /></label>
      <p class="info-hint full-row">12~72 UTF-8 바이트. 계정 생성 후 비밀번호는 다시 표시되지 않습니다. 현재 MVP에서는 비밀번호 초기화 기능이 없습니다.</p>
    </div><footer class="dialog-footer"><button class="btn btn-outline" type="button" @click="drawer = false">취소</button><button class="btn btn-primary" type="submit" :disabled="saving">{{ saving ? '발급 중...' : '계정 발급' }}</button></footer></form>
  </section></div>
</template>
