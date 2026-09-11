package com.framework.auth;

import com.framework.config.AppConfig;
import com.framework.config.ServiceConfig;
import io.restassured.builder.RequestSpecBuilder;

import java.util.Arrays;

/**
 * Applies authentication to a request based on the active auth type.
 * Centralizing this means switching auth strategy is a one-line config change,
 * not a code change across every test class.
 *
 * Two overloads share one implementation: the framework-wide {@link AppConfig}
 * (today's single "default" service) and a per-service {@link ServiceConfig}
 * (see {@code RequestSpecFactory.forService}) — both expose the same five
 * auth-related values, just from different config sources.
 *
 * An unrecognized {@code auth.type}, or a blank/missing credential the
 * resolved type actually needs, fails immediately with {@link
 * IllegalStateException} naming the bad value/key — the same
 * fail-fast-on-bad-config discipline {@code ConfigManager} already applies to
 * {@code -Dapp}/{@code -Denv}. Silently falling back to {@code NONE} or
 * sending a blank credential would turn a local config mistake into a
 * confusing remote 401/403 with no indication of the real cause.
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
            case BASIC -> {
                requireNonBlank(username, "auth.username");
                requireNonBlank(password, "auth.password");
                specBuilder.setAuth(io.restassured.RestAssured.preemptive().basic(username, password));
            }
            case BEARER_TOKEN, OAUTH2 -> {
                requireNonBlank(token, "auth.token");
                specBuilder.addHeader("Authorization", "Bearer " + token);
            }
            case API_KEY -> {
                requireNonBlank(apiKeyValue, "auth.api.key.value");
                specBuilder.addHeader(apiKeyName, apiKeyValue);
            }
            case DIGEST -> {
                requireNonBlank(username, "auth.username");
                requireNonBlank(password, "auth.password");
                specBuilder.setAuth(io.restassured.RestAssured.digest(username, password));
            }
            case NONE -> {
                // no-op — unauthenticated request, no credential required
            }
        }
    }

    /**
     * Resolves the configured auth.type string to an {@link AuthType},
     * failing immediately (rather than silently defaulting to {@code NONE})
     * if the value doesn't match a supported type — an unrecognized value is
     * almost always a typo, and a silent downgrade to no auth would only
     * surface later as a confusing remote 401/403.
     */
    private static AuthType resolveType(String value) {
        if (value != null) {
            try {
                return AuthType.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // fall through to the error below
            }
        }
        throw new IllegalStateException(
                "Unknown auth.type '" + value + "'. Supported values: " + Arrays.toString(AuthType.values()));
    }

    /**
     * Fails fast with the offending config key named, rather than sending a
     * blank credential and letting the target API reject it remotely with no
     * indication of which local property was the actual problem.
     */
    private static void requireNonBlank(String value, String configKey) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Configuration key '" + configKey
                            + "' is required for the configured auth.type but is missing or blank.");
        }
    }
}
