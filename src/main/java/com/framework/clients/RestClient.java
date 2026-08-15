package com.framework.clients;

import com.framework.builders.RequestSpecFactory;
import com.framework.config.AppConfig;
import com.framework.config.ConfigManager;
import com.framework.constants.HttpMethod;
import com.framework.exceptions.ApiException;
import io.qameta.allure.Step;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Thin, fluent wrapper around RestAssured's given/when/then chain.
 * Every HTTP verb funnels through {@link #execute}, which is where retry
 * behaviour lives — one place to change "retry on 5xx" policy for the
 * whole framework instead of duplicating it per test.
 *
 * Usage:
 * <pre>
 *   Response resp = new RestClient()
 *       .queryParam("page", 2)
 *       .get(UserEndpoints.USERS);
 * </pre>
 */
public class RestClient {

    private static final Logger log = LoggerFactory.getLogger(RestClient.class);

    private RequestSpecification spec;

    public RestClient() {
        this.spec = RequestSpecFactory.createDefault().request();
    }

    public RestClient(RequestSpecification customSpec) {
        this.spec = customSpec;
    }

    // ---------- request customization (fluent) ----------

    public RestClient header(String name, String value) {
        spec = spec.header(name, value);
        return this;
    }

    public RestClient headers(Headers headers) {
        spec = spec.headers(headers);
        return this;
    }

    public RestClient headers(Map<String, ?> headers) {
        spec = spec.headers(headers);
        return this;
    }

    public RestClient queryParam(String name, Object value) {
        spec = spec.queryParam(name, value);
        return this;
    }

    public RestClient queryParams(Map<String, ?> params) {
        spec = spec.queryParams(params);
        return this;
    }

    public RestClient pathParam(String name, Object value) {
        spec = spec.pathParam(name, value);
        return this;
    }

    public RestClient pathParams(Map<String, ?> params) {
        spec = spec.pathParams(params);
        return this;
    }

    public RestClient body(Object body) {
        spec = spec.body(body);
        return this;
    }

    public RestClient contentType(String contentType) {
        spec = spec.contentType(contentType);
        return this;
    }

    public RestClient multiPart(String controlName, java.io.File file) {
        spec = spec.multiPart(controlName, file);
        return this;
    }

    public RestClient cookie(String name, String value) {
        spec = spec.cookie(name, value);
        return this;
    }

    public RestClient auth(Header authHeader) {
        spec = spec.header(authHeader);
        return this;
    }

    public RequestSpecification spec() {
        return spec;
    }

    // ---------- HTTP verbs ----------

    @Step("GET {endpoint}")
    public Response get(String endpoint) {
        return execute(HttpMethod.GET, endpoint);
    }

    @Step("POST {endpoint}")
    public Response post(String endpoint) {
        return execute(HttpMethod.POST, endpoint);
    }

    @Step("POST {endpoint}")
    public Response post(String endpoint, Object body) {
        return this.body(body).execute(HttpMethod.POST, endpoint);
    }

    @Step("PUT {endpoint}")
    public Response put(String endpoint, Object body) {
        return this.body(body).execute(HttpMethod.PUT, endpoint);
    }

    @Step("PATCH {endpoint}")
    public Response patch(String endpoint, Object body) {
        return this.body(body).execute(HttpMethod.PATCH, endpoint);
    }

    @Step("DELETE {endpoint}")
    public Response delete(String endpoint) {
        return execute(HttpMethod.DELETE, endpoint);
    }

    @Step("HEAD {endpoint}")
    public Response head(String endpoint) {
        return execute(HttpMethod.HEAD, endpoint);
    }

    @Step("OPTIONS {endpoint}")
    public Response options(String endpoint) {
        return execute(HttpMethod.OPTIONS, endpoint);
    }

    // ---------- execution + retry ----------

    private Response execute(HttpMethod method, String endpoint) {
        AppConfig config = ConfigManager.getConfig();
        int maxAttempts = Math.max(1, config.maxRetryCount() + 1);
        long delay = config.retryDelayMs();

        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Response response = dispatch(method, endpoint);

                // Retry only on transient server-side failures; 4xx are the
                // system under test telling us something real — don't mask it.
                if (response.getStatusCode() < 500) {
                    return response;
                }
                log.warn("Attempt {}/{} got {} for {} {} — retrying transient failure",
                        attempt, maxAttempts, response.getStatusCode(), method, endpoint);
                if (attempt == maxAttempts) {
                    return response;
                }
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("Attempt {}/{} threw {} for {} {}",
                        attempt, maxAttempts, e.getClass().getSimpleName(), method, endpoint);
                if (attempt == maxAttempts) {
                    throw new ApiException("Request failed after " + maxAttempts
                            + " attempt(s): " + method + " " + endpoint, e);
                }
            }
            sleep(delay);
        }
        // Unreachable in practice, but keeps the compiler happy.
        throw new ApiException("Request failed: " + method + " " + endpoint, lastFailure);
    }

    private Response dispatch(HttpMethod method, String endpoint) {
        return switch (method) {
            case GET -> spec.when().get(endpoint);
            case POST -> spec.when().post(endpoint);
            case PUT -> spec.when().put(endpoint);
            case PATCH -> spec.when().patch(endpoint);
            case DELETE -> spec.when().delete(endpoint);
            case HEAD -> spec.when().head(endpoint);
            case OPTIONS -> spec.when().options(endpoint);
        };
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
