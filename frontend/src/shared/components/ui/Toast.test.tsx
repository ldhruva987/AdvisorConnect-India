import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import userEvent from '@testing-library/user-event'
import { useToastStore } from '@/stores/toastStore'
import { render, screen } from '@/test/test-utils'
import { Toaster } from './Toast'

describe('Toaster', () => {
  it('renders nothing while the queue is empty', () => {
    const { container } = render(<Toaster />)

    expect(container).toBeEmptyDOMElement()
  })

  it('renders a queued success toast with pine tokens', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Booking confirmed', 'success')
    })

    const item = screen.getByRole('status')
    expect(item).toHaveTextContent('Booking confirmed')
    expect(item).toHaveClass('bg-pine-100', 'text-pine-600')
  })

  it('renders an error toast with danger tokens and an assertive alert role', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Payment failed', 'error')
    })

    const item = screen.getByRole('alert')
    expect(item).toHaveClass('bg-danger-100', 'text-danger-600')
    expect(item).toHaveAttribute('aria-live', 'assertive')
  })

  it('never uses oxblood, which DESIGN.md reserves for primary CTAs', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Saved', 'success')
      useToastStore.getState().show('Failed', 'error')
    })

    for (const item of [screen.getByRole('status'), screen.getByRole('alert')]) {
      expect(item.className).not.toMatch(/oxblood/)
    }
  })

  it('carries the toast elevation token, since toasts sit outside document flow', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Saved')
    })

    expect(screen.getByRole('status')).toHaveClass('shadow-toast')
  })

  it('stacks multiple toasts', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('first')
      useToastStore.getState().show('second')
    })

    expect(screen.getAllByRole('status')).toHaveLength(2)
  })

  it('dismisses a toast when its close button is clicked', async () => {
    const user = userEvent.setup()
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Dismiss me')
    })

    await user.click(screen.getByRole('button', { name: /dismiss notification/i }))

    expect(screen.queryByText('Dismiss me')).not.toBeInTheDocument()
    expect(useToastStore.getState().toasts).toHaveLength(0)
  })
})

describe('Toaster auto-dismiss', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('removes a toast on its own after the timeout', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Transient')
    })
    expect(screen.getByText('Transient')).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(screen.queryByText('Transient')).not.toBeInTheDocument()
  })

  it('keeps the toast up until the timeout elapses', () => {
    render(<Toaster />)

    act(() => {
      useToastStore.getState().show('Transient')
    })

    act(() => {
      vi.advanceTimersByTime(4000)
    })

    expect(screen.getByText('Transient')).toBeInTheDocument()
  })
})
