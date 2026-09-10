package com.framework.apps.appB.tests;

import com.framework.apps.appB.api.BookingApi;
import com.framework.apps.appB.models.request.BookingDates;
import com.framework.apps.appB.models.request.CreateBookingRequest;
import com.framework.apps.appB.models.response.BookingIdResponse;
import com.framework.apps.appB.models.response.BookingResponse;
import com.framework.base.BaseTest;
import com.framework.utils.RandomDataGenerator;
import com.framework.validators.ResponseValidator;
import com.framework.validators.SchemaValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Epic("Restful Booker API")
@Feature("Get Booking")
public class AppBGetBookingTests extends BaseTest {

    private BookingApi bookingApi() {
        return new BookingApi(client());
    }

    @Test(groups = {"regression"},
            description = "GET /booking filtered by firstname returns exactly the booking just created")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Filters by a unique firstname rather than asserting the whole list is non-empty, since /booking " +
            "is a large shared public dataset other users of this demo API are concurrently mutating.")
    public void testGetBookingsListSuccess() {
        String uniqueFirstname = "Claude" + RandomDataGenerator.alphanumeric(8);

        CreateBookingRequest createRequest = CreateBookingRequest.builder()
                .firstname(uniqueFirstname)
                .lastname("ListTest")
                .totalprice(100)
                .depositpaid(true)
                .bookingdates(BookingDates.builder().checkin("2026-01-01").checkout("2026-01-05").build())
                .build();

        int bookingId = bookingApi().createBooking(createRequest).as(BookingIdResponse.class).getBookingid();

        Response listResponse = bookingApi().getBookings(uniqueFirstname);

        ResponseValidator.of(listResponse).assertStatusCode(200);

        List<Map<String, Object>> matches = listResponse.jsonPath().getList("$");
        assertThat(matches)
                .as("Filtering /booking by a just-created unique firstname should return exactly that one booking")
                .hasSize(1);
        assertThat(((Number) matches.get(0).get("bookingid")).intValue()).isEqualTo(bookingId);
    }

    @Test(groups = {"smoke", "regression"},
            description = "GET /booking/{id} for a freshly created booking returns 200 with matching fields")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Creates its own booking rather than relying on a shared fixture id, since Restful Booker " +
            "(unlike reqres.in) genuinely persists writes on a shared public dataset.")
    public void testGetBookingByIdSuccess() {
        CreateBookingRequest createRequest = CreateBookingRequest.builder()
                .firstname("Ada")
                .lastname("Lovelace")
                .totalprice(150)
                .depositpaid(true)
                .bookingdates(BookingDates.builder().checkin("2026-03-01").checkout("2026-03-10").build())
                .additionalneeds("Breakfast")
                .build();

        int bookingId = bookingApi().createBooking(createRequest).as(BookingIdResponse.class).getBookingid();

        Response response = bookingApi().getBooking(bookingId);

        ResponseValidator.of(response).assertStatusCode(200);

        SchemaValidator.validate(response, "appB/booking_schema.json");

        BookingResponse body = response.as(BookingResponse.class);
        assertThat(body.getFirstname()).isEqualTo("Ada");
        assertThat(body.getLastname()).isEqualTo("Lovelace");
        assertThat(body.getAdditionalneeds()).isEqualTo("Breakfast");
    }

    @Test(groups = {"regression", "negative"}, description = "GET /booking/{id} for a non-existent id returns 404")
    @Severity(SeverityLevel.NORMAL)
    public void testGetBookingNotFound() {
        Response response = bookingApi().getBooking(999_999_999);
        ResponseValidator.of(response).assertStatusCode(404);
    }
}
