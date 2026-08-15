package com.framework.auth;

import com.framework.config.AppConfig;
import io.restassured.builder.RequestSpecBuilder;

/**
 * Applies authentication to a request based on {@link AppConfig#authType()}.
 * Centralizing this means switching auth strategy is a one-line config change,
 * not a code change across every test class.
 */
public final class AuthProvider {

    private AuthProvider() {
    }

    public static void apply(RequestSpecBuilder specBuilder, AppConfig config) {
        AuthType type = resolveType(config.authType());

        switch (type) {
            case BASIC -> specBuilder.setAuth(
                    io.restassured.RestAssured.preemptive()
                            .basic(config.authUsername(), config.authPassword()));
            case BEARER_TOKEN -> specBuilder.addHeader("Authorization", "Bearer " + config.authToken());
            case API_KEY -> specBuilder.addHeader(config.apiKeyName(), config.apiKeyValue());
            case DIGEST -> specBuilder.setAuth(
                    io.restassured.RestAssured.digest(config.authUsername(), config.authPassword()));
            case OAUTH2 -> specBuilder.addHeader("Authorization", "Bearer " + config.authToken());
            case NONE -> {
                // no-op — unauthenticated request
            }
        }
    }

    private static AuthType resolveType(String value) {
        try {
            return AuthType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AuthType.NONE;
        }
    }
}
