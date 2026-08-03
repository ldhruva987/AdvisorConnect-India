package com.advisorconnect.booking.domain.port.out;

/**
 * A payment could not be reserved.
 *
 * <p>Unchecked, and declared next to {@link PaymentGateway} rather than in the Razorpay adapter,
 * so callers can react to a payment failure without importing {@code com.razorpay.*}. Razorpay's
 * own {@code RazorpayException} is wrapped into this at the adapter boundary.
 */
public class PaymentGatewayException extends RuntimeException {

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
