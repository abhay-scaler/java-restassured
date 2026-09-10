package com.framework.apps.appB.tests;

import com.framework.apps.appB.api.BookingApi;
import com.framework.apps.appB.dataproviders.AppBDataProviders;
import com.framework.apps.appB.endpoints.BookingEndpoints;
import com.framework.apps.appB.models.request.BookingDates;
import com.framework.apps.appB.models.request.CreateBookingRequest;
import com.framework.apps.appB.models.response.BookingIdResponse;
import com.framework.base.BaseTest;
import com.framework.exceptions.ApiException;
import com.framework.validators.ResponseValidator;
import com.framework.validators.SchemaValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Epic("Restful Booker API")
@Feature("Create Booking")
public class AppBCreateBookingTests extends BaseTest {

    private BookingApi bookingApi() {
        return new BookingApi(client());
    }

    @Test(groups = {"smoke", "regression"},
            description = "POST /booking creates a booking and returns a bookingid with the booking payload")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Validates status, bookingid, echoed booking fields, and the booking_id_schema.json contract.")
    public void testCreateBookingSuccess() {
        CreateBookingRequest requestBody = CreateBookingRequest.builder()
                .firstname("Grace")
                .lastname("Hopper")
                .totalprice(300)
                .depositpaid(true)
                .bookingdates(BookingDates.builder().checkin("2026-05-01").checkout("2026-05-07").build())
                .additionalneeds("Late checkout")
                .build();

        Response response = bookingApi().createBooking(requestBody);

        ResponseValidator.of(response).assertStatusCode(200);

        SchemaValidator.validate(response, "appB/booking_id_schema.json");

        BookingIdResponse body = response.as(BookingIdResponse.class);
        assertThat(body.getBookingid()).isPositive();
        assertThat(body.getBooking().getFirstname()).isEqualTo("Grace");
        assertThat(body.getBooking().getLastname()).isEqualTo("Hopper");
        assertThat(body.getBooking().getAdditionalneeds()).isEqualTo("Late checkout");
    }

    @Test(groups = {"regression"},
            dataProvider = "createBookingData",
            dataProviderClass = AppBDataProviders.class,
            description = "Data-driven booking creation across multiple guest/price combinations")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateBookingDataDriven(Map<String, Object> bookingData) {
        CreateBookingRequest requestBody = CreateBookingRequest.builder()
                .firstname((String) bookingData.get("firstname"))
                .lastname((String) bookingData.get("lastname"))
                .totalprice(((Number) bookingData.get("totalprice")).intValue())
                .depositpaid((Boolean) bookingData.get("depositpaid"))
                .bookingdates(BookingDates.builder().checkin("2026-06-01").checkout("2026-06-05").build())
                .build();

        Response response = bookingApi().createBooking(requestBody);

        ResponseValidator.of(response).assertStatusCode(200);

        BookingIdResponse body = response.as(BookingIdResponse.class);
        assertThat(body.getBooking().getFirstname()).isEqualTo(bookingData.get("firstname"));
        assertThat(body.getBooking().getTotalprice()).isEqualTo(((Number) bookingData.get("totalprice")).intValue());
    }

    @Test(groups = {"regression", "negative"},
            description = "POST /booking with a body missing all required fields persistently 500s, so " +
                    "RestClient's 5xx retry exhausts and surfaces an ApiException rather than a response")
    @Severity(SeverityLevel.MINOR)
    @Description("Confirmed against the live API before writing this test. Demonstrates RestClient.execute()'s " +
            "documented behavior: a persistent 5xx after all retries is an execution failure, not a response to assert against.")
    public void testCreateBookingMissingRequiredFieldsThrowsApiException() {
        assertThatThrownBy(() -> client().post(BookingEndpoints.BOOKINGS, "{}"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("500");
    }
}
