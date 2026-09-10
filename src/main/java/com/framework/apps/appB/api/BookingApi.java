package com.framework.apps.appB.api;

import com.framework.apps.appB.endpoints.BookingEndpoints;
import com.framework.apps.appB.models.request.CreateBookingRequest;
import com.framework.apps.appB.models.request.PartialBookingRequest;
import com.framework.apps.appB.models.request.UpdateBookingRequest;
import com.framework.clients.RestClient;
import io.restassured.response.Response;

/**
 * Thin wrapper over the shared {@link RestClient} for Restful Booker's
 * booking resource. Justified here (unlike App A, which calls
 * {@code client()} directly) because update/delete need an explicit
 * token attached as a cookie - real, non-trivial per-call logic worth
 * writing once - and because the create-get-update-delete sequence is
 * reused across several test classes.
 *
 * The no-token overloads rely on whatever auth the underlying RestClient
 * already carries (e.g. this framework's existing, unmodified
 * AuthProvider/AuthType.BASIC via config); the token overloads attach an
 * explicit "token" cookie - exactly how Restful Booker's own /auth flow
 * expects it, via RestClient's existing generic cookie() method. Neither
 * path touches AuthProvider.
 */
public class BookingApi {

    private final RestClient client;

    public BookingApi(RestClient client) {
        this.client = client;
    }

    public Response getBookings() {
        return client.get(BookingEndpoints.BOOKINGS);
    }

    public Response getBookings(String firstname) {
        return client.queryParam("firstname", firstname).get(BookingEndpoints.BOOKINGS);
    }

    public Response getBooking(int id) {
        return client.pathParam("id", id).get(BookingEndpoints.BOOKING_BY_ID);
    }

    public Response createBooking(CreateBookingRequest request) {
        return client.post(BookingEndpoints.BOOKINGS, request);
    }

    public Response updateBooking(int id, UpdateBookingRequest request) {
        return client.pathParam("id", id).put(BookingEndpoints.BOOKING_BY_ID, request);
    }

    public Response updateBooking(int id, String token, UpdateBookingRequest request) {
        return client.pathParam("id", id).cookie("token", token).put(BookingEndpoints.BOOKING_BY_ID, request);
    }

    public Response partialUpdateBooking(int id, PartialBookingRequest request) {
        return client.pathParam("id", id).patch(BookingEndpoints.BOOKING_BY_ID, request);
    }

    public Response partialUpdateBooking(int id, String token, PartialBookingRequest request) {
        return client.pathParam("id", id).cookie("token", token).patch(BookingEndpoints.BOOKING_BY_ID, request);
    }

    public Response deleteBooking(int id) {
        return client.pathParam("id", id).delete(BookingEndpoints.BOOKING_BY_ID);
    }

    public Response deleteBooking(int id, String token) {
        return client.pathParam("id", id).cookie("token", token).delete(BookingEndpoints.BOOKING_BY_ID);
    }
}
