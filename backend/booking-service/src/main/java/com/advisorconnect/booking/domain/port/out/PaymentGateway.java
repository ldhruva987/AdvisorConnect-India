package com.advisorconnect.booking.domain.port.out;

import com.advisorconnect.booking.domain.model.PaymentIntentResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Outbound port for taking money.
 *
 * <p>{@code BookingService} used to fabricate {@code "pi_placeholder_" + randomUUID()} and mark
 * the booking {@code CONFIRMED} on the spot, so every booking looked paid whether or not a card
 * had ever been charged. This port is the seam where a real charge is authorised; confirmation
 * is no longer the caller's decision, it arrives asynchronously via the gateway's webhook.
 */
public interface PaymentGateway {

    /**
     * Reserves a charge and returns the identifiers needed to finish it client-side.
     *
     * @param amount   amount in major units (e.g. {@code 50.00} dollars) — the adapter converts
     *                 to the gateway's minor-unit representation
     * @param currency ISO-4217 code, lower case (e.g. {@code "usd"})
     * @param metadata gateway-side annotations for reconciliation; values must be strings
     * @throws PaymentGatewayException if the gateway rejects or cannot be reached
     */
    PaymentIntentResult createPaymentIntent(BigDecimal amount, String currency, Map<String, String> metadata);
}
