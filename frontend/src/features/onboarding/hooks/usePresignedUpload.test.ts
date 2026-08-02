import { describe, expect, it, vi } from 'vitest'
import { http, HttpResponse } from 'msw'
import { getErrorMessage } from '@/lib/getErrorMessage'
import { server } from '@/test/mocks/server'
import { MOCK_STORAGE_ORIGIN, lastStorageUpload } from '@/test/mocks/handlers/upload'
import { act, createQueryWrapper, loginAs, renderHook, waitFor } from '@/test/test-utils'
import { usePresignedUpload, uploadFileToPresignedUrl } from './usePresignedUpload'

describe('usePresignedUpload', () => {
  it('requests a presigned URL and object key for a document', async () => {
    const { result } = renderHook(() => usePresignedUpload(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({
        fileName: 'diploma.pdf',
        mimeType: 'application/pdf',
        docType: 'QUALIFICATION',
      })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.objectKey).toBe('applications/user-1/diploma.pdf')
    expect(result.current.data?.uploadUrl).toContain(MOCK_STORAGE_ORIGIN)
    expect(result.current.data?.uploadUrl).toContain('X-Amz-Signature=')
  })

  it('sends fileName, mimeType and docType', async () => {
    let body: unknown
    server.use(
      http.post('*/api/advisors/apply/upload-url', async ({ request }) => {
        body = await request.json()
        return HttpResponse.json({ uploadUrl: 'https://storage.test/x', objectKey: 'k', expiresAt: 'e' })
      }),
    )

    const { result } = renderHook(() => usePresignedUpload(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ fileName: 'id.png', mimeType: 'image/png', docType: 'ID_PROOF' })
    })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(body).toEqual({ fileName: 'id.png', mimeType: 'image/png', docType: 'ID_PROOF' })
  })

  it('surfaces a rejected file type', async () => {
    server.use(
      http.post('*/api/advisors/apply/upload-url', () =>
        HttpResponse.json({ message: 'Unsupported file type' }, { status: 415 }),
      ),
    )

    const { result } = renderHook(() => usePresignedUpload(), { wrapper: createQueryWrapper() })

    act(() => {
      result.current.mutate({ fileName: 'virus.exe', mimeType: 'application/x-msdownload', docType: 'OTHER' })
    })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(getErrorMessage(result.current.error)).toBe('Unsupported file type')
  })
})

describe('uploadFileToPresignedUrl', () => {
  const file = new File(['pdf-bytes'], 'diploma.pdf', { type: 'application/pdf' })

  it('PUTs the file itself, with its content type', async () => {
    // Asserted on the fetch call rather than on what MSW received: jsdom's
    // `File` does not round-trip through the interceptor's body reader, so
    // reading it back server-side would be testing jsdom, not this function.
    const fetchSpy = vi.spyOn(globalThis, 'fetch')

    const url = `${MOCK_STORAGE_ORIGIN}/bucket/diploma.pdf?sig=abc`
    await uploadFileToPresignedUrl(file, url)

    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const [calledUrl, init] = fetchSpy.mock.calls[0]
    expect(calledUrl).toBe(url)
    expect(init?.method).toBe('PUT')
    expect(init?.body).toBe(file)
    expect(init?.headers).toEqual({ 'Content-Type': 'application/pdf' })

    // And the header does reach storage.
    expect(lastStorageUpload.contentType).toBe('application/pdf')

    fetchSpy.mockRestore()
  })

  it('never attaches the JWT — that would break the presigned signature', async () => {
    // The whole reason this is a raw fetch and not apiClient. S3-compatible
    // storage rejects a request carrying both an Authorization header and a
    // presigned query signature.
    loginAs({ accessToken: 'jwt-token-123' })

    await uploadFileToPresignedUrl(file, `${MOCK_STORAGE_ORIGIN}/bucket/diploma.pdf?sig=abc`)

    expect(lastStorageUpload.authorization).toBeNull()
  })

  it('throws with the status when storage rejects the upload', async () => {
    server.use(
      http.put(`${MOCK_STORAGE_ORIGIN}/*`, () => new HttpResponse(null, { status: 403 })),
    )

    await expect(
      uploadFileToPresignedUrl(file, `${MOCK_STORAGE_ORIGIN}/bucket/diploma.pdf?sig=expired`),
    ).rejects.toThrow('Upload failed (403)')
  })
})
