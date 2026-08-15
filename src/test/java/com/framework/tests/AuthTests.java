package com.framework.tests;

import com.framework.base.BaseTest;
import com.framework.dataproviders.DataProviders;
import com.framework.endpoints.UserEndpoints;
import com.framework.models.request.LoginRequest;
import com.framework.validators.ResponseValidator;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import java.util.Map;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Epic("Users API")
@Feature("Authentication")
public class AuthTests extends BaseTest {

    @Test(groups = {"smoke", "regression"}, description = "Successful registration returns id and token")
    @Severity(SeverityLevel.BLOCKER)
    public void testRegisterSuccess() {
        LoginRequest requestBody = LoginRequest.builder()
                .email("eve.holt@reqres.in")
                .password("pistol")
                .build();

        Response response = client.post(UserEndpoints.REGISTER, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("id", notNullValue())
                .assertJsonValue("token", notNullValue());
    }

    @Test(groups = {"smoke", "regression"}, description = "Successful login returns a token")
    @Severity(SeverityLevel.BLOCKER)
    public void testLoginSuccess() {
        LoginRequest requestBody = LoginRequest.builder()
                .email("eve.holt@reqres.in")
                .password("cityslicka")
                .build();

        Response response = client.post(UserEndpoints.LOGIN, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("token", notNullValue());
    }

    @Test(groups = {"regression", "negative"}, dataProvider = "invalidLoginData",
            dataProviderClass = DataProviders.class,
            description = "Login with missing email/password returns 400 with expected error message")
    @Severity(SeverityLevel.NORMAL)
    public void testLoginInvalidCredentials(Map<String, Object> data) {
        LoginRequest requestBody = LoginRequest.builder()
                .email((String) data.get("email"))
                .password((String) data.get("password"))
                .build();

        Response response = client.post(UserEndpoints.LOGIN, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(400)
                .assertJsonValue("error", equalTo(data.get("expectedError")));
    }

    @Test(groups = {"regression", "negative"}, description = "Register without a password returns 400")
    @Severity(SeverityLevel.NORMAL)
    public void testRegisterWithoutPassword() {
        LoginRequest requestBody = LoginRequest.builder()
                .email("sydney@fife")
                .build();

        Response response = client.post(UserEndpoints.REGISTER, requestBody);

        ResponseValidator.of(response)
                .assertStatusCode(400)
                .assertJsonValue("error", equalTo("Missing password"));
    }
}
