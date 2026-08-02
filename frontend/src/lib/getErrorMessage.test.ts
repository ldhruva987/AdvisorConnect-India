import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { GENERIC_ERROR_MESSAGE, getErrorMessage } from './getErrorMessage'

/** Build an AxiosError that `axios.isAxiosError` recognises. */
function axiosErrorWithResponse(status: number, data: unknown): AxiosError {
  const headers = new AxiosHeaders()
  const config = { headers }
  const response = {
    data,
    status,
    statusText: '',
    headers: {},
    config,
  } as unknown as AxiosResponse
  return new AxiosError(
    `Request failed with status code ${status}`,
    String(status),
    config as never,
    {},
    response,
  )
}

function axiosErrorWithoutResponse(code: string): AxiosError {
  const config = { headers: new AxiosHeaders() }
  return new AxiosError('Network Error', code, config as never, {}, undefined)
}

describe('getErrorMessage', () => {
  it('prefers the response body `message` field', () => {
    const err = axiosErrorWithResponse(400, { message: 'Email is already registered' })
    expect(getErrorMessage(err)).toBe('Email is already registered')
  })

  it('falls back to `error` then `detail`', () => {
    expect(getErrorMessage(axiosErrorWithResponse(403, { error: 'Forbidden sector' }))).toBe(
      'Forbidden sector',
    )
    expect(getErrorMessage(axiosErrorWithResponse(422, { detail: 'Slot no longer available' }))).toBe(
      'Slot no longer available',
    )
  })

  it('honours the message > error > detail priority order', () => {
    const err = axiosErrorWithResponse(400, {
      detail: 'third',
      error: 'second',
      message: 'first',
    })
    expect(getErrorMessage(err)).toBe('first')
  })

  it('ignores blank or non-string body fields', () => {
    expect(getErrorMessage(axiosErrorWithResponse(500, { message: '   ' }))).toBe(
      GENERIC_ERROR_MESSAGE,
    )
    expect(getErrorMessage(axiosErrorWithResponse(500, { message: { nested: true } }))).toBe(
      GENERIC_ERROR_MESSAGE,
    )
  })

  it('trims surrounding whitespace from body copy', () => {
    expect(getErrorMessage(axiosErrorWithResponse(400, { message: '  Bad input\n' }))).toBe(
      'Bad input',
    )
  })

  it('accepts a bare string body', () => {
    expect(getErrorMessage(axiosErrorWithResponse(400, 'Booking window closed'))).toBe(
      'Booking window closed',
    )
  })

  it('does not surface an HTML error page as copy', () => {
    const err = axiosErrorWithResponse(502, '<html><body>Bad Gateway</body></html>')
    expect(getErrorMessage(err)).toBe(GENERIC_ERROR_MESSAGE)
  })

  it('returns the generic message for an Axios error with no body', () => {
    expect(getErrorMessage(axiosErrorWithResponse(500, undefined))).toBe(GENERIC_ERROR_MESSAGE)
    expect(getErrorMessage(axiosErrorWithResponse(500, {}))).toBe(GENERIC_ERROR_MESSAGE)
    expect(getErrorMessage(axiosErrorWithResponse(500, null))).toBe(GENERIC_ERROR_MESSAGE)
  })

  it('never leaks the raw Axios status message', () => {
    expect(getErrorMessage(axiosErrorWithResponse(500, {}))).not.toContain('status code')
  })

  it('describes a network failure when there is no response at all', () => {
    const message = getErrorMessage(axiosErrorWithoutResponse('ERR_NETWORK'))
    expect(message).toMatch(/cannot reach the server/i)
  })

  it('describes a timeout distinctly', () => {
    expect(getErrorMessage(axiosErrorWithoutResponse('ECONNABORTED'))).toMatch(/took too long/i)
    expect(getErrorMessage(axiosErrorWithoutResponse('ETIMEDOUT'))).toMatch(/took too long/i)
  })

  it('uses the message of a plain non-Axios Error', () => {
    expect(getErrorMessage(new Error('Stripe.js failed to load'))).toBe('Stripe.js failed to load')
  })

  it('falls back to generic for an Error with an empty message', () => {
    expect(getErrorMessage(new Error(''))).toBe(GENERIC_ERROR_MESSAGE)
  })

  it('uses a raw thrown string', () => {
    expect(getErrorMessage('boom')).toBe('boom')
  })

  it('falls back to generic for unknown thrown values', () => {
    expect(getErrorMessage(undefined)).toBe(GENERIC_ERROR_MESSAGE)
    expect(getErrorMessage(null)).toBe(GENERIC_ERROR_MESSAGE)
    expect(getErrorMessage(42)).toBe(GENERIC_ERROR_MESSAGE)
    expect(getErrorMessage({ unexpected: 'shape' })).toBe(GENERIC_ERROR_MESSAGE)
    expect(getErrorMessage('   ')).toBe(GENERIC_ERROR_MESSAGE)
  })
})
