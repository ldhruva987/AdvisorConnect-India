package com.advisorconnect.booking.domain.model;

public enum BookingStatus {
    /** Created, order open, not yet paid. Razorpay's webhook decides what happens next. */
    PENDING,
    CONFIRMED,
    COMPLETED,
    /** Withdrawn by the user. */
    CANCELLED,
    /**
     * The payment did not go through. Distinct from {@link #CANCELLED}: nobody chose this, and
     * the two need to be told apart for support and reporting.
     */
    FAILED,
    REFUNDED
}
