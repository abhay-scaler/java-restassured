package com.framework.apps.appB.tests;

import com.framework.apps.appB.api.AuthApi;
import com.framework.apps.appB.models.request.AuthRequest;
import com.framework.apps.appB.models.response.AuthResponse;
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
import static org.hamcrest.Matchers.notNullValue;

@Epic("Restful Booker API")
@Feature("Authentication")
public class AppBAuthTests extends BaseTest {

    @Test(groups = {"smoke", "regression"}, description = "POST /auth with valid credentials returns a token")
    @Severity(SeverityLevel.BLOCKER)
    @Description("Restful Booker's own login flow - App B's business API, not a framework auth strategy.")
    public void testCreateTokenSuccess() {
        AuthApi authApi = new AuthApi(client());

        AuthRequest request = AuthRequest.builder()
                .username("admin")
                .password("password123")
                .build();

        Response response = authApi.createTokenRaw(request);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("token", notNullValue());

        SchemaValidator.validate(response, "appB/auth_schema.json");

        AuthResponse body = response.as(AuthResponse.class);
        assertThat(body.getToken()).isNotBlank();
    }

    @Test(groups = {"regression", "negative"},
            description = "POST /auth with invalid credentials returns 200 with a 'Bad credentials' reason - " +
                    "Restful Booker does not use a 4xx status here, confirmed against the live API before writing this test")
    @Severity(SeverityLevel.NORMAL)
    public void testCreateTokenInvalidCredentials() {
        AuthApi authApi = new AuthApi(client());

        AuthRequest request = AuthRequest.builder()
                .username("admin")
                .password("wrong-password")
                .build();

        Response response = authApi.createTokenRaw(request);

        ResponseValidator.of(response).assertStatusCode(200);

        AuthResponse body = response.as(AuthResponse.class);
        assertThat(body.getReason()).isEqualTo("Bad credentials");
        assertThat(body.getToken()).isNull();
    }
}
