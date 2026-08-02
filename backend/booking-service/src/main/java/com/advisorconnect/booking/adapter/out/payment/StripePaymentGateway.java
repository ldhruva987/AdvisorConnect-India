package com.advisorconnect.booking.adapter.out.payment;

import com.advisorconnect.booking.domain.model.PaymentIntentResult;
import com.advisorconnect.booking.domain.port.out.PaymentGateway;
import com.advisorconnect.booking.domain.port.out.PaymentGatewayException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Stripe-backed {@link PaymentGateway}.
 *
 * <p>Talks to Stripe through the injected {@link StripeClient} rather than calling
 * {@code PaymentIntent.create} statically, so this class is unit-testable without a network or
 * a static mock.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StripePaymentGateway implements PaymentGateway {

    /** Stripe quotes every amount in the currency's minor unit, so dollars are sent as cents. */
    private static final BigDecimal MINOR_UNITS_PER_MAJOR = new BigDecimal("100");

    private final StripeClient stripeClient;

    @Override
    public PaymentIntentResult createPaymentIntent(BigDecimal amount, String currency,
                                                   Map<String, String> metadata) {
        long amountInMinorUnits = toMinorUnits(amount);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInMinorUnits)
                .setCurrency(currency)
                .putAllMetadata(metadata)
                // Without this Stripe requires an explicit payment_method_types list; letting
                // Stripe pick the methods enabled on the account is what the hosted client
                // confirmation flow expects.
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        try {
            PaymentIntent intent = stripeClient.createPaymentIntent(params);
            log.info("Created Stripe PaymentIntent id={} amount={} {}",
                    intent.getId(), amountInMinorUnits, currency);
            return new PaymentIntentResult(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            // The Stripe message can carry request-specific detail; log it, but hand callers a
            // domain exception so nothing upstream has to know about com.stripe.*.
            log.error("Stripe PaymentIntent creation failed: {}", e.getMessage());
            throw new PaymentGatewayException("Could not create payment intent", e);
        }
    }

    /**
     * {@code 50.00} dollars becomes {@code 5000} cents.
     *
     * <p>{@code setScale} before conversion rather than {@code longValue()} on the scaled decimal:
     * an amount with sub-cent precision is a pricing bug, and rounding it explicitly is preferable
     * to silently truncating it.
     */
    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(MINOR_UNITS_PER_MAJOR)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
