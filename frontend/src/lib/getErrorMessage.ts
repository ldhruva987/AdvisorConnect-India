import axios from 'axios'

/** Shown when we have nothing better. Never leaks a stack trace or status code. */
export const GENERIC_ERROR_MESSAGE = 'Something went wrong. Please try again.'
const NETWORK_ERROR_MESSAGE = 'Cannot reach the server. Check your connection and try again.'
const TIMEOUT_ERROR_MESSAGE = 'The request took too long. Please try again.'

/**
 * Response-body keys we'll trust as user-facing copy, in priority order.
 * `message` is Spring Boot's default error field; `error` and `detail` cover
 * the other two shapes services in this codebase emit.
 */
const BODY_MESSAGE_KEYS = ['message', 'error', 'detail'] as const

function firstStringField(body: unknown): string | undefined {
  if (!body || typeof body !== 'object') return undefined
  const record = body as Record<string, unknown>
  for (const key of BODY_MESSAGE_KEYS) {
    const value = record[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
  }
  return undefined
}

/**
 * Turn anything thrown by a query/mutation into one sentence safe to show a
 * user. Every hook in `src/features/**` routes its error through this so the
 * copy is consistent and no raw Axios noise ("Request failed with status code
 * 500") ever reaches the UI.
 */
export function getErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    // No response object at all: the request never completed (DNS failure,
    // connection refused, CORS, or a client-side timeout).
    if (!err.response) {
      if (err.code === 'ECONNABORTED' || err.code === 'ETIMEDOUT') return TIMEOUT_ERROR_MESSAGE
      return NETWORK_ERROR_MESSAGE
    }

    const body = err.response.data
    const fromBody = firstStringField(body)
    if (fromBody) return fromBody
    // Some services return a bare string body rather than a JSON envelope.
    if (typeof body === 'string' && body.trim() && !body.trim().startsWith('<')) {
      return body.trim()
    }
    // Deliberately not `err.message` here: for Axios that is always the
    // machine-facing "Request failed with status code 500".
    return GENERIC_ERROR_MESSAGE
  }

  if (err instanceof Error && err.message.trim()) return err.message.trim()
  if (typeof err === 'string' && err.trim()) return err.trim()

  return GENERIC_ERROR_MESSAGE
}
