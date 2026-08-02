package com.advisorconnect.booking.domain.port.out;

/**
 * A payment could not be reserved.
 *
 * <p>Unchecked, and declared next to {@link PaymentGateway} rather than in the Stripe adapter,
 * so callers can react to a payment failure without importing {@code com.stripe.*}. Stripe's own
 * {@code StripeException} is wrapped into this at the adapter boundary.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
