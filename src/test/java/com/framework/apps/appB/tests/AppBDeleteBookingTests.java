package com.framework.apps.appB.tests;

import com.framework.apps.appB.api.BookingApi;
import com.framework.apps.appB.models.request.BookingDates;
import com.framework.apps.appB.models.request.CreateBookingRequest;
import com.framework.apps.appB.models.response.BookingIdResponse;
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

@Epic("Restful Booker API")
@Feature("Delete Booking")
public class AppBDeleteBookingTests extends BaseTest {

    private BookingApi bookingApi() {
        return new BookingApi(client());
    }

    private int createBooking(BookingApi api, String firstname) {
        CreateBookingRequest requestBody = CreateBookingRequest.builder()
                .firstname(firstname)
                .lastname("ToDelete")
                .totalprice(50)
                .depositpaid(true)
                .bookingdates(BookingDates.builder().checkin("2026-07-01").checkout("2026-07-03").build())
                .build();
        return api.createBooking(requestBody).as(BookingIdResponse.class).getBookingid();
    }

    @Test(groups = {"smoke", "regression"},
            description = "DELETE /booking/{id} removes a booking; Restful Booker returns 201 (not 204) for a successful delete")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Status confirmed against the live API before writing this test. Confirms the deletion by " +
            "following up with a GET, which must now return 404.")
    public void testDeleteBookingSuccess() {
        int bookingId = createBooking(bookingApi(), "DeleteMe");

        Response deleteResponse = bookingApi().deleteBooking(bookingId);
        ResponseValidator.of(deleteResponse).assertStatusCode(201);

        Response getResponse = bookingApi().getBooking(bookingId);
        ResponseValidator.of(getResponse).assertStatusCode(404);
    }

    @Test(groups = {"regression", "negative"}, description = "DELETE /booking/{id} for a non-existent id returns 405")
    @Severity(SeverityLevel.MINOR)
    public void testDeleteNonExistentBookingReturns405() {
        Response response = bookingApi().deleteBooking(999_999_999);
        ResponseValidator.of(response).assertStatusCode(405);
    }

    @Test(groups = {"regression", "negative"}, description = "DELETE /booking/{id} without any authentication returns 403")
    @Severity(SeverityLevel.NORMAL)
    public void testDeleteBookingWithoutAuthReturnsForbidden() {
        RestClient unauthenticatedClient = new RestClient(RequestSpecFactory.createWithoutAuth());
        BookingApi unauthenticatedBookingApi = new BookingApi(unauthenticatedClient);

        int bookingId = createBooking(unauthenticatedBookingApi, "Unauthorized");

        Response response = unauthenticatedBookingApi.deleteBooking(bookingId);

        ResponseValidator.of(response).assertStatusCode(403);
    }
}
