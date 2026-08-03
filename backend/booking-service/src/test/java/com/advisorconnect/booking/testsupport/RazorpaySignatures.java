package com.advisorconnect.booking.testsupport;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Produces genuinely valid {@code X-Razorpay-Signature} headers for tests.
 *
 * <p>The scheme is simpler than Stripe's: no timestamp, no tolerance window — just the raw
 * payload's HMAC-SHA256, keyed with the webhook secret, hex-encoded.
 *
 * <p>Implemented here rather than stubbing {@code Utils.verifyWebhookSignature} out so that the
 * tests exercise Razorpay's real verification code. A test that mocks away signature checking
 * would still pass if the controller forgot to verify at all — which is the one bug that matters
 * most on a route deliberately exposed without authentication.
 */
public final class RazorpaySignatures {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private RazorpaySignatures() {
    }

    /** A header Razorpay's verifier will accept for this exact payload and secret. */
    public static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign test payload", e);
        }
    }
}
