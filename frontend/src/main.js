import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router/index.js'
import './assets/base.css'
import './assets/accessibility.css'
import { dialogFocus } from './directives/dialogFocus.js'
import { setSessionExpiredHandler } from './lib/api.js'
import { useAuthStore } from './stores/auth.js'

const pinia = createPinia()
setSessionExpiredHandler(() => {
  useAuthStore(pinia).sessionExpired()
  if (router.currentRoute.value.name !== 'login') router.replace('/login')
})
createApp(App).use(pinia).use(router).directive('dialog-focus', dialogFocus).mount('#app')
