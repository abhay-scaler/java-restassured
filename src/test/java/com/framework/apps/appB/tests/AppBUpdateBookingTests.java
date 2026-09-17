package com.framework.apps.appB.tests;

import com.framework.apps.appB.api.AuthApi;
import com.framework.apps.appB.api.BookingApi;
import com.framework.apps.appB.models.request.AuthRequest;
import com.framework.apps.appB.models.request.BookingDates;
import com.framework.apps.appB.models.request.CreateBookingRequest;
import com.framework.apps.appB.models.request.PartialBookingRequest;
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

@Epic("Restful Booker API")
@Feature("Update Booking")
public class AppBUpdateBookingTests extends BaseTest {

private BookingApi bookingApi() {
    return new BookingApi(client());
}

private int createBooking(BookingApi api, String firstname) {
    CreateBookingRequest requestBody = CreateBookingRequest.builder()
            .firstname(firstname)
            .lastname("Original")
            .totalprice(200)
            .depositpaid(true)
            .bookingdates(BookingDates.builder()
                    .checkin("2026-04-01")
                    .checkout("2026-04-05")
                    .build())
            .build();

    return api.createBooking(requestBody)
            .as(BookingIdResponse.class)
            .getBookingid();
}

@Test(groups = {"smoke", "regression"},
        description = "PUT /booking/{id} succeeds using only the config-driven Basic auth already on the ambient client")
@Severity(SeverityLevel.CRITICAL)
@Description("Proves the existing AuthProvider/AuthType.BASIC mechanism is sufficient on its own for Restful " +
        "Booker's protected endpoints, confirmed against the live API - no App B-specific auth code involved.")
public void testUpdateBookingPutWithConfigDrivenBasicAuth() {
    int bookingId = createBooking(bookingApi(), "BasicAuthUpdate");

    UpdateBookingRequest updateRequest = UpdateBookingRequest.builder()
            .firstname("BasicAuthUpdated")
            .lastname("Original")
            .totalprice(250)
            .depositpaid(false)
            .bookingdates(BookingDates.builder()
                    .checkin("2026-04-02")
                    .checkout("2026-04-06")
                    .build())
            .build();

    Response response = bookingApi().updateBooking(bookingId, updateRequest);

    ResponseValidator.of(response).assertStatusCode(200);

    BookingResponse body = response.as(BookingResponse.class);
    assertThat(body.getFirstname()).isEqualTo("BasicAuthUpdated");
    assertThat(body.getTotalprice()).isEqualTo(250);
}

@Test(groups = {"regression"},
        description = "PUT /booking/{id} succeeds using an explicit token cookie obtained from Restful Booker's own /auth endpoint")
@Severity(SeverityLevel.CRITICAL)
@Description("Demonstrates App B's own token flow (AuthApi + RestClient.cookie()) authorizing a write " +
        "independently of the config-driven Basic auth - an unauthenticated RestClient is used here so only " +
        "the token can be responsible for success.")
public void testUpdateBookingPutWithExplicitTokenCookie() {
    RestClient unauthenticatedClient =
            new RestClient(RequestSpecFactory.createWithoutAuth());

    BookingApi bookingApi = new BookingApi(unauthenticatedClient);

    String token = new AuthApi(unauthenticatedClient)
            .createToken(
                    AuthRequest.builder()
                            .username("admin")
                            .password("password123")
                            .build()
            );

    int bookingId = createBooking(bookingApi, "TokenAuthUpdate");

    UpdateBookingRequest updateRequest = UpdateBookingRequest.builder()
            .firstname("TokenAuthUpdated")
            .lastname("Original")
            .totalprice(275)
            .depositpaid(false)
            .bookingdates(BookingDates.builder()
                    .checkin("2026-04-03")
                    .checkout("2026-04-07")
                    .build())
            .build();

    Response response = bookingApi.updateBooking(
            bookingId,
            token,
            updateRequest
    );

    ResponseValidator.of(response).assertStatusCode(200);

    BookingResponse body = response.as(BookingResponse.class);
    assertThat(body.getFirstname()).isEqualTo("TokenAuthUpdated");
    assertThat(body.getTotalprice()).isEqualTo(275);
}

@Test(groups = {"regression"},
        description = "PATCH /booking/{id} partially updates only the submitted field")
@Severity(SeverityLevel.NORMAL)
public void testPartialUpdateBookingPatchSuccess() {
    int bookingId = createBooking(bookingApi(), "PatchTest");

    PartialBookingRequest partialRequest = PartialBookingRequest.builder()
            .totalprice(999)
            .build();

    Response response = bookingApi()
            .partialUpdateBooking(bookingId, partialRequest);

    ResponseValidator.of(response).assertStatusCode(200);

    BookingResponse body = response.as(BookingResponse.class);
    assertThat(body.getTotalprice()).isEqualTo(999);
    assertThat(body.getFirstname()).isEqualTo("PatchTest");
}

@Test(groups = {"regression", "negative"},
        description = "PUT /booking/{id} for a non-existent id returns 405 (Restful Booker's real behavior, " +
                "not 404 - confirmed against the live API before writing this test)")
@Severity(SeverityLevel.MINOR)
public void testUpdateNonExistentBookingReturns405() {
    UpdateBookingRequest updateRequest = UpdateBookingRequest.builder()
            .firstname("Ghost")
            .lastname("Booking")
            .totalprice(1)
            .depositpaid(true)
            .bookingdates(BookingDates.builder()
                    .checkin("2026-01-01")
                    .checkout("2026-01-02")
                    .build())
            .build();

    Response response = bookingApi()
            .updateBooking(999_999_999, updateRequest);

    ResponseValidator.of(response).assertStatusCode(405);
}

@Test(groups = {"regression", "negative"},
        description = "PUT /booking/{id} without any authentication returns 403")
@Severity(SeverityLevel.NORMAL)
@Description("Mirrors AppBDeleteBookingTests.testDeleteBookingWithoutAuthReturnsForbidden's established " +
        "pattern for the update path, which had no equivalent for PUT. Confirmed against the live API before " +
        "writing this test: an unauthenticated PUT is rejected with 403 and the booking's fields are left " +
        "unchanged.")
public void testUpdateBookingWithoutAuthReturnsForbidden() {
    RestClient unauthenticatedClient =
            new RestClient(RequestSpecFactory.createWithoutAuth());

    BookingApi unauthenticatedBookingApi =
            new BookingApi(unauthenticatedClient);

    int bookingId = createBooking(
            unauthenticatedBookingApi,
            "Unauthorized"
    );

    UpdateBookingRequest updateRequest = UpdateBookingRequest.builder()
            .firstname("ShouldNotApply")
            .lastname("Original")
            .totalprice(1)
            .depositpaid(false)
            .bookingdates(BookingDates.builder()
                    .checkin("2026-04-01")
                    .checkout("2026-04-05")
                    .build())
            .build();

    Response response = unauthenticatedBookingApi
            .updateBooking(bookingId, updateRequest);

    ResponseValidator.of(response).assertStatusCode(403);
}

@Test(groups = {"regression", "negative"},
        description = "PATCH /booking/{id} without any authentication returns 403")
@Severity(SeverityLevel.NORMAL)
@Description("PATCH had a success case (testPartialUpdateBookingPatchSuccess) but no negative coverage at " +
        "all. Confirmed against the live API before writing this test: an unauthenticated PATCH is rejected " +
        "with 403, the same as PUT and DELETE on this same resource.")
public void testPartialUpdateBookingWithoutAuthReturnsForbidden() {
    RestClient unauthenticatedClient =
            new RestClient(RequestSpecFactory.createWithoutAuth());

    BookingApi unauthenticatedBookingApi =
            new BookingApi(unauthenticatedClient);

    int bookingId = createBooking(
            unauthenticatedBookingApi,
            "Unauthorized"
    );

    PartialBookingRequest partialRequest = PartialBookingRequest.builder()
            .totalprice(1)
            .build();

    Response response = unauthenticatedBookingApi
            .partialUpdateBooking(bookingId, partialRequest);

    ResponseValidator.of(response).assertStatusCode(403);
}


}