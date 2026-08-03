package com.advisorconnect.booking.domain.model;

/**
 * What the domain needs back from the payment gateway after reserving a charge.
 *
 * <p>Deliberately not the gateway SDK's own object: this id is persisted on the {@code Booking}
 * (it is what the webhook later matches on) and is also handed straight to the browser so it can
 * open Razorpay Checkout. Unlike Stripe's PaymentIntent — which splits a persisted id from a
 * separate, short-lived client secret — Razorpay's order id is the single value both sides need;
 * there is no secret half to keep out of storage.
 *
 * @param orderId gateway-side id, persisted as {@code Booking.razorpayOrderId} and returned to
 *                the client so it can open Razorpay Checkout with it
 */
public record PaymentOrderResult(String orderId) {
}
