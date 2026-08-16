package com.framework.tests;

import com.framework.base.BaseTest;
import com.framework.endpoints.UserEndpoints;
import com.framework.models.response.SingleUserResponse;
import com.framework.models.response.UserListResponse;
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

        SchemaValidator.validate(response, "user_list_schema.json");

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

        SchemaValidator.validate(response, "single_user_schema.json");

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
}