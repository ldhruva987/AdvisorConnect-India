import '@testing-library/jest-dom'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './mocks/server'
import { resetStores } from './test-utils'

// `onUnhandledRequest: 'error'` is deliberate: any request a test makes that
// isn't explicitly mocked should fail the test, not silently escape to the
// network or hang.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

afterEach(() => {
  // RTL auto-cleans in v14+, but being explicit keeps it independent of the
  // `globals` setting and of RTL's own auto-cleanup detection.
  cleanup()
  server.resetHandlers()
  resetStores()
})

afterAll(() => server.close())
