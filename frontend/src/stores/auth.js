import { defineStore } from 'pinia'
import { clearCsrfToken, get, loginForm, logoutSession } from '../lib/api.js'

let bootstrapPromise = null

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null, ready: false, loading: false, revision: 0 }),
  getters: { canWrite: state => state.user?.role === 'ADMIN' },
  actions: {
    async bootstrap() {
      if (this.ready) return
      if (bootstrapPromise) return bootstrapPromise
      this.loading = true
      const revision = this.revision
      const request = (async () => {
        try {
          const user = await get('/api/v1/auth/me')
          if (revision === this.revision) this.user = user
        } catch {
          if (revision === this.revision) this.user = null
        } finally {
          if (revision === this.revision) {
            this.ready = true
            this.loading = false
          }
        }
      })()
      bootstrapPromise = request
      try { await request } finally {
        if (bootstrapPromise === request) bootstrapPromise = null
      }
    },
    async login(username, password) {
      const user = await loginForm(username, password)
      clearCsrfToken() // Spring rotates the CSRF secret when authentication succeeds.
      // Delay fetching the new token until the next write to avoid false login failure.
      this.revision += 1
      this.user = user
      this.ready = true
      this.loading = false
      return user
    },
    async logout() {
      try {
        await logoutSession()
      } finally {
        clearCsrfToken()
        this.revision += 1
        this.user = null
        this.ready = true
        this.loading = false
      }
    },
    sessionExpired() {
      clearCsrfToken()
      this.revision += 1
      this.user = null
      this.ready = true
      this.loading = false
    },
  },
})
