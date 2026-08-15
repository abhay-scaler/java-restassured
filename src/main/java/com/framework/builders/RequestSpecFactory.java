package com.framework.builders;

import com.framework.auth.AuthProvider;
import com.framework.config.AppConfig;
import com.framework.config.ConfigManager;
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
                .setAccept(ContentType.JSON)
                .setConfig(restAssuredConfig(config))
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestResponseLoggingFilter());

        AuthProvider.apply(builder, config);

        return builder.build();
    }

    public static RequestSpecification createWithoutAuth() {
        AppConfig config = ConfigManager.getConfig();

        String baseUri = buildBaseUri(config);

        return new RequestSpecBuilder()
                .setBaseUri(baseUri)
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(restAssuredConfig(config))
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestResponseLoggingFilter())
                .build();
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
        String baseUrl = config.baseUrl();

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Configuration property 'base.url' must not be empty"
            );
        }

        baseUrl = baseUrl.trim().replaceAll("/+$", "");

        String basePath = config.basePath();

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
        return RestAssuredConfig.config()
                .httpClient(
                        HttpClientConfig.httpClientConfig()
                                .setParam(
                                        "http.connection.timeout",
                                        config.connectionTimeout()
                                )
                                .setParam(
                                        "http.socket.timeout",
                                        config.socketTimeout()
                                )
                )
                .encoderConfig(
                        EncoderConfig.encoderConfig()
                                .defaultContentCharset("UTF-8")
                );
    }
}