package com.advisorconnect.booking.adapter.out.payment;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;

/**
 * A one-method seam over Stripe's static SDK entry points.
 *
 * <p>{@code PaymentIntent.create(params)} is a {@code static} call, which cannot be stubbed with
 * plain Mockito. Routing it through this interface — implemented in {@code StripeConfig} as the
 * method reference {@code PaymentIntent::create} — keeps {@link StripePaymentGateway} a pure,
 * trivially unit-testable mapping from domain arguments to Stripe params and back, with no
 * network and no static mocking machinery in the test.
 */
@FunctionalInterface
public interface StripeClient {

    PaymentIntent createPaymentIntent(PaymentIntentCreateParams params) throws StripeException;
}
