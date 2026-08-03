package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.application.BookingService;
import com.advisorconnect.booking.infrastructure.config.SecurityConfig;
import com.advisorconnect.booking.testsupport.RazorpaySignatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Razorpay webhook end of the booking lifecycle.
 *
 * <p>Signature verification is <em>not</em> mocked. {@code Utils.verifyWebhookSignature} runs for
 * real and the test signs its own payloads with {@link RazorpaySignatures}, so a controller that
 * forgot to verify — or verified against the wrong secret — fails here. On a route that is
 * deliberately {@code permitAll()}, that check is the only thing standing between the open
 * internet and the ability to mark any booking paid.
 *
 * <p>{@code SecurityConfig} is imported rather than disabled, so this also covers the
 * {@code permitAll()} rule added for this path: without it every request below would be a 401.
 */
@WebMvcTest(RazorpayWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "razorpay.webhook-secret=whsec_test_0123456789abcdef")
class RazorpayWebhookControllerTest {

    private static final String SECRET = "whsec_test_0123456789abcdef";
    private static final String OTHER_SECRET = "whsec_someone_elses_secret";
    private static final String ORDER_ID = "order_DESlLckIVRkHWj";
    private static final String PAYMENT_ID = "pay_DESlfW9H8K9uqM";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookingService bookingService;

    // ------------------------------------------------------------------------- happy paths

    @Test
    @DisplayName("a validly signed payment.captured confirms the booking and returns 200")
    void capturedEventConfirmsTheBooking() throws Exception {
        String payload = event("payment.captured", ORDER_ID);

        postWebhook(payload, RazorpaySignatures.sign(payload, SECRET))
                .andExpect(status().isOk());

        verify(bookingService).confirmBooking(ORDER_ID);
    }

    @Test
    @DisplayName("a validly signed payment.failed fails the booking and returns 200")
    void failedEventFailsTheBooking() throws Exception {
        String payload = event("payment.failed", ORDER_ID);

        postWebhook(payload, RazorpaySignatures.sign(payload, SECRET))
                .andExpect(status().isOk());

        verify(bookingService).failBooking(ORDER_ID);
    }

    @Test
    @DisplayName("an unhandled but genuine event type is acknowledged with 200, so Razorpay stops retrying")
    void unhandledEventTypesAreAcknowledged() throws Exception {
        String payload = event("order.paid", ORDER_ID);

        postWebhook(payload, RazorpaySignatures.sign(payload, SECRET))
                .andExpect(status().isOk());

        verifyNoInteractions(bookingService);
    }

    // ------------------------------------------------------------- signature verification

    @Test
    @DisplayName("a payload signed with the wrong secret is refused with 400 and changes nothing")
    void wrongSecretIsRejected() throws Exception {
        String payload = event("payment.captured", ORDER_ID);

        postWebhook(payload, RazorpaySignatures.sign(payload, OTHER_SECRET))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("a signature that does not match the body is refused — the body cannot be swapped "
            + "after signing")
    void tamperedPayloadIsRejected() throws Exception {
        String signedPayload = event("payment.captured", ORDER_ID);
        String tamperedPayload = event("payment.captured", "order_attackers_own_order");

        postWebhook(tamperedPayload, RazorpaySignatures.sign(signedPayload, SECRET))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    @Test
    @DisplayName("a garbage X-Razorpay-Signature header is refused with 400")
    void malformedSignatureHeaderIsRejected() throws Exception {
        String payload = event("payment.captured", ORDER_ID);

        postWebhook(payload, "not-a-signature")
                .andExpect(status().isBadRequest());

        verifyNoInteractions(bookingService);
    }

    // ------------------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions postWebhook(
            String payload, String signature) throws Exception {
        return mockMvc.perform(post("/bookings/webhooks/razorpay")
                .header("X-Razorpay-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    /** A Razorpay webhook envelope, shaped as documented for payment.captured/payment.failed. */
    private static String event(String type, String orderId) {
        return """
                {
                  "entity": "event",
                  "event": "%s",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s",
                        "status": "captured"
                      }
                    }
                  }
                }
                """.formatted(type, PAYMENT_ID, orderId);
    }
}
