import { defineStore } from 'pinia'
import { clearCsrfToken, ensureCsrfToken, get, loginForm, logoutSession } from '../lib/api.js'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null, ready: false, loading: false }),
  getters: { canWrite: state => state.user?.role === 'ADMIN' },
  actions: {
    async bootstrap() {
      if (this.ready || this.loading) return
      this.loading = true
      try {
        this.user = await get('/api/v1/auth/me')
      } catch {
        this.user = null
      } finally {
        this.ready = true
        this.loading = false
      }
    },
    async login(username, password) {
      const user = await loginForm(username, password)
      clearCsrfToken() // Spring rotates the CSRF secret when authentication succeeds.
      await ensureCsrfToken()
      this.user = user
      this.ready = true
      return user
    },
    async logout() {
      try {
        await logoutSession()
      } finally {
        clearCsrfToken()
        this.user = null
        this.ready = true
      }
    },
    sessionExpired() {
      clearCsrfToken()
      this.user = null
      this.ready = true
    },
  },
})
