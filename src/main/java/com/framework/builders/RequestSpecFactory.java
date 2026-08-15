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
 * Single place that assembles a {@link RequestSpecification}: base URI,
 * timeouts, auth, and logging/Allure filters all come from here so every
 * client and test shares identical baseline behaviour. Tests that need a
 * one-off tweak (extra header, different content-type) do it on the
 * returned spec without touching this factory.
 */
public final class RequestSpecFactory {

    private RequestSpecFactory() {
    }

    public static RequestSpecification createDefault() {
        AppConfig config = ConfigManager.getConfig();

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(config.baseUrl())
                .setBasePath(config.basePath())
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
        return new RequestSpecBuilder()
                .setBaseUri(config.baseUrl())
                .setBasePath(config.basePath())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(restAssuredConfig(config))
                .addFilter(new AllureRestAssured())
                .addFilter(new RequestResponseLoggingFilter())
                .build();
    }

    private static RestAssuredConfig restAssuredConfig(AppConfig config) {
        return RestAssuredConfig.config()
                .httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", config.connectionTimeout())
                        .setParam("http.socket.timeout", config.socketTimeout()))
                .encoderConfig(EncoderConfig.encoderConfig()
                        .defaultContentCharset("UTF-8"));
    }
}
