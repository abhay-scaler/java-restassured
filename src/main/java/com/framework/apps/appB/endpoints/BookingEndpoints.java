package com.framework.apps.appB.endpoints;

/**
 * Centralized endpoint definitions for Restful Booker's booking resource.
 */
public final class BookingEndpoints {

    private BookingEndpoints() {
    }

    public static final String BOOKINGS = "/booking";
    public static final String BOOKING_BY_ID = "/booking/{id}";
}
