package com.advisorconnect.booking.infrastructure.config;

import com.advisorconnect.booking.adapter.out.payment.RazorpayOrderClient;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Razorpay SDK.
 *
 * <p>{@code razorpay.key-id} and {@code razorpay.key-secret} have been in {@code application.yml}
 * since the service was created but were never read by any code — the booking flow fabricated
 * payment intent ids instead. This is where they finally reach the SDK.
 */
@Configuration
@Slf4j
public class RazorpayConfig {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    /**
     * Unlike Stripe's static {@code Stripe.apiKey}, Razorpay's SDK is instance-based: the client
     * carries the credentials itself, so there is nothing to initialise beyond constructing it.
     */
    @Bean
    public RazorpayClient razorpayClient() throws RazorpayException {
        log.info("Razorpay SDK initialised (key prefix={})", keyPrefix(keyId));
        return new RazorpayClient(keyId, keySecret);
    }

    /**
     * The production {@link RazorpayOrderClient}: a direct pass-through to the SDK's order
     * sub-client. Tests substitute a mock for this one method instead of standing up a client.
     */
    @Bean
    public RazorpayOrderClient razorpayOrderClient(RazorpayClient client) {
        return client.orders::create;
    }

    /** First few characters only — enough to tell test keys from live ones in a log, no secret. */
    private static String keyPrefix(String key) {
        if (key == null || key.isBlank()) {
            return "<unset>";
        }
        return key.substring(0, Math.min(8, key.length())) + "...";
    }
}
