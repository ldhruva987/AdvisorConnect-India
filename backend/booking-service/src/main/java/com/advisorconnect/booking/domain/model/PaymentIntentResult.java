package com.advisorconnect.booking.domain.model;

/**
 * What the domain needs back from a payment gateway after reserving a charge.
 *
 * <p>Deliberately not the gateway SDK's own object: the id is persisted on the {@code Booking}
 * and is what the webhook later matches on, while the client secret is short-lived, must never
 * be stored, and only exists to be handed straight back to the browser so it can confirm the
 * payment. Keeping the pair in a domain record means nothing outside
 * {@code adapter.out.payment} has to import Stripe types.
 *
 * @param paymentIntentId gateway-side id, persisted as {@code Booking.stripePaymentIntentId}
 * @param clientSecret    one-shot secret for the front end; never persisted
 */
public record PaymentIntentResult(String paymentIntentId, String clientSecret) {
}
