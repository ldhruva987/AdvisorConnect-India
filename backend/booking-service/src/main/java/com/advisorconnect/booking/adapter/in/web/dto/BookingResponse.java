package com.advisorconnect.booking.adapter.in.web.dto;

import com.advisorconnect.booking.domain.model.Booking;

/**
 * What {@code POST /bookings} returns.
 *
 * <p>The endpoint used to return the {@link Booking} entity directly, which was enough while the
 * service pretended payment had already succeeded. A real PaymentIntent has to be confirmed by
 * the browser, and that needs the client secret — which is deliberately <em>not</em> a column on
 * {@code Booking} (it is single-use and must not be persisted or re-read later), so the response
 * carries it alongside the booking instead.
 *
 * @param booking      the persisted booking, created in {@code PENDING} until Stripe's webhook
 *                     confirms the charge
 * @param clientSecret hand straight to Stripe.js; never stored server-side
 */
public record BookingResponse(Booking booking, String clientSecret) {
}
