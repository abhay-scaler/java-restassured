package com.framework.apps.appA.tests;

import com.framework.apps.appA.dataproviders.AppADataProviders;
import com.framework.apps.appA.endpoints.UserEndpoints;
import com.framework.apps.appA.models.request.CreateUserRequest;
import com.framework.apps.appA.models.response.CreateUserResponse;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;

@Epic("Users API")
@Feature("Create User")
public class CreateUserTests extends BaseTest {

    @Test(
            groups = {"smoke", "regression"},
            description = "POST creates a user and returns 201 with generated id"
    )
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verifies user creation returns 201, an id, and a createdAt timestamp.")
    public void testCreateUserSuccess() {

        CreateUserRequest requestBody = CreateUserRequest.builder()
                .name(RandomDataGenerator.fullName())
                .job(RandomDataGenerator.jobTitle())
                .build();

        Response response = client().post(UserEndpoints.USERS, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(201)
                .assertJsonValue("id", notNullValue())
                .assertJsonValue("createdAt", notNullValue());

        SchemaValidator.validate(response, "appA/create_user_schema.json");

        CreateUserResponse body = response.as(CreateUserResponse.class);

        assertThat(body.getName())
                .isEqualTo(requestBody.getName());

        assertThat(body.getJob())
                .isEqualTo(requestBody.getJob());
    }

    @Test(
            groups = {"regression"},
            dataProvider = "createUserData",
            dataProviderClass = AppADataProviders.class,
            description = "Data-driven user creation across multiple name/job combinations"
    )
    @Severity(SeverityLevel.NORMAL)
    public void testCreateUserDataDriven(Map<String, Object> userData) {

        CreateUserRequest requestBody = CreateUserRequest.builder()
                .name((String) userData.get("name"))
                .job((String) userData.get("job"))
                .build();

        Response response = client().post(UserEndpoints.USERS, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(201);

        CreateUserResponse body = response.as(CreateUserResponse.class);

        assertThat(body.getName())
                .isEqualTo(userData.get("name"));

        assertThat(body.getJob())
                .isEqualTo(userData.get("job"));
    }

    @Test(
            groups = {"regression", "negative"},
            description = "POST with empty body still returns 201 (API accepts partial payloads)"
    )
    @Severity(SeverityLevel.MINOR)
    public void testCreateUserEmptyBody() {

        Response response = client().post(UserEndpoints.USERS, "{}");

        ResponseValidator.of(response)
                .assertStatusCode(201)
                .assertJsonValue("id", notNullValue());
    }

    @Test(
            groups = {"regression"},
            description = "POST create user - response contains required top-level fields"
    )
    @Severity(SeverityLevel.TRIVIAL)
    @Description("Verifies the create-user response contains all required business fields while allowing additional API metadata.")
    public void testCreateUserResponseShape() {

        CreateUserRequest requestBody = CreateUserRequest.builder()
                .name(RandomDataGenerator.fullName())
                .job(RandomDataGenerator.jobTitle())
                .build();

        Response response = client().post(UserEndpoints.USERS, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(201);

        Map<String, Object> body = response.jsonPath().getMap("$");

        /*
         * ReqRes currently adds "_meta" to the response.
         *
         * Do not use containsExactlyInAnyOrder() here because that
         * makes the test fail whenever the API adds non-breaking
         * metadata fields.
         *
         * The contract of this test is that the required fields exist.
         */
        assertThat(body)
                .containsKeys(
                        "name",
                        "job",
                        "id",
                        "createdAt"
                );

        /*
         * Validate that the values returned by the API are populated.
         */
        assertThat(body.get("name"))
                .isEqualTo(requestBody.getName());

        assertThat(body.get("job"))
                .isEqualTo(requestBody.getJob());

        assertThat(body.get("id"))
                .isNotNull();

        assertThat(body.get("createdAt"))
                .isNotNull();
    }
}