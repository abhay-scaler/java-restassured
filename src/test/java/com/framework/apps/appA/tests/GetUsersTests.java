package com.framework.apps.appA.tests;

import com.framework.apps.appA.endpoints.UserEndpoints;
import com.framework.apps.appA.models.response.SingleUserResponse;
import com.framework.apps.appA.models.response.UserListResponse;
import com.framework.base.BaseTest;
import com.framework.validators.ResponseValidator;
import com.framework.validators.SchemaValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

@Epic("Users API")
@Feature("Get Users")
public class GetUsersTests extends BaseTest {

    @Test(
            groups = {"smoke", "regression"},
            description = "GET paginated user list returns 200 and valid schema"
    )
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verifies the users list endpoint returns a correctly shaped, paginated response.")
    public void testGetUserListSuccess() {

        Response response = client()
                .queryParam("page", 2)
                .get(UserEndpoints.USERS);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertResponseTimeBelow(5000)
                .assertContentType("application/json")
                .assertJsonValue("page", equalTo(2))
                .assertArrayNotEmpty("data");

        SchemaValidator.validate(response, "appA/user_list_schema.json");

        UserListResponse body = response.as(UserListResponse.class);

        assertThat(body.getData()).allSatisfy(user -> {
            assertThat(user.getId()).isPositive();
            assertThat(user.getEmail()).contains("@");
        });
    }

    @Test(
            groups = {"smoke", "regression"},
            description = "GET single user by valid id returns 200"
    )
    @Severity(SeverityLevel.CRITICAL)
    public void testGetSingleUserSuccess() {

        Response response = client()
                .pathParam("id", 2)
                .get(UserEndpoints.USER_BY_ID);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("data.id", equalTo(2));

        SchemaValidator.validate(response, "appA/single_user_schema.json");

        SingleUserResponse body = response.as(SingleUserResponse.class);

        assertThat(body.getData().getEmail()).isNotBlank();
        assertThat(body.getData().getFirstName()).isNotBlank();
    }

    @Test(
            groups = {"regression", "negative"},
            description = "GET user with non-existent id returns 404"
    )
    @Severity(SeverityLevel.NORMAL)
    @Description("Verifies that requesting a non-existent user returns HTTP 404 with an empty JSON object response.")
    public void testGetSingleUserNotFound() {

        Response response = client()
                .pathParam("id", 999999)
                .get(UserEndpoints.USER_BY_ID);

        ResponseValidator.of(response)
                .assertStatusCode(404);

        /*
         * ReqRes currently returns "{}" for a non-existent user.
         * It does not return a zero-length response body.
         *
         * Therefore, do not use assertBodyEmpty() here.
         *
         * Accept either:
         *   - an actually empty body
         *   - "{}"
         */
        String responseBody = response.getBody().asString().trim();

        assertThat(responseBody)
                .as("Expected empty response body or empty JSON object for 404 response")
                .isIn("", "{}");
    }

    @Test(
            groups = {"regression"},
            description = "GET user list pagination - total pages is consistent with total/per_page"
    )
    @Severity(SeverityLevel.MINOR)
    public void testPaginationConsistency() {

        Response response = client()
                .queryParam("page", 1)
                .get(UserEndpoints.USERS);

        ResponseValidator.of(response)
                .assertStatusCode(200);

        UserListResponse body = response.as(UserListResponse.class);

        int expectedTotalPages =
                (int) Math.ceil(
                        (double) body.getTotal() / body.getPerPage()
                );

        assertThat(body.getTotalPages())
                .isEqualTo(expectedTotalPages);

        assertThat(body.getData())
                .hasSizeLessThanOrEqualTo(body.getPerPage());
    }

    @Test(
            groups = {"regression"},
            description = "GET user list response time is within SLA"
    )
    @Severity(SeverityLevel.MINOR)
    public void testResponseTimeSla() {

        Response response = client().get(UserEndpoints.USERS);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertResponseTimeBelow(5000);
    }

    @Test(
            groups = {"regression"},
            description = "GET /users?delay={seconds} returns the same paginated shape after the requested delay"
    )
    @Severity(SeverityLevel.MINOR)
    @Description("Verifies reqres.in's documented delay parameter on the users list endpoint: the response body " +
            "keeps the same paginated contract as GET /users, but is only returned after waiting at least the " +
            "requested number of seconds.")
    public void testGetUsersWithDelay() {

        int delaySeconds = 2;

        Response response = client()
                .pathParam("seconds", delaySeconds)
                .get(UserEndpoints.DELAYED_USERS);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertContentType("application/json")
                .assertArrayNotEmpty("data");

        SchemaValidator.validate(response, "appA/user_list_schema.json");

        assertThat(response.getTime())
                .as("Response time should reflect the requested %d-second delay", delaySeconds)
                .isGreaterThanOrEqualTo(delaySeconds * 1000L);

        UserListResponse body = response.as(UserListResponse.class);

        assertThat(body.getData()).allSatisfy(user -> {
            assertThat(user.getId()).isPositive();
            assertThat(user.getEmail()).contains("@");
        });
    }
}