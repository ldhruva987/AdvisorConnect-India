/**
 * Thin wrapper over Razorpay's Checkout script.
 *
 * Unlike Stripe, Razorpay has no npm package for the browser side — `checkout.js` is meant to be
 * loaded as a plain `<script>` tag and used via the `window.Razorpay` constructor it defines.
 * This module is the one place that injects the tag and exposes a typed surface over it, so
 * nothing else in the app touches `window` directly.
 */

export interface RazorpayPaymentResponse {
  razorpay_payment_id: string
  razorpay_order_id: string
  razorpay_signature: string
}

export interface RazorpayCheckoutOptions {
  key: string
  order_id: string
  name?: string
  description?: string
  /** Called once the modal collects a successful payment. */
  handler: (response: RazorpayPaymentResponse) => void
  prefill?: { name?: string; email?: string; contact?: string }
  theme?: { color?: string }
  modal?: {
    /** Called when the user closes the modal without paying. */
    ondismiss?: () => void
  }
}

interface RazorpayCheckoutInstance {
  open: () => void
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayCheckoutOptions) => RazorpayCheckoutInstance
  }
}

const CHECKOUT_SCRIPT_SRC = 'https://checkout.razorpay.com/v1/checkout.js'

/** Resolves once the script has loaded; rejects on network failure. Cached across calls. */
let loadPromise: Promise<void> | null = null

export function loadRazorpayCheckout(): Promise<void> {
  if (typeof window !== 'undefined' && window.Razorpay) {
    return Promise.resolve()
  }
  if (loadPromise) {
    return loadPromise
  }

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = CHECKOUT_SCRIPT_SRC
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      // A failed load must be retryable — the network blip that caused it may not recur.
      loadPromise = null
      reject(new Error('Could not load the Razorpay Checkout script.'))
    }
    document.body.appendChild(script)
  })
  return loadPromise
}

/** Opens the Checkout modal. Callers must await {@link loadRazorpayCheckout} first. */
export function openRazorpayCheckout(options: RazorpayCheckoutOptions): RazorpayCheckoutInstance {
  if (!window.Razorpay) {
    throw new Error('Razorpay Checkout has not finished loading.')
  }
  const instance = new window.Razorpay(options)
  instance.open()
  return instance
}
