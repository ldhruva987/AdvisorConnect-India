package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.application.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's callback into the service — the only thing that may move a booking out of
 * {@code PENDING}.
 *
 * <p>Unauthenticated by necessity: Razorpay calls this server-to-server and will never carry a
 * JWT, so the route is {@code permitAll()} in {@code SecurityConfig} and listed as a public
 * route in the gateway's {@code JwtAuthenticationFilter}. Its actual authentication is the
 * {@code X-Razorpay-Signature} header — an HMAC-SHA256 over the exact request body, keyed with
 * the webhook's signing secret. Nothing here runs before that signature verifies.
 *
 * <p>The body is taken as a raw {@code String} rather than a parsed DTO on purpose: the HMAC is
 * computed over the exact bytes Razorpay sent, and re-serialising a deserialised object would
 * change them and break verification.
 */
@RestController
@RequestMapping("/bookings/webhooks")
@Slf4j
public class RazorpayWebhookController {

    private static final String PAYMENT_CAPTURED = "payment.captured";
    private static final String PAYMENT_FAILED = "payment.failed";

    /** Only ever used to read a couple of fields out of an already-verified payload. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BookingService bookingService;
    private final String webhookSecret;

    public RazorpayWebhookController(BookingService bookingService,
                                     @Value("${razorpay.webhook-secret}") String webhookSecret) {
        this.bookingService = bookingService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader("X-Razorpay-Signature") String signature) {
        boolean verified;
        try {
            verified = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
        } catch (RazorpayException e) {
            // Either a forgery, a malformed header, or a misconfigured secret. 400 tells Razorpay
            // not to keep retrying a payload we will never accept, and nothing downstream is
            // touched.
            log.warn("Rejected Razorpay webhook — signature could not be verified: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }
        if (!verified) {
            log.warn("Rejected Razorpay webhook with invalid signature");
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        JsonNode root;
        try {
            root = JSON.readTree(payload);
        } catch (Exception e) {
            // Verified as genuinely signed with our secret, but not valid JSON — should not
            // happen against the real Razorpay, but a malformed body must not blow up as a 500.
            log.warn("Verified Razorpay webhook body was not valid JSON: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Malformed payload");
        }

        String type = root.path("event").asText(null);
        String orderId = root.path("payload").path("payment").path("entity").path("order_id").asText(null);

        if (orderId == null || orderId.isBlank()) {
            // Verified as genuinely from Razorpay, but not shaped like a payment event.
            // Acknowledge it so Razorpay stops redelivering.
            log.info("Razorpay webhook {} carried no order id — acknowledged, no action", type);
            return ResponseEntity.ok("Ignored");
        }

        switch (type) {
            case PAYMENT_CAPTURED -> {
                log.info("Razorpay webhook {}: confirming orderId={}", type, orderId);
                bookingService.confirmBooking(orderId);
            }
            case PAYMENT_FAILED -> {
                log.info("Razorpay webhook {}: failing orderId={}", type, orderId);
                bookingService.failBooking(orderId);
            }
            default ->
                // Razorpay endpoints often receive more event types than they subscribe to.
                // Anything unrecognised is acknowledged rather than retried forever.
                    log.debug("Ignoring unhandled Razorpay event type={}", type);
        }

        return ResponseEntity.ok("Processed");
    }
}
