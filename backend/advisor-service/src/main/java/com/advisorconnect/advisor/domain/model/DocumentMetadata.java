package com.advisorconnect.advisor.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * An identity document attached to an onboarding application.
 *
 * <p>Replaces the previous {@code List<String> documentS3Keys}. A bare key told a reviewing
 * admin nothing: not what the file claims to be, not whether it is a 2 KB placeholder or a
 * 40 MB scan, not when it arrived. Reviewers need that to decide whether a submission is even
 * worth opening, and the service needs it to reject obvious junk without a round trip to S3.
 *
 * <p>{@code uploadedAt} is stamped server-side on submission — a client-supplied timestamp on
 * an identity document is not evidence of anything.
 *
 * <p>Never exposed through a public DTO: possession of the key is possession of the document.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
public class DocumentMetadata {

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;
}
