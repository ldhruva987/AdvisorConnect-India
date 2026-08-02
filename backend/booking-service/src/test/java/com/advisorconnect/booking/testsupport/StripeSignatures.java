package com.advisorconnect.booking.testsupport;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Produces genuinely valid {@code Stripe-Signature} headers for tests.
 *
 * <p>The scheme is documented and small: the signed payload is {@code "{timestamp}.{body}"},
 * the signature is its HMAC-SHA256 keyed with the endpoint's webhook secret, hex-encoded, and
 * the header is {@code t=<timestamp>,v1=<signature>}.
 *
 * <p>Implemented here rather than stubbing {@code Webhook.constructEvent} out so that the tests
 * exercise Stripe's real verification code. A test that mocks away signature checking would
 * still pass if the controller forgot to verify at all — which is the one bug that matters most
 * on a route deliberately exposed without authentication.
 */
public final class StripeSignatures {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private StripeSignatures() {
    }

    /** A header Stripe's verifier will accept, timestamped now. */
    public static String sign(String payload, String secret) {
        return sign(payload, secret, Instant.now().getEpochSecond());
    }

    /** A header signed at an explicit timestamp, for exercising the tolerance window. */
    public static String sign(String payload, String secret, long timestampSeconds) {
        String signedPayload = timestampSeconds + "." + payload;
        return "t=" + timestampSeconds + ",v1=" + hmacSha256Hex(secret, signedPayload);
    }

    private static String hmacSha256Hex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign test payload", e);
        }
    }
}
