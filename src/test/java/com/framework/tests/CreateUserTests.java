package com.framework.tests;

import com.framework.base.BaseTest;
import com.framework.dataproviders.DataProviders;
import com.framework.endpoints.UserEndpoints;
import com.framework.models.request.CreateUserRequest;
import com.framework.models.response.CreateUserResponse;
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

    @Test(groups = {"smoke", "regression"}, description = "POST creates a user and returns 201 with generated id")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Verifies user creation returns 201, an id, and a createdAt timestamp.")
    public void testCreateUserSuccess() {
        CreateUserRequest requestBody = CreateUserRequest.builder()
                .name(RandomDataGenerator.fullName())
                .job(RandomDataGenerator.jobTitle())
                .build();

        Response response = client.post(UserEndpoints.USERS, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(201)
                .assertJsonValue("id", notNullValue())
                .assertJsonValue("createdAt", notNullValue());

        SchemaValidator.validate(response, "create_user_schema.json");

        CreateUserResponse body = response.as(CreateUserResponse.class);
        assertThat(body.getName()).isEqualTo(requestBody.getName());
        assertThat(body.getJob()).isEqualTo(requestBody.getJob());
    }

    @Test(groups = {"regression"}, dataProvider = "createUserData",
            dataProviderClass = DataProviders.class,
            description = "Data-driven user creation across multiple name/job combinations")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateUserDataDriven(Map<String, Object> userData) {
        CreateUserRequest requestBody = CreateUserRequest.builder()
                .name((String) userData.get("name"))
                .job((String) userData.get("job"))
                .build();

        Response response = client.post(UserEndpoints.USERS, requestBody);

        ResponseValidator.of(response).assertStatusCode(201);
        CreateUserResponse body = response.as(CreateUserResponse.class);
        assertThat(body.getName()).isEqualTo(userData.get("name"));
        assertThat(body.getJob()).isEqualTo(userData.get("job"));
    }

    @Test(groups = {"regression", "negative"}, description = "POST with empty body still returns 201 (API accepts partial payloads)")
    @Severity(SeverityLevel.MINOR)
    public void testCreateUserEmptyBody() {
        Response response = client.post(UserEndpoints.USERS, "{}");
        ResponseValidator.of(response)
                .assertStatusCode(201)
                .assertJsonValue("id", notNullValue());
    }

    @Test(groups = {"regression"}, description = "POST create user - response contains no unexpected extra top-level fields")
    @Severity(SeverityLevel.TRIVIAL)
    public void testCreateUserResponseShape() {
        CreateUserRequest requestBody = CreateUserRequest.builder()
                .name(RandomDataGenerator.fullName())
                .job(RandomDataGenerator.jobTitle())
                .build();

        Response response = client.post(UserEndpoints.USERS, requestBody);
        Map<String, Object> body = response.jsonPath().getMap("$");

        assertThat(body.keySet()).containsExactlyInAnyOrder("name", "job", "id", "createdAt");
    }
}
