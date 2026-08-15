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
import io.restassured.builder.RequestSpecBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Thin, fluent wrapper around RestAssured's request API.
 *
 * Responsibilities:
 *
 * 1. Provide a fluent API for configuring requests.
 * 2. Execute HTTP requests through RestAssured.
 * 3. Retry transient 5xx responses.
 * 4. Prevent request state from leaking between requests.
 * 5. Support parallel TestNG execution safely.
 *
 * Request state is maintained per thread using ThreadLocal.
 *
 * Example:
 *
 * new RestClient()
 *      .pathParam("id", 2)
 *      .get("/users/{id}");
 *
 * Or:
 *
 * new RestClient()
 *      .queryParam("page", 2)
 *      .get("/users");
 *
 * The same RestClient instance may safely be used by multiple
 * TestNG threads because each thread receives its own request
 * specification.
 */
public class RestClient {

    private static final Logger log =
            LoggerFactory.getLogger(RestClient.class);

    /**
     * Base specification used when a new request starts.
     *
     * Each thread receives its own RequestSpecification instance.
     */
    private final ThreadLocal<RequestSpecification> spec;

    // ============================================================
    // Constructors
    // ============================================================

    /**
     * Creates a RestClient using the framework default specification.
     */
    public RestClient() {

        this.spec =
                ThreadLocal.withInitial(
                        RequestSpecFactory::createDefault
                );
    }

    /**
     * Creates a RestClient using a custom RequestSpecification.
     *
     * The supplied specification is copied into a new specification
     * for each thread to prevent concurrent mutation.
     *
     * @param customSpec custom RestAssured request specification
     */
    public RestClient(RequestSpecification customSpec) {

        if (customSpec == null) {
            throw new IllegalArgumentException(
                    "RequestSpecification cannot be null"
            );
        }

        this.spec =
                ThreadLocal.withInitial(
                        () -> copySpecification(customSpec)
                );
    }

    // ============================================================
    // Request customization
    // ============================================================

