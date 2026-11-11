import { defineStore } from 'pinia'

export const useUiStore = defineStore('ui', {
  state: () => ({ notice: null }),
  actions: {
    notify(message, tone = 'success') {
      this.notice = { message, tone, id: Date.now() }
    },
    dismiss() { this.notice = null },
  },
})
