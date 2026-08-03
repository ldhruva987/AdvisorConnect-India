package com.advisorconnect.booking.domain.port.out;

import com.advisorconnect.booking.domain.model.PaymentOrderResult;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Outbound port for taking money.
 *
 * <p>{@code BookingService} used to fabricate {@code "pi_placeholder_" + randomUUID()} and mark
 * the booking {@code CONFIRMED} on the spot, so every booking looked paid whether or not a card
 * had ever been charged. This port is the seam where a real order is created; confirmation is no
 * longer the caller's decision, it arrives asynchronously via the gateway's webhook.
 */
public interface PaymentGateway {

    /**
     * Reserves a charge and returns the identifier needed to finish it client-side.
     *
     * @param amount   amount in major units (e.g. {@code 500.00} rupees) — the adapter converts
     *                 to the gateway's minor-unit representation (paise)
     * @param currency ISO-4217 code, upper case (e.g. {@code "INR"})
     * @param metadata gateway-side annotations for reconciliation; values must be strings
     * @throws PaymentGatewayException if the gateway rejects or cannot be reached
     */
    PaymentOrderResult createOrder(BigDecimal amount, String currency, Map<String, String> metadata);
}
