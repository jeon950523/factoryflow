/**
 * Reusable focus management for the five ERP slide-over dialogs.
 * The directive does not change form state or submit behavior.
 *
 * Usage: v-dialog-focus="{ close: closeDrawer, locked: saving }"
 * on the element with role="dialog". The owner remains responsible for
 * blocking backdrop/cancel buttons while a write operation is in flight.
 */
const stateKey = Symbol('dialogFocus')

const selectors = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

function focusables(dialog) {
  return [...dialog.querySelectorAll(selectors)].filter(element =>
    element.getClientRects().length > 0 && getComputedStyle(element).visibility !== 'hidden'
  )
}

function focusStart(dialog) {
  const initial = dialog.querySelector(
    '[data-dialog-initial-focus], .dialog-body input:not([disabled]),' +
    ' .dialog-body .item-search-toggle:not([disabled]), .dialog-body select:not([disabled])'
  )
  const target = initial || focusables(dialog)[0] || dialog
  target.focus({ preventScroll: true })
}

export const dialogFocus = {
  mounted(el, binding) {
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (!el.hasAttribute('tabindex')) el.setAttribute('tabindex', '-1')

    const state = { previous, options: binding.value }
    state.onKeydown = event => {
      if (event.key === 'Escape') {
        event.stopPropagation()
        event.preventDefault()
        if (!state.options?.locked) state.options?.close?.()
        return
      }
      if (event.key !== 'Tab') return

      const options = focusables(el)
      const first = options[0], last = options[options.length - 1]
      if (!first) {
        event.preventDefault()
        el.focus({ preventScroll: true })
      } else if (!el.contains(document.activeElement) || document.activeElement === el) {
        event.preventDefault()
        const target = event.shiftKey ? last : first
        target.focus({ preventScroll: true })
      } else if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus({ preventScroll: true })
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus({ preventScroll: true })
      }
    }
    state.onFocusin = event => {
      if (el.isConnected && !el.contains(event.target)) focusStart(el)
    }

    el[stateKey] = state
    // Document listener also catches Tab if a pending save disables the focused submit button.
    document.addEventListener('keydown', state.onKeydown)
    document.addEventListener('focusin', state.onFocusin)
    queueMicrotask(() => {
      if (el.isConnected) focusStart(el)
    })
  },
  updated(el, binding) {
    if (el[stateKey]) el[stateKey].options = binding.value
  },
  unmounted(el) {
    const state = el[stateKey]
    if (!state) return
    document.removeEventListener('keydown', state.onKeydown)
    document.removeEventListener('focusin', state.onFocusin)
    delete el[stateKey]
    queueMicrotask(() => {
      if (state.previous?.isConnected && !document.querySelector('[role="dialog"][aria-modal="true"]')) {
        state.previous.focus({ preventScroll: true })
      }
    })
  },
}
