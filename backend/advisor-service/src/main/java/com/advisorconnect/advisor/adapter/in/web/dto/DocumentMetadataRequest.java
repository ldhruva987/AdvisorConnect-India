package com.advisorconnect.advisor.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/**
 * One uploaded identity document, as described by the client after it has PUT the file to the
 * pre-signed S3 URL.
 *
 * <p>Deliberately has no {@code uploadedAt}: the server stamps that itself. Everything here is
 * client-asserted and is treated as a display hint for the reviewing admin, not as fact —
 * {@code mimeType} in particular is what the browser guessed, not what the bytes are.
 */
@Data
public class DocumentMetadataRequest {

    @NotBlank
    private String s3Key;

    @NotBlank
    private String fileName;

    @NotNull
    @Positive
    private Long sizeBytes;

    @NotBlank
    private String mimeType;
}
