package com.framework.validators;

import com.framework.exceptions.ValidationException;
import io.qameta.allure.Step;
import io.restassured.response.Response;
import org.hamcrest.Matcher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluent, chainable assertions over a RestAssured {@link Response}.
 * Wraps failures as {@link ValidationException} with a clear message
 * (including the actual response body) so a failing assertion in a report
 * is diagnosable without re-running the test with logs on.
 *
 * Usage:
 * <pre>
 *   ResponseValidator.of(response)
 *       .assertStatusCode(200)
 *       .assertResponseTimeBelow(2000)
 *       .assertHeaderExists("Content-Type")
 *       .assertJsonValue("data.id", equalTo(2))
 *       .assertBodyContainsKey("data");
 * </pre>
 */
public class ResponseValidator {

    private final Response response;

    private ResponseValidator(Response response) {
        this.response = response;
    }

    public static ResponseValidator of(Response response) {
        return new ResponseValidator(response);
    }

    @Step("Assert status code is {expected}")
    public ResponseValidator assertStatusCode(int expected) {
        int actual = response.getStatusCode();
        if (actual != expected) {
            throw new ValidationException(String.format(
                    "Expected status code <%d> but got <%d>.%nResponse body: %s",
                    expected, actual, response.getBody().asPrettyString()));
        }
        return this;
    }

    @Step("Assert status code is one of {expected}")
    public ResponseValidator assertStatusCodeIn(int... expected) {
        int actual = response.getStatusCode();
        for (int code : expected) {
            if (code == actual) return this;
        }
        throw new ValidationException(String.format(
                "Expected status code to be one of %s but got <%d>.%nResponse body: %s",
                java.util.Arrays.toString(expected), actual, response.getBody().asPrettyString()));
    }

    @Step("Assert response time below {maxMillis} ms")
    public ResponseValidator assertResponseTimeBelow(long maxMillis) {
        long actual = response.getTime();
        assertThat(actual)
                .as("Response time should be below %d ms but was %d ms", maxMillis, actual)
                .isLessThan(maxMillis);
        return this;
    }

    @Step("Assert header {name} exists")
    public ResponseValidator assertHeaderExists(String name) {
        assertThat(response.getHeader(name))
                .as("Expected header '%s' to be present", name)
                .isNotNull();
        return this;
    }

    @Step("Assert header {name} equals {expected}")
    public ResponseValidator assertHeaderEquals(String name, String expected) {
        assertThat(response.getHeader(name))
                .as("Header '%s' mismatch", name)
                .isEqualTo(expected);
        return this;
    }

    @Step("Assert header {name} contains {expectedSubstring}")
    public ResponseValidator assertHeaderContains(String name, String expectedSubstring) {
        assertThat(response.getHeader(name))
                .as("Header '%s' should contain '%s'", name, expectedSubstring)
                .contains(expectedSubstring);
        return this;
    }

    @Step("Assert content type is {expected}")
    public ResponseValidator assertContentType(String expected) {
        assertThat(response.getContentType())
                .as("Content-Type mismatch")
                .contains(expected);
        return this;
    }

    @Step("Assert JSON path {jsonPath} matches")
    public <T> ResponseValidator assertJsonValue(String jsonPath, Matcher<T> matcher) {
        response.then().body(jsonPath, matcher);
        return this;
    }

    @Step("Assert JSON path {jsonPath} equals {expected}")
    public ResponseValidator assertJsonEquals(String jsonPath, Object expected) {
        Object actual = response.jsonPath().get(jsonPath);
        assertThat(actual)
                .as("JSON path '%s' mismatch", jsonPath)
                .isEqualTo(expected);
        return this;
    }

    @Step("Assert body contains key {key}")
    public ResponseValidator assertBodyContainsKey(String key) {
        Map<String, Object> body = response.jsonPath().getMap("$");
        assertThat(body)
                .as("Response body should contain key '%s'", key)
                .containsKey(key);
        return this;
    }

    @Step("Assert JSON array {jsonPath} has size {expectedSize}")
    public ResponseValidator assertArraySize(String jsonPath, int expectedSize) {
        List<Object> list = response.jsonPath().getList(jsonPath);
        assertThat(list)
                .as("Array at '%s' should have size %d", jsonPath, expectedSize)
                .hasSize(expectedSize);
        return this;
    }

    @Step("Assert JSON array {jsonPath} is not empty")
    public ResponseValidator assertArrayNotEmpty(String jsonPath) {
        List<Object> list = response.jsonPath().getList(jsonPath);
        assertThat(list)
                .as("Array at '%s' should not be empty", jsonPath)
                .isNotEmpty();
        return this;
    }

    @Step("Assert body is empty")
    public ResponseValidator assertBodyEmpty() {
        assertThat(response.getBody().asString())
                .as("Expected empty response body")
                .isEmpty();
        return this;
    }

    @Step("Assert cookie {name} exists")
    public ResponseValidator assertCookieExists(String name) {
        assertThat(response.getCookie(name))
                .as("Expected cookie '%s' to be present", name)
                .isNotNull();
        return this;
    }

    public Response response() {
        return response;
    }

    public <T> T extractAs(Class<T> clazz) {
        return response.as(clazz);
    }

    public <T> T extractJsonPath(String jsonPath) {
        return response.jsonPath().get(jsonPath);
    }
}
