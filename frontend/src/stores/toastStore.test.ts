import { describe, expect, it } from 'vitest'
import { toast, useToastStore } from './toastStore'

/** `resetStores()` in src/test/setup.ts clears the queue after every test. */

describe('toastStore', () => {
  it('starts empty', () => {
    expect(useToastStore.getState().toasts).toEqual([])
  })

  it('queues a toast with the given message and variant', () => {
    useToastStore.getState().show('Booking confirmed', 'success')

    const { toasts } = useToastStore.getState()
    expect(toasts).toHaveLength(1)
    expect(toasts[0]).toMatchObject({ message: 'Booking confirmed', variant: 'success' })
    expect(toasts[0].id).toBeTruthy()
  })

  it('defaults to the success variant', () => {
    useToastStore.getState().show('Saved')

    expect(useToastStore.getState().toasts[0].variant).toBe('success')
  })

  it('supports the error variant', () => {
    useToastStore.getState().show('Payment failed', 'error')

    expect(useToastStore.getState().toasts[0].variant).toBe('error')
  })

  it('appends toasts in order and gives each a unique id', () => {
    const { show } = useToastStore.getState()
    show('first', 'success')
    show('second', 'error')
    show('third', 'success')

    const { toasts } = useToastStore.getState()
    expect(toasts.map((t) => t.message)).toEqual(['first', 'second', 'third'])
    expect(new Set(toasts.map((t) => t.id)).size).toBe(3)
  })

  it('returns the id of the toast it queued', () => {
    const id = useToastStore.getState().show('Saved')

    expect(useToastStore.getState().toasts[0].id).toBe(id)
  })

  it('dismisses only the targeted toast', () => {
    const { show } = useToastStore.getState()
    const first = show('first')
    show('second')

    useToastStore.getState().dismiss(first)

    const { toasts } = useToastStore.getState()
    expect(toasts).toHaveLength(1)
    expect(toasts[0].message).toBe('second')
  })

  it('is a no-op when dismissing an unknown id', () => {
    useToastStore.getState().show('still here')

    useToastStore.getState().dismiss('toast-does-not-exist')

    expect(useToastStore.getState().toasts).toHaveLength(1)
  })

  it('clears the whole queue', () => {
    const { show } = useToastStore.getState()
    show('a')
    show('b')

    useToastStore.getState().clear()

    expect(useToastStore.getState().toasts).toEqual([])
  })

  it('replaces the array rather than mutating it, so subscribers re-render', () => {
    const before = useToastStore.getState().toasts
    useToastStore.getState().show('new')

    expect(useToastStore.getState().toasts).not.toBe(before)
    expect(before).toHaveLength(0)
  })

  it('exposes an imperative shorthand for non-React call sites', () => {
    toast.success('Application approved')
    toast.error('Could not reach the server')

    const { toasts } = useToastStore.getState()
    expect(toasts.map((t) => t.variant)).toEqual(['success', 'error'])
    expect(toasts[0].message).toBe('Application approved')
  })
})
