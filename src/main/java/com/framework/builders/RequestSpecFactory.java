package com.framework.builders;

import com.framework.auth.AuthProvider;
import com.framework.config.AppConfig;
import com.framework.config.ConfigManager;
import com.framework.config.ServiceConfig;
import com.framework.filters.ExtentReportingFilter;
import com.framework.filters.RequestResponseLoggingFilter;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.EncoderConfig;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

/**
 * Single place that assembles a RequestSpecification.
 *
 * Base URL, base path, timeouts, authentication and filters are centralized
 * here so every RestClient instance uses the same baseline configuration.
 */
public final class RequestSpecFactory {

    private RequestSpecFactory() {
    }

    public static RequestSpecification createDefault() {
        AppConfig config = ConfigManager.getConfig();

        String baseUri = buildBaseUri(config);

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                // Send the literal "application/json", not ContentType.JSON: RestAssured expands
                // that enum to "application/json, application/javascript, text/javascript, text/json",
                // and Restful Booker's demo API returns 418 "I'm a Teapot" for that broader Accept
                // value (confirmed against the live API; reqres.in is indifferent either way).
                .setAccept("application/json")
                .setConfig(restAssuredConfig(config))
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestResponseLoggingFilter())
                .addFilter(new ExtentReportingFilter());

        AuthProvider.apply(builder, config);

        return builder.build();
    }

    public static RequestSpecification createWithoutAuth() {
        AppConfig config = ConfigManager.getConfig();

        String baseUri = buildBaseUri(config);

        return new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                // See createDefault() above: literal "application/json", not ContentType.JSON.
                .setAccept("application/json")
                .setConfig(restAssuredConfig(config))
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestResponseLoggingFilter())
                .addFilter(new ExtentReportingFilter())
                .build();
    }

    /**
     * Builds a spec for a named service, e.g. {@code forService("orders")}.
     * Reads {@code services.orders.*} keys (base URL, auth, timeouts),
     * falling back to today's top-level keys for anything the service
     * hasn't overridden — so an unregistered service name still resolves
     * to the framework's current single-service defaults.
     *
     * Adding a second real API means adding {@code services.<name>.*}
     * properties, not touching this class.
     */
    public static RequestSpecification forService(String serviceName) {
        ServiceConfig config = ServiceConfig.of(serviceName);

        String baseUri = buildBaseUri(config.baseUrl(), config.basePath());

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                // See createDefault() above: literal "application/json", not ContentType.JSON.
                .setAccept("application/json")
                .setConfig(restAssuredConfig(config.connectionTimeout(), config.socketTimeout()))
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestResponseLoggingFilter())
                .addFilter(new ExtentReportingFilter());

        AuthProvider.apply(builder, config);

        return builder.build();
    }

    /**
     * Builds one deterministic base URI.
     *
     * Example:
     * base.url  = https://reqres.in
     * base.path = /api
     *
     * Result:
     * https://reqres.in/api
     */
    private static String buildBaseUri(AppConfig config) {
        return buildBaseUri(config.baseUrl(), config.basePath());
    }

    private static String buildBaseUri(String baseUrl, String basePath) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Configuration property 'base.url' must not be empty"
            );
        }

        baseUrl = baseUrl.trim().replaceAll("/+$", "");

        if (basePath == null || basePath.isBlank() || "/".equals(basePath.trim())) {
            return baseUrl;
        }

        basePath = basePath.trim();

        if (!basePath.startsWith("/")) {
            basePath = "/" + basePath;
        }

        basePath = basePath.replaceAll("/+$", "");

        return baseUrl + basePath;
    }

    private static RestAssuredConfig restAssuredConfig(AppConfig config) {
        return restAssuredConfig(config.connectionTimeout(), config.socketTimeout());
    }

    private static RestAssuredConfig restAssuredConfig(int connectionTimeout, int socketTimeout) {
        return RestAssuredConfig.config()
                .httpClient(
                        HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", connectionTimeout)
                                .setParam("http.socket.timeout", socketTimeout)
                )
                .encoderConfig(
                        EncoderConfig.encoderConfig()
                                .defaultContentCharset("UTF-8")
                );
    }
}