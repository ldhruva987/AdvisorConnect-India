package com.advisorconnect.booking.infrastructure.config;

import com.advisorconnect.booking.adapter.out.payment.StripeClient;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Stripe SDK.
 *
 * <p>{@code stripe.secret-key} has been in {@code application.yml} since the service was created
 * but was never read by any code — the booking flow fabricated payment intent ids instead. This
 * is where it finally reaches the SDK.
 */
@Configuration
@Slf4j
public class StripeConfig {

    /**
     * Stripe's SDK keeps the key in a static field, so this is set once at startup rather than
     * passed per request.
     */
    @Value("${stripe.secret-key}")
    private String secretKey;

    @PostConstruct
    void initialiseStripe() {
        Stripe.apiKey = secretKey;
        log.info("Stripe SDK initialised (key prefix={})", keyPrefix(secretKey));
    }

    /**
     * The production {@link StripeClient}: a direct pass-through to the static SDK call. Tests
     * substitute a mock for this one method instead of mocking a static.
     */
    @Bean
    public StripeClient stripeClient() {
        return PaymentIntent::create;
    }

    /** First few characters only — enough to tell test keys from live ones in a log, no secret. */
    private static String keyPrefix(String key) {
        if (key == null || key.isBlank()) {
            return "<unset>";
        }
        return key.substring(0, Math.min(8, key.length())) + "...";
    }
}