    public RestClient header(String name, String value) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Header name cannot be null or blank"
            );
        }

        currentSpec().header(name, value);

        return this;
    }

    public RestClient headers(Headers headers) {

        if (headers == null) {
            throw new IllegalArgumentException(
                    "Headers cannot be null"
            );
        }

        currentSpec().headers(headers);

        return this;
    }

    public RestClient headers(Map<String, ?> headers) {

        if (headers == null) {
            throw new IllegalArgumentException(
                    "Headers map cannot be null"
            );
        }

        currentSpec().headers(headers);

        return this;
    }

    public RestClient queryParam(
            String name,
            Object value
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Query parameter name cannot be null or blank"
            );
        }

        currentSpec().queryParam(name, value);

        return this;
    }

    public RestClient queryParams(
            Map<String, ?> params
    ) {

        if (params == null) {
            throw new IllegalArgumentException(
                    "Query parameters cannot be null"
            );
        }

        currentSpec().queryParams(params);

        return this;
    }

    public RestClient pathParam(
            String name,
            Object value
    ) {

        validatePathParameter(name, value);

        currentSpec().pathParam(name, value);

        return this;
    }

    public RestClient pathParams(
            Map<String, ?> params
    ) {

        if (params == null) {
            throw new IllegalArgumentException(
                    "Path parameters cannot be null"
            );
        }

        params.forEach(this::validatePathParameter);

        currentSpec().pathParams(params);

        return this;
    }

    public RestClient body(Object body) {

        if (body == null) {
            throw new IllegalArgumentException(
                    "Request body cannot be null"
            );
        }

        currentSpec().body(body);

        return this;
    }

    public RestClient contentType(String contentType) {

        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "Content type cannot be null or blank"
            );
        }

        currentSpec().contentType(contentType);

        return this;
    }

    public RestClient multiPart(
            String controlName,
            File file
    ) {

        if (controlName == null || controlName.isBlank()) {
            throw new IllegalArgumentException(
                    "Multipart control name cannot be null or blank"
            );
        }

        if (file == null) {
            throw new IllegalArgumentException(
                    "Multipart file cannot be null"
            );
        }

        currentSpec().multiPart(controlName, file);

        return this;
    }

    public RestClient cookie(
            String name,
            String value
    ) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Cookie name cannot be null or blank"
            );
        }

        currentSpec().cookie(name, value);

        return this;
    }

    public RestClient auth(Header authHeader) {

        if (authHeader == null) {
            throw new IllegalArgumentException(
                    "Auth header cannot be null"
            );
        }

        currentSpec().header(authHeader);

        return this;
    }

    /**
     * Returns the RequestSpecification belonging to the current thread.
     */
    public RequestSpecification spec() {

        return currentSpec();
    }

    // ============================================================
    // HTTP verbs
    // ============================================================

    @Step("GET {endpoint}")
    public Response get(String endpoint) {

        return execute(
                HttpMethod.GET,
                endpoint
        );
    }

    @Step("POST {endpoint}")
    public Response post(String endpoint) {

        return execute(
                HttpMethod.POST,
                endpoint
        );
    }

    @Step("POST {endpoint}")
    public Response post(
            String endpoint,
            Object body
    ) {

        return this
                .body(body)
                .execute(
                        HttpMethod.POST,
                        endpoint
                );
    }

    @Step("PUT {endpoint}")
    public Response put(
            String endpoint,
            Object body
    ) {

        return this
                .body(body)
                .execute(
                        HttpMethod.PUT,
                        endpoint
                );
    }

    @Step("PATCH {endpoint}")
    public Response patch(
            String endpoint,
            Object body
    ) {

        return this
                .body(body)
                .execute(
                        HttpMethod.PATCH,
                        endpoint
                );
    }

    @Step("DELETE {endpoint}")
    public Response delete(String endpoint) {

        return execute(
                HttpMethod.DELETE,
                endpoint
        );
    }

    @Step("HEAD {endpoint}")
    public Response head(String endpoint) {

        return execute(
                HttpMethod.HEAD,
                endpoint
        );
    }

    @Step("OPTIONS {endpoint}")
    public Response options(String endpoint) {

        return execute(
                HttpMethod.OPTIONS,
                endpoint
        );
    }

    // ============================================================
    // Execution + retry
    // ============================================================

    /**
     * Executes the request and retries transient 5xx responses.
     *
     * Retry behaviour:
     *
     * 2xx -> return immediately
     * 3xx -> return immediately
     * 4xx -> return immediately
     * 5xx -> retry
     *
     * If all attempts return 5xx, ApiException is thrown.
     */
    private Response execute(
            HttpMethod method,
            String endpoint
    ) {

        validateRequest(
                method,
                endpoint
        );

        AppConfig config =
                ConfigManager.getConfig();

        int maxAttempts =
                Math.max(
                        1,
                        config.maxRetryCount() + 1
                );

        long delay =
                Math.max(
                        0,
                        config.retryDelayMs()
                );

        /*
         * Capture the current specification once.
         *
         * This is important because every retry must use the
         * exact same request configuration.
         */
        RequestSpecification requestSpec =
                currentSpec();

        try {

            for (
                    int attempt = 1;
                    attempt <= maxAttempts;
                    attempt++
            ) {

                Response response =
                        dispatch(
                                requestSpec,
                                method,
                                endpoint
                        );

                int statusCode =
                        response.getStatusCode();

                /*
                 * Everything below 500 is considered non-transient.
                 *
                 * This includes:
                 *
                 * 200
                 * 201
                 * 204
                 * 301
                 * 400
                 * 401
                 * 403
                 * 404
                 * 409
                 * etc.
                 */
                if (statusCode < 500) {

                    return response;
                }

                log.warn(
                        "Attempt {}/{} received HTTP {} for {} {}",
                        attempt,
                        maxAttempts,
                        statusCode,
                        method,
                        endpoint
                );

                /*
                 * No more retries available.
                 */
                if (attempt >= maxAttempts) {

                    break;
                }

                sleep(delay);
            }

            throw new ApiException(
                    "Request failed after " +
                            maxAttempts +
                            " attempt(s): " +
                            method +
                            " " +
                            endpoint
            );

        } finally {

            /*
             * Reset ONLY after the entire request lifecycle has
             * completed.
             *
             * This prevents request-specific state from leaking
             * into the next request while still allowing retries
             * to reuse the complete original request.
             */
            resetSpec();
        }
    }

    /**
     * Dispatches one HTTP request.
     *
     * A fresh RestAssured request context is created for every
     * individual attempt.
     *
     * The RequestSpecification itself is NOT reset here because
     * retries must preserve headers, body, query params and path
     * params.
     */
    private Response dispatch(
            RequestSpecification requestSpec,
            HttpMethod method,
            String endpoint
    ) {

        RequestSpecification request =
                given().spec(requestSpec);

        return switch (method) {

            case GET ->
                    request.when().get(endpoint);

            case POST ->
                    request.when().post(endpoint);

            case PUT ->
                    request.when().put(endpoint);

            case PATCH ->
                    request.when().patch(endpoint);

            case DELETE ->
                    request.when().delete(endpoint);

            case HEAD ->
                    request.when().head(endpoint);

            case OPTIONS ->
                    request.when().options(endpoint);
        };
    }

    // ============================================================
    // Validation
    // ============================================================

    private void validateRequest(
            HttpMethod method,
            String endpoint
    ) {

        if (method == null) {

            throw new IllegalArgumentException(
                    "HTTP method cannot be null"
            );
        }

        if (endpoint == null || endpoint.isBlank()) {

            throw new IllegalArgumentException(
                    "Endpoint cannot be null or blank"
            );
        }
    }

    private void validatePathParameter(
            String name,
            Object value
    ) {

        if (name == null || name.isBlank()) {

            throw new IllegalArgumentException(
                    "Path parameter name cannot be null or blank"
            );
        }

        if (value == null) {

            throw new IllegalArgumentException(
                    "Path parameter '" +
                            name +
                            "' cannot be null"
            );
        }
    }

    // ============================================================
    // Request specification management
    // ============================================================

    /**
     * Returns the RequestSpecification belonging to the current
     * TestNG thread.
     */
    private RequestSpecification currentSpec() {

        return spec.get();
    }

    /**
     * Resets request-specific state after a complete request.
     *
     * A new specification is created for the current thread.
     */
    private void resetSpec() {

        spec.set(
                RequestSpecFactory.createDefault()
        );
    }

    /**
     * Creates an independent RequestSpecification from the supplied
     * custom specification.
     *
     * This prevents multiple TestNG threads from mutating the same
     * RequestSpecification instance.
     */
    private RequestSpecification copySpecification(
            RequestSpecification source
    ) {

        return new RequestSpecBuilder()
                .addRequestSpecification(source)
                .build();
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

            throw new ApiException(
                    "Retry interrupted while waiting " +
                            millis +
                            " ms",
                    e
            );
        }
    }
}