package com.framework.apps.appB.tests;

import com.framework.apps.appB.api.AuthApi;
import com.framework.apps.appB.api.BookingApi;
import com.framework.apps.appB.models.request.AuthRequest;
import com.framework.apps.appB.models.request.BookingDates;
import com.framework.apps.appB.models.request.CreateBookingRequest;
import com.framework.apps.appB.models.request.UpdateBookingRequest;
import com.framework.apps.appB.models.response.BookingIdResponse;
import com.framework.apps.appB.models.response.BookingResponse;
import com.framework.base.BaseTest;
import com.framework.builders.RequestSpecFactory;
import com.framework.clients.RestClient;
import com.framework.validators.ResponseValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One end-to-end lifecycle test through the full App B stack:
 * Test -> AuthApi/BookingApi -> RestClient -> RequestSpecFactory ->
 * AuthProvider -> REST Assured. Deliberately kept in its own class,
 * separate from the individual CRUD test classes above, so a break here
 * never masks - or is masked by - an independent CRUD test's own
 * diagnosis; the CRUD suites stay green/red on their own merits.
 */
@Epic("Restful Booker API")
@Feature("Booking Lifecycle")
public class AppBBookingLifecycleTests extends BaseTest {

    @Test(groups = {"regression"},
            description = "authenticate -> create -> get -> update -> get+verify -> delete, using one token and one client throughout")
    @Severity(SeverityLevel.CRITICAL)
    @Description("The token lives only in this method's local variable - never static, never shared across tests " +
            "or threads - exactly the per-test scope this framework's existing ThreadLocal design already relies on elsewhere.")
    public void testFullBookingLifecycle() {
        RestClient restClient = new RestClient(RequestSpecFactory.createWithoutAuth());
        AuthApi authApi = new AuthApi(restClient);
        BookingApi bookingApi = new BookingApi(restClient);

        String token = authApi.createToken(
                AuthRequest.builder().username("admin").password("password123").build());
        assertThat(token).isNotBlank();

        int bookingId = bookingApi.createBooking(
                CreateBookingRequest.builder()
                        .firstname("Lifecycle")
                        .lastname("Test")
                        .totalprice(400)
                        .depositpaid(true)
                        .bookingdates(BookingDates.builder().checkin("2026-08-01").checkout("2026-08-10").build())
                        .build()
        ).as(BookingIdResponse.class).getBookingid();

        Response afterCreate = bookingApi.getBooking(bookingId);
        ResponseValidator.of(afterCreate).assertStatusCode(200);
        assertThat(afterCreate.as(BookingResponse.class).getFirstname()).isEqualTo("Lifecycle");

        Response updateResponse = bookingApi.updateBooking(bookingId, token,
                UpdateBookingRequest.builder()
                        .firstname("LifecycleUpdated")
                        .lastname("Test")
                        .totalprice(450)
                        .depositpaid(false)
                        .bookingdates(BookingDates.builder().checkin("2026-08-02").checkout("2026-08-11").build())
                        .build());
        ResponseValidator.of(updateResponse).assertStatusCode(200);

        Response afterUpdate = bookingApi.getBooking(bookingId);
        ResponseValidator.of(afterUpdate).assertStatusCode(200);
        BookingResponse updatedBody = afterUpdate.as(BookingResponse.class);
        assertThat(updatedBody.getFirstname()).isEqualTo("LifecycleUpdated");
        assertThat(updatedBody.getTotalprice()).isEqualTo(450);

        Response deleteResponse = bookingApi.deleteBooking(bookingId, token);
        ResponseValidator.of(deleteResponse).assertStatusCode(201);

        Response afterDelete = bookingApi.getBooking(bookingId);
        ResponseValidator.of(afterDelete).assertStatusCode(404);
    }
}
