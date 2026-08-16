package com.framework.tests;

import com.framework.base.BaseTest;
import com.framework.endpoints.UserEndpoints;
import com.framework.validators.ResponseValidator;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

@Epic("Users API")
@Feature("Delete User")
public class DeleteUserTests extends BaseTest {

    @Test(groups = {"smoke", "regression"}, description = "DELETE removes a user and returns 204 with empty body")
    @Severity(SeverityLevel.CRITICAL)
    public void testDeleteUserSuccess() {
        Response response = client().pathParam("id", 2).delete(UserEndpoints.USER_BY_ID);

        ResponseValidator.of(response)
                .assertStatusCode(204)
                .assertBodyEmpty();
    }

    @Test(groups = {"regression"}, description = "DELETE on already-deleted / non-existent id still returns 204")
    @Severity(SeverityLevel.MINOR)
    public void testDeleteNonExistentUser() {
        Response response = client().pathParam("id", 999999).delete(UserEndpoints.USER_BY_ID);
        ResponseValidator.of(response).assertStatusCode(204);
    }
}
