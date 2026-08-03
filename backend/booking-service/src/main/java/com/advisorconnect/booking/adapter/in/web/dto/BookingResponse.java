package com.advisorconnect.booking.adapter.in.web.dto;

import com.advisorconnect.booking.domain.model.Booking;

/**
 * What {@code POST /bookings} returns.
 *
 * <p>The endpoint used to return the {@link Booking} entity directly, which was enough while the
 * service pretended payment had already succeeded. A real Razorpay order has to be paid by the
 * browser, and that needs the order id to open Checkout with — which doubles as
 * {@code Booking.razorpayOrderId}, so unlike Stripe's short-lived client secret it is safe to
 * both persist and return here.
 *
 * @param booking       the persisted booking, created in {@code PENDING} until Razorpay's
 *                       webhook confirms the charge
 * @param razorpayOrderId hand straight to Razorpay Checkout as {@code order_id}
 */
public record BookingResponse(Booking booking, String razorpayOrderId) {
}
