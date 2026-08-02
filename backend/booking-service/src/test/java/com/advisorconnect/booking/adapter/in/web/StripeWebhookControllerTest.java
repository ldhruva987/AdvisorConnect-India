package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.application.BookingService;
import com.advisorconnect.booking.infrastructure.config.SecurityConfig;
import com.advisorconnect.booking.testsupport.StripeSignatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Stripe webhook end of the booking lifecycle.
 *
 * <p>Signature verification is <em>not</em> mocked. {@code Webhook.constructEvent} runs for real
 * and the test signs its own payloads with {@link StripeSignatures}, so a controller that forgot
 * to verify — or verified against the wrong secret — fails here. On a route that is deliberately
 * {@code permitAll()}, that check is the only thing standing between the open internet and the
 * ability to mark any booking paid.
 *
 * <p>{@code SecurityConfig} is imported rather than disabled, so this also covers the
 * {@code permitAll()} rule added for this path: without it every request below would be a 401.
 */
@WebMvcTest(StripeWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "stripe.webhook-secret=whsec_test_0123456789abcdef")
class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_0123456789abcdef";
    private static final String OTHER_SECRET = "whsec_someone_elses_secret";
    private static final String PAYMENT_INTENT_ID = "pi_3Nx9aB2eZvKYlo2C";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    // ------------------------------------------------------------------------- happy paths

    @Test
    @DisplayName("a validly signed payment_intent.succeeded confirms the booking and returns 200")
    void succeededEventConfirmsTheBooking() throws Exception {
        String payload = event("payment_intent.succeeded", PAYMENT_INTENT_ID);

        postWebhook(payload, StripeSignatures.sign(payload, SECRET))
                .andExpect(status().isOk());

        verify(bookingService).confirmBooking(PAYMENT_INTENT_ID);
    }

    @Test
    @DisplayName("a validly signed payment_intent.payment_failed fails the booking and returns 200")
    void failedEventFailsTheBooking() throws Exception {
        String payload = event("payment_intent.payment_failed", PAYMENT_INTENT_ID);

        postWebhook(payload, StripeSignatures.sign(payload, SECRET))
                .andExpect(status().isOk());

        verify(bookingService).failBooking(PAYMENT_INTENT_ID);
    }

    @Test
    @DisplayName("an unhandled but genuine event type is acknowledged with 200, so Stripe stops retrying")
    void unhandledEventTypesAreAcknowledged() throws Exception {
        String payload = event("payment_intent.created", PAYMENT_INTENT_ID);

        postWebhook(payload, StripeSignatures.sign(payload, SECRET))
                .andExpect(status().isOk());

        verifyNoInteractions(bookingService);
    }

    // ------------------------------------------------------------- signature verification

    @Test
    @DisplayName("a payload signed with the wrong secret is refused with 400 and changes nothing")
    void wrongSecretIsRejected() throws Exception {
        String payload = event("payment_intent.succeeded", PAYMENT_INTENT_ID);

        postWebhook(payload, StripeSignatures.sign(payload, OTHER_SECRET))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("a signature that does not match the body is refused — the body cannot be swapped "
            + "after signing")
    void tamperedPayloadIsRejected() throws Exception {
        String signedPayload = event("payment_intent.succeeded", PAYMENT_INTENT_ID);
        String tamperedPayload = event("payment_intent.succeeded", "pi_attackers_own_intent");

        postWebhook(tamperedPayload, StripeSignatures.sign(signedPayload, SECRET))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("a garbage Stripe-Signature header is refused with 400")
    void malformedSignatureHeaderIsRejected() throws Exception {
        String payload = event("payment_intent.succeeded", PAYMENT_INTENT_ID);

        postWebhook(payload, "not-a-signature")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("a correctly signed but long-expired payload is refused — replays fall outside "
            + "Stripe's tolerance window")
    void staleTimestampIsRejected() throws Exception {
        String payload = event("payment_intent.succeeded", PAYMENT_INTENT_ID);
        long anHourAgo = Instant.now().minusSeconds(3600).getEpochSecond();

        postWebhook(payload, StripeSignatures.sign(payload, SECRET, anHourAgo))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    // ------------------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions postWebhook(
            String payload, String signature) throws Exception {
        return mockMvc.perform(post("/bookings/webhooks/stripe")
                .header("Stripe-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    /**
     * A Stripe event envelope. {@code api_version} matches the version the pinned SDK release was
     * generated against so the typed deserialiser path is the one under test here; the
     * controller's raw-JSON fallback covers the mismatched case.
     */
    private static String event(String type, String paymentIntentId) {
        return """
                {
                  "id": "evt_test_webhook",
                  "object": "event",
                  "api_version": "2023-10-16",
                  "created": 1700000000,
                  "livemode": false,
                  "pending_webhooks": 1,
                  "type": "%s",
                  "data": {
                    "object": {
                      "id": "%s",
                      "object": "payment_intent",
                      "amount": 5000,
                      "currency": "usd",
                      "status": "succeeded"
                    }
                  }
                }
                """.formatted(type, paymentIntentId);
    }
}
