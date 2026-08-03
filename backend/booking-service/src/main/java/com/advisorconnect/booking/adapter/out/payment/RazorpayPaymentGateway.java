package com.advisorconnect.booking.adapter.out.payment;

import com.advisorconnect.booking.domain.model.PaymentOrderResult;
import com.advisorconnect.booking.domain.port.out.PaymentGateway;
import com.advisorconnect.booking.domain.port.out.PaymentGatewayException;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

/**
 * Razorpay-backed {@link PaymentGateway}.
 *
 * <p>Talks to Razorpay through the injected {@link RazorpayOrderClient} rather than constructing
 * an {@code OrderClient} directly, so this class is unit-testable without a network call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentGateway implements PaymentGateway {

    /** Razorpay quotes every INR amount in paise, so rupees are sent as whole paise. */
    private static final BigDecimal MINOR_UNITS_PER_MAJOR = new BigDecimal("100");

    /**
     * Razorpay auto-captures the charge on authorisation rather than leaving it in an
     * authorised-but-uncaptured state — there is no separate "capture later" step in this flow,
     * and a booking that never gets captured is money the advisor never receives for a session
     * the calendar still shows as held.
     */
    private static final int AUTO_CAPTURE = 1;

    private final RazorpayOrderClient razorpayOrderClient;

    @Override
    public PaymentOrderResult createOrder(BigDecimal amount, String currency, Map<String, String> metadata) {
        long amountInMinorUnits = toMinorUnits(amount);

        JSONObject params = new JSONObject();
        params.put("amount", amountInMinorUnits);
        params.put("currency", currency);
        // Razorpay requires a receipt identifier; the booking row does not exist yet (its id is
        // generated on save), so a fresh UUID stands in — it only has to be unique per request.
        params.put("receipt", "booking-" + UUID.randomUUID());
        params.put("payment_capture", AUTO_CAPTURE);
        params.put("notes", new JSONObject(metadata));

        try {
            Order order = razorpayOrderClient.createOrder(params);
            String orderId = order.get("id");
            log.info("Created Razorpay order id={} amount={} {}", orderId, amountInMinorUnits, currency);
            return new PaymentOrderResult(orderId);
        } catch (RazorpayException e) {
            // The Razorpay message can carry request-specific detail; log it, but hand callers a
            // domain exception so nothing upstream has to know about com.razorpay.*.
            log.error("Razorpay order creation failed: {}", e.getMessage());
            throw new PaymentGatewayException("Could not create payment order", e);
        }
    }

    /**
     * {@code 500.00} rupees becomes {@code 50000} paise.
     *
     * <p>{@code setScale} before conversion rather than {@code longValue()} on the scaled decimal:
     * an amount with sub-paise precision is a pricing bug, and rounding it explicitly is
     * preferable to silently truncating it.
     */
    private static long toMinorUnits(BigDecimal amount) {
        return amount.multiply(MINOR_UNITS_PER_MAJOR)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
