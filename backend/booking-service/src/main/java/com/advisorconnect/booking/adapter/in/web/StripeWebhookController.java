package com.advisorconnect.booking.adapter.in.web;

import com.advisorconnect.booking.application.BookingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Stripe's callback into the service — the only thing that may move a booking out of
 * {@code PENDING}.
 *
 * <p>Unauthenticated by necessity: Stripe calls this server-to-server and will never carry a
 * JWT, so the route is {@code permitAll()} in {@code SecurityConfig} and listed as a public
 * route in the gateway's {@code JwtAuthenticationFilter}. Its actual authentication is the
 * {@code Stripe-Signature} header — an HMAC-SHA256 over {@code "{timestamp}.{payload}"} keyed
 * with the endpoint's webhook secret. Nothing here runs before that signature verifies.
 *
 * <p>The body is taken as a raw {@code String} rather than a parsed DTO on purpose: the HMAC is
 * computed over the exact bytes Stripe sent, and re-serialising a deserialised object would
 * change them and break verification.
 */
@RestController
@RequestMapping("/bookings/webhooks")
@Slf4j
public class StripeWebhookController {

    private static final String PAYMENT_SUCCEEDED = "payment_intent.succeeded";
    private static final String PAYMENT_FAILED = "payment_intent.payment_failed";

    /** Only ever used to read one field out of an already-verified payload. */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final BookingService bookingService;
    private final String webhookSecret;

    public StripeWebhookController(BookingService bookingService,
                                   @Value("${stripe.webhook-secret}") String webhookSecret) {
        this.bookingService = bookingService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/stripe")
    public ResponseEntity<String> handle(@RequestBody String payload,
                                         @RequestHeader("Stripe-Signature") String sigHeader) {
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            // Either a forgery or a misconfigured secret. 400 tells Stripe not to keep retrying
            // a payload we will never accept, and nothing downstream is touched.
            log.warn("Rejected Stripe webhook with invalid signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        String type = event.getType();
        Optional<String> paymentIntentId = paymentIntentIdOf(event);

        if (paymentIntentId.isEmpty()) {
            // Verified as genuinely from Stripe, but not shaped like a payment intent event.
            // Acknowledge it so Stripe stops redelivering.
            log.info("Stripe webhook {} (id={}) carried no payment intent id — acknowledged, no action",
                    type, event.getId());
            return ResponseEntity.ok("Ignored");
        }

        switch (type) {
            case PAYMENT_SUCCEEDED -> {
                log.info("Stripe webhook {}: confirming paymentIntentId={}", type, paymentIntentId.get());
                bookingService.confirmBooking(paymentIntentId.get());
            }
            case PAYMENT_FAILED -> {
                log.info("Stripe webhook {}: failing paymentIntentId={}", type, paymentIntentId.get());
                bookingService.failBooking(paymentIntentId.get());
            }
            default ->
                // Stripe endpoints often receive more event types than they subscribe to.
                // Anything unrecognised is acknowledged rather than retried forever.
                    log.debug("Ignoring unhandled Stripe event type={}", type);
        }

        return ResponseEntity.ok("Processed");
    }

    /**
     * Pulls the payment intent id out of a verified event.
     *
     * <p>Tries the SDK's typed deserialiser first, then falls back to reading {@code id} out of
     * the raw data object JSON. The fallback is not paranoia: {@code getObject()} returns empty
     * whenever the event's {@code api_version} differs from the version this SDK release was
     * generated against, which happens routinely as Stripe accounts are upgraded. Losing
     * payment confirmations to an API-version drift would be far worse than parsing one field
     * by hand.
     */
    private static Optional<String> paymentIntentIdOf(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();

        Optional<StripeObject> typed = deserializer.getObject();
        if (typed.isPresent() && typed.get() instanceof PaymentIntent intent && intent.getId() != null) {
            return Optional.of(intent.getId());
        }

        String rawJson = deserializer.getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode id = JSON.readTree(rawJson).path("id");
            return id.isTextual() ? Optional.of(id.asText()) : Optional.empty();
        } catch (Exception e) {
            log.warn("Could not read an id from Stripe event {} data object: {}",
                    event.getId(), e.getMessage());
            return Optional.empty();
        }
    }
}
