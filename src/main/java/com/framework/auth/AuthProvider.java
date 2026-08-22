package com.framework.auth;

import com.framework.config.AppConfig;
import com.framework.config.ServiceConfig;
import io.restassured.builder.RequestSpecBuilder;

/**
 * Applies authentication to a request based on the active auth type.
 * Centralizing this means switching auth strategy is a one-line config change,
 * not a code change across every test class.
 *
 * Two overloads share one implementation: the framework-wide {@link AppConfig}
 * (today's single "default" service) and a per-service {@link ServiceConfig}
 * (see {@code RequestSpecFactory.forService}) — both expose the same five
 * auth-related values, just from different config sources.
 */
public final class AuthProvider {

    private AuthProvider() {
    }

    public static void apply(RequestSpecBuilder specBuilder, AppConfig config) {
        applyAuth(specBuilder, config.authType(), config.authUsername(), config.authPassword(),
                config.authToken(), config.apiKeyName(), config.apiKeyValue());
    }

    public static void apply(RequestSpecBuilder specBuilder, ServiceConfig config) {
        applyAuth(specBuilder, config.authType(), config.authUsername(), config.authPassword(),
                config.authToken(), config.apiKeyName(), config.apiKeyValue());
    }

    private static void applyAuth(RequestSpecBuilder specBuilder, String authTypeValue, String username,
                                   String password, String token, String apiKeyName, String apiKeyValue) {
        AuthType type = resolveType(authTypeValue);

        switch (type) {
            case BASIC -> specBuilder.setAuth(
                    io.restassured.RestAssured.preemptive().basic(username, password));
            case BEARER_TOKEN -> specBuilder.addHeader("Authorization", "Bearer " + token);
            case API_KEY -> specBuilder.addHeader(apiKeyName, apiKeyValue);
            case DIGEST -> specBuilder.setAuth(
                    io.restassured.RestAssured.digest(username, password));
            case OAUTH2 -> specBuilder.addHeader("Authorization", "Bearer " + token);
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
