import { create } from 'zustand'

export type ToastVariant = 'success' | 'error'

export interface Toast {
  id: string
  message: string
  variant: ToastVariant
}

interface ToastStore {
  toasts: Toast[]
  /** Queue a toast. Returns its id so a caller can dismiss it early. */
  show: (message: string, variant?: ToastVariant) => string
  dismiss: (id: string) => void
  clear: () => void
}

/**
 * Monotonic counter rather than `crypto.randomUUID()`: ids only need to be
 * unique within a session, and a counter keeps tests deterministic.
 */
let nextId = 0

/**
 * Transient confirmation/error notices.
 *
 * Deliberately holds no timers — auto-dismiss lives in the `Toaster` component
 * so this store stays pure and testable without fake timers. Not persisted:
 * a toast that survived a page reload would be a bug.
 */
export const useToastStore = create<ToastStore>((set) => ({
  toasts: [],

  show: (message, variant = 'success') => {
    const id = `toast-${++nextId}`
    set((s) => ({ toasts: [...s.toasts, { id, message, variant }] }))
    return id
  },

  dismiss: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),

  clear: () => set({ toasts: [] }),
}))

/**
 * Imperative shorthand for non-React call sites (mutation `onSuccess`/`onError`
 * callbacks, interceptors) that can't use a hook.
 */
export const toast = {
  success: (message: string) => useToastStore.getState().show(message, 'success'),
  error: (message: string) => useToastStore.getState().show(message, 'error'),
}
