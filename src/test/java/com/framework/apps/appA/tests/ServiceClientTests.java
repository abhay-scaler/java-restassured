package com.framework.apps.appA.tests;

import com.framework.apps.appA.endpoints.UserEndpoints;
import com.framework.base.BaseTest;
import com.framework.clients.RestClient;
import com.framework.validators.ResponseValidator;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.testng.annotations.Test;

import static org.hamcrest.Matchers.equalTo;

/**
 * Regression coverage for the {@code services.<name>.*} multi-API mechanism
 * (RequestSpecFactory.forService / RestClient(String)) added alongside the
 * existing single-service framework. "users" has no services.users.* keys
 * configured, so these tests also prove the documented fallback: a service
 * with no per-service overrides resolves to today's top-level base.url/auth
 * keys, unchanged.
 */
@Epic("Framework")
@Feature("Multi-service client")
public class ServiceClientTests extends BaseTest {

    @Test(groups = {"regression"},
            description = "RestClient(\"users\") falls back to the top-level base.url/auth config and behaves like the default client")
    @Severity(SeverityLevel.NORMAL)
    @Description("Verifies the services.<name>.* config convention resolves correctly for a service with no overrides.")
    public void testForServiceFallsBackToDefaultConfig() {
        RestClient usersClient = new RestClient("users");

        Response response = usersClient.pathParam("id", 2).get(UserEndpoints.USER_BY_ID);

        ResponseValidator.of(response)
                .assertStatusCode(200)
                .assertJsonValue("data.id", equalTo(2));
    }

    @Test(groups = {"regression"},
            description = "A service-scoped RestClient stays correctly configured across multiple sequential calls")
    @Severity(SeverityLevel.NORMAL)
    @Description("Guards against the client silently reverting to a different spec after its internal reset between calls.")
    public void testForServiceClientSurvivesMultipleCalls() {
        RestClient usersClient = new RestClient("users");

        Response first = usersClient.queryParam("page", 1).get(UserEndpoints.USERS);
        ResponseValidator.of(first).assertStatusCode(200);

        Response second = usersClient.pathParam("id", 2).get(UserEndpoints.USER_BY_ID);
        ResponseValidator.of(second)
                .assertStatusCode(200)
                .assertJsonValue("data.id", equalTo(2));
    }
}
