import { useMutation, type UseMutationResult } from '@tanstack/react-query'
import apiClient from '@/lib/axios'
import type { PresignedUploadRequest, PresignedUploadResponse } from '@/types/api'

/**
 * Two-step document upload for advisor onboarding.
 *
 * 1. Ask the gateway for a short-lived presigned URL (authenticated, via
 *    `apiClient`).
 * 2. PUT the bytes straight at object storage (unauthenticated, via raw
 *    `fetch`).
 *
 * Files never pass through the API, which keeps multi-megabyte PDFs off the
 * gateway and out of the service's memory.
 */

/** `POST /advisors/apply/upload-url` — pending backend. */
export async function requestPresignedUpload(
  body: PresignedUploadRequest,
): Promise<PresignedUploadResponse> {
  const { data } = await apiClient.post<PresignedUploadResponse>('/advisors/apply/upload-url', body)
  return data
}

/**
 * PUT a file to a presigned storage URL.
 *
 * Deliberately a plain `fetch`, NOT `apiClient`. Two reasons, both load-bearing:
 *
 *  - The URL is a different origin (MinIO/S3), so `apiClient`'s `/api` baseURL
 *    and 401-refresh-retry interceptor are meaningless or actively harmful
 *    here — a 403 from storage must not trigger a token refresh.
 *  - `apiClient` attaches `Authorization: Bearer …`. S3-compatible storage
 *    rejects a request that carries both an `Authorization` header and a
 *    presigned query signature; the upload would fail with a signature error
 *    that looks nothing like its actual cause.
 *
 * Not a react-query mutation because there is no cache to touch and callers
 * need to await it in a loop over several files.
 */
export async function uploadFileToPresignedUrl(file: File, uploadUrl: string): Promise<void> {
  const response = await fetch(uploadUrl, {
    method: 'PUT',
    body: file,
    // Must match the `mimeType` the presigned URL was signed for.
    headers: { 'Content-Type': file.type },
  })

  if (!response.ok) {
    throw new Error(`Upload failed (${response.status})`)
  }
}

/** Request a presigned URL for one document. */
export function usePresignedUpload(): UseMutationResult<
  PresignedUploadResponse,
  Error,
  PresignedUploadRequest
> {
  return useMutation({ mutationFn: requestPresignedUpload })
}
