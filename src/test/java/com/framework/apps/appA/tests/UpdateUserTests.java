package com.framework.apps.appA.tests;

import com.framework.apps.appA.endpoints.UserEndpoints;
import com.framework.apps.appA.models.request.UpdateUserRequest;
import com.framework.base.BaseTest;
import com.framework.utils.RandomDataGenerator;
import com.framework.validators.ResponseValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Epic("Users API")
@Feature("Update User")
public class UpdateUserTests extends BaseTest {

    @Test(groups = {"smoke", "regression"}, description = "PUT fully updates a user and returns 200")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Verifies a full update (PUT) returns the new field values and an updatedAt timestamp.")
    public void testUpdateUserPutSuccess() {
        UpdateUserRequest requestBody = UpdateUserRequest.builder()
                .name(RandomDataGenerator.fullName())
                .job("Senior QA Engineer")
                .build();

        Response response = client().pathParam("id", 2).put(UserEndpoints.USER_BY_ID, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("job", equalTo("Senior QA Engineer"))
                .assertJsonValue("updatedAt", notNullValue());
    }

    @Test(groups = {"regression"}, description = "PATCH partially updates a user and returns 200")
    @Severity(SeverityLevel.NORMAL)
    public void testUpdateUserPatchSuccess() {
        UpdateUserRequest requestBody = UpdateUserRequest.builder()
                .job("Principal QA Architect")
                .build();

        Response response = client().pathParam("id", 2).patch(UserEndpoints.USER_BY_ID, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("job", equalTo("Principal QA Architect"));
    }

    @Test(groups = {"regression", "negative"}, description = "PUT update on non-existent user still returns 200 (reqres does not enforce existence)")
    @Severity(SeverityLevel.MINOR)
    public void testUpdateNonExistentUser() {
        UpdateUserRequest requestBody = UpdateUserRequest.builder()
                .name("Ghost")
                .job("Unknown")
                .build();

        Response response = client().pathParam("id", 999999).put(UserEndpoints.USER_BY_ID, requestBody);
        ResponseValidator.of(response).assertStatusCode(200);
    }
}
