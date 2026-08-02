import { http, HttpResponse } from 'msw'
import type { PresignedUploadRequest, PresignedUploadResponse } from '@/types/api'

/**
 * Presigned document upload — pending backend.
 *
 * Two distinct hops, deliberately mocked at two distinct origins:
 *
 *  1. `POST /advisors/apply/upload-url` through the gateway (authenticated,
 *     goes via `apiClient`), which mints a short-lived presigned URL;
 *  2. a raw `PUT` straight at object storage on a *different* origin, which
 *     must NOT carry our JWT — an `Authorization` header alongside the
 *     presigned query signature is what makes S3/MinIO reject the upload.
 *
 * The storage handler echoes back what it received so tests can assert that
 * second property directly rather than trusting the implementation.
 */

/** Origin the fixture presigned URLs point at. Not the API gateway. */
export const MOCK_STORAGE_ORIGIN = 'https://storage.test'

export const MOCK_OBJECT_KEY = 'applications/user-1/id-proof.pdf'

export const MOCK_PRESIGNED_RESPONSE: PresignedUploadResponse = {
  uploadUrl: `${MOCK_STORAGE_ORIGIN}/advisorconnect/${MOCK_OBJECT_KEY}?X-Amz-Signature=deadbeef`,
  objectKey: MOCK_OBJECT_KEY,
  expiresAt: '2026-08-01T13:00:00Z',
}

/** Populated by the storage handler on each PUT, for assertions. */
export const lastStorageUpload: {
  authorization: string | null
  contentType: string | null
  body: string | null
} = { authorization: null, contentType: null, body: null }

export const uploadHandlers = [
  http.post('*/api/advisors/apply/upload-url', async ({ request }) => {
    const body = (await request.json()) as PresignedUploadRequest
    return HttpResponse.json({
      ...MOCK_PRESIGNED_RESPONSE,
      objectKey: `applications/user-1/${body.fileName}`,
      uploadUrl: `${MOCK_STORAGE_ORIGIN}/advisorconnect/applications/user-1/${body.fileName}?X-Amz-Signature=deadbeef`,
    } satisfies PresignedUploadResponse)
  }),

  http.put(`${MOCK_STORAGE_ORIGIN}/*`, async ({ request }) => {
    lastStorageUpload.authorization = request.headers.get('authorization')
    lastStorageUpload.contentType = request.headers.get('content-type')
    lastStorageUpload.body = await request.text()
    return new HttpResponse(null, { status: 200 })
  }),
]
