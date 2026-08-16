package com.framework.clients;

import com.framework.builders.RequestSpecFactory;
import com.framework.config.AppConfig;
import com.framework.config.ConfigManager;
import com.framework.constants.HttpMethod;
import com.framework.exceptions.ApiException;
import io.qameta.allure.Step;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.Header;
import io.restassured.http.Headers;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Thin, fluent wrapper around RestAssured's request API.
 *
 * Responsibilities:
 * 1. Provide a fluent API for configuring requests.
 * 2. Execute HTTP requests through RestAssured.
 * 3. Retry transient 5xx responses.
 * 4. Reset request-specific state after each call so it doesn't leak
 *    into the next call made on the same instance.
 *
 * Thread-safety note: a RestClient instance is meant to be created fresh
 * per test (see BaseTest#setUp, called from @BeforeMethod) and used only
 * by the thread running that test — it is NOT designed to be shared
 * across threads. That single-instance-per-test lifecycle is also why a
 * plain field is used here instead of a ThreadLocal: a ThreadLocal would
 * only add overhead, and worse, would leak entries in TestNG's reused
 * worker-thread pool if never explicitly removed.
 *
 * Example:
 * <pre>
 *   new RestClient()
 *       .pathParam("id", 2)
 *       .get("/users/{id}");
 * </pre>
 */
public class RestClient {

    private static final Logger log = LoggerFactory.getLogger(RestClient.class);

    private RequestSpecification spec;

    // ============================================================
    // Constructors
    // ============================================================

    /** Creates a RestClient using the framework default specification. */
    public RestClient() {
        this.spec = RequestSpecFactory.createDefault();
    }

    /**
     * Creates a RestClient using a custom RequestSpecification. The
     * supplied specification is copied so mutations made through this
     * client's fluent methods never affect the caller's original object.
     */
    public RestClient(RequestSpecification customSpec) {
        if (customSpec == null) {
            throw new IllegalArgumentException("RequestSpecification cannot be null");
        }
        this.spec = copySpecification(customSpec);
    }

    // ============================================================
    // Request customization
    // ============================================================

    public RestClient header(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Header name cannot be null or blank");
        }
        spec.header(name, value);
        return this;
    }

    public RestClient headers(Headers headers) {
        if (headers == null) {
            throw new IllegalArgumentException("Headers cannot be null");
        }
        spec.headers(headers);
        return this;
    }

    public RestClient headers(Map<String, ?> headers) {
        if (headers == null) {
            throw new IllegalArgumentException("Headers map cannot be null");
        }
        spec.headers(headers);
        return this;
    }

    public RestClient queryParam(String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Query parameter name cannot be null or blank");
        }
        spec.queryParam(name, value);
        return this;
    }

    public RestClient queryParams(Map<String, ?> params) {
        if (params == null) {
            throw new IllegalArgumentException("Query parameters cannot be null");
        }
        spec.queryParams(params);
        return this;
    }

    public RestClient pathParam(String name, Object value) {
        validatePathParameter(name, value);
        spec.pathParam(name, value);
        return this;
    }

    public RestClient pathParams(Map<String, ?> params) {
        if (params == null) {
            throw new IllegalArgumentException("Path parameters cannot be null");
        }
        params.forEach(this::validatePathParameter);
        spec.pathParams(params);
        return this;
    }

    public RestClient body(Object body) {
        if (body == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        spec.body(body);
        return this;
    }

    public RestClient contentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content type cannot be null or blank");
        }
        spec.contentType(contentType);
        return this;
    }

    public RestClient multiPart(String controlName, File file) {
        if (controlName == null || controlName.isBlank()) {
            throw new IllegalArgumentException("Multipart control name cannot be null or blank");
        }
        if (file == null) {
            throw new IllegalArgumentException("Multipart file cannot be null");
        }
        spec.multiPart(controlName, file);
        return this;
    }

    public RestClient cookie(String name, String value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Cookie name cannot be null or blank");
        }
        spec.cookie(name, value);
        return this;
    }

    public RestClient auth(Header authHeader) {
        if (authHeader == null) {
            throw new IllegalArgumentException("Auth header cannot be null");
        }
        spec.header(authHeader);
        return this;
    }

    /** Returns the RequestSpecification currently being built by this client. */
    public RequestSpecification spec() {
        return spec;
    }

    // ============================================================
    // HTTP verbs
    // ============================================================

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

    // ============================================================
    // Execution + retry
    // ============================================================

    /**
     * Executes the request and retries transient 5xx responses.
     *
     * Retry behaviour: 2xx/3xx/4xx return immediately (a 4xx is the
     * system under test telling us something real — retrying would mask
     * a genuine bug). Only 5xx is treated as possibly transient and
     * retried up to {@code max.retry.count} additional times. If every
     * attempt still comes back 5xx, an {@link ApiException} is thrown
     * rather than returning the failed response — a persistent 5xx means
     * the call itself didn't succeed, so it's treated as an execution
     * failure rather than a response for the test to assert against.
     */
    private Response execute(HttpMethod method, String endpoint) {
        validateRequest(method, endpoint);

        AppConfig config = ConfigManager.getConfig();
        int maxAttempts = Math.max(1, config.maxRetryCount() + 1);
        long delay = Math.max(0, config.retryDelayMs());

        // Captured once: every retry attempt must reuse the exact same
        // request configuration (headers, body, params).
        RequestSpecification requestSpec = spec;

        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                Response response = dispatch(requestSpec, method, endpoint);
                int statusCode = response.getStatusCode();

                if (statusCode < 500) {
                    return response;
                }

                log.warn("Attempt {}/{} received HTTP {} for {} {}",
                        attempt, maxAttempts, statusCode, method, endpoint);

                if (attempt >= maxAttempts) {
                    throw new ApiException("Request failed after " + maxAttempts
                            + " attempt(s), last status: " + statusCode
                            + " for " + method + " " + endpoint);
                }
                sleep(delay);
            }
            // Unreachable — the loop above always returns or throws.
            throw new ApiException("Request failed: " + method + " " + endpoint);

        } finally {
            // Reset only after the whole request lifecycle (all retries)
            // completes, so this client starts clean for its next call.
            resetSpec();
        }
    }

    private Response dispatch(RequestSpecification requestSpec, HttpMethod method, String endpoint) {
        RequestSpecification request = given().spec(requestSpec);
        return switch (method) {
            case GET -> request.when().get(endpoint);
            case POST -> request.when().post(endpoint);
            case PUT -> request.when().put(endpoint);
            case PATCH -> request.when().patch(endpoint);
            case DELETE -> request.when().delete(endpoint);
            case HEAD -> request.when().head(endpoint);
            case OPTIONS -> request.when().options(endpoint);
        };
    }

    // ============================================================
    // Validation
    // ============================================================

    private void validateRequest(HttpMethod method, String endpoint) {
        if (method == null) {
            throw new IllegalArgumentException("HTTP method cannot be null");
        }
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Endpoint cannot be null or blank");
        }
    }

    private void validatePathParameter(String name, Object value) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Path parameter name cannot be null or blank");
        }
        if (value == null) {
            throw new IllegalArgumentException("Path parameter '" + name + "' cannot be null");
        }
    }

    // ============================================================
    // Request specification management
    // ============================================================

    private void resetSpec() {
        this.spec = RequestSpecFactory.createDefault();
    }

    /** Builds an independent copy so the caller's original spec is never mutated. */
    private RequestSpecification copySpecification(RequestSpecification source) {
        return new RequestSpecBuilder().addRequestSpecification(source).build();
    }

    // ============================================================
    // Retry delay
    // ============================================================

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Retry interrupted while waiting " + millis + " ms", e);
        }
    }
}