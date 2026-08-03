package com.advisorconnect.booking.adapter.out.payment;

import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;

/**
 * A one-method seam over Razorpay's {@code OrderClient}.
 *
 * <p>Routing order creation through this interface — implemented in {@code RazorpayConfig} as the
 * method reference {@code client.orders::create} — keeps {@link RazorpayPaymentGateway} a pure,
 * trivially unit-testable mapping from domain arguments to a Razorpay request and back, with no
 * network in the test.
 */
@FunctionalInterface
public interface RazorpayOrderClient {

    Order createOrder(JSONObject params) throws RazorpayException;
}
