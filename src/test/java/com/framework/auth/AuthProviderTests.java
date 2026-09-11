package com.framework.auth;

import com.framework.config.AppConfig;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.Headers;
import io.restassured.specification.FilterableRequestSpecification;
import org.aeonbits.owner.ConfigFactory;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Network-free coverage for {@link AuthProvider}'s config validation.
 *
 * Builds {@link AppConfig} instances directly via {@code ConfigFactory.create(AppConfig.class,
 * overrides)} - the same OWNER API {@code ConfigManager.loadFor(app, env)} uses internally, just
 * with a small ad hoc map instead of a real {@code config/<app>/<env>.properties} file. Only the
 * auth.* keys each test cares about are set in that map; every other key resolves through
 * {@code AppConfig}'s own {@code @Config.Sources} chain (system properties, then
 * classpath:config/default.properties) and, failing that, each key's own {@code @DefaultValue}
 * annotation - this is deliberately a narrower construction than {@code ConfigManager}'s, not
 * "exactly" the same resolution a real app/env run performs. No HTTP call, no app/env fixture
 * file, no suite dependency.
 */
@Epic("Framework")
@Feature("Authentication configuration")
public class AuthProviderTests {

    private static AppConfig configWith(Map<String, Object> overrides) {
        return ConfigFactory.create(AppConfig.class, overrides);
    }

    private static Map<String, Object> withAuthType(String authType) {
        Map<String, Object> map = new HashMap<>();
        map.put("auth.type", authType);
        return map;
    }

    @Test(groups = {"regression"},
            description = "Unknown auth.type throws IllegalStateException naming the bad value and supported types")
    @Severity(SeverityLevel.CRITICAL)
    public void testUnknownAuthTypeThrows() {
        AppConfig config = configWith(withAuthType("BOGUS"));

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOGUS")
                .hasMessageContaining("NONE")
                .hasMessageContaining("API_KEY");
    }

    @Test(groups = {"regression"},
            description = "API_KEY with a blank auth.api.key.value throws, naming that key")
    @Severity(SeverityLevel.CRITICAL)
    public void testApiKeyBlankValueThrows() {
        Map<String, Object> overrides = withAuthType("API_KEY");
        overrides.put("auth.api.key.value", "");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.api.key.value");
    }

    @Test(groups = {"regression"},
            description = "BEARER_TOKEN with a blank auth.token throws, naming that key")
    @Severity(SeverityLevel.CRITICAL)
    public void testBearerTokenBlankThrows() {
        Map<String, Object> overrides = withAuthType("BEARER_TOKEN");
        overrides.put("auth.token", "");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.token");
    }

    @Test(groups = {"regression"},
            description = "BEARER_TOKEN with a whitespace-only auth.token throws, not just an empty "
                    + "one - locks isBlank() (not isEmpty()) as the intended check")
    @Severity(SeverityLevel.CRITICAL)
    public void testBearerTokenWhitespaceOnlyThrows() {
        Map<String, Object> overrides = withAuthType("BEARER_TOKEN");
        overrides.put("auth.token", " ");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.token");
    }

    @Test(groups = {"regression"},
            description = "OAUTH2 with a blank auth.token throws, naming that key - OAUTH2 "
                    + "intentionally shares BEARER_TOKEN's implementation path")
    @Severity(SeverityLevel.CRITICAL)
    public void testOauth2BlankTokenThrows() {
        Map<String, Object> overrides = withAuthType("OAUTH2");
        overrides.put("auth.token", "");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.token");
    }

    @Test(groups = {"regression"},
            description = "BASIC with a blank auth.username throws, naming that key")
    @Severity(SeverityLevel.CRITICAL)
    public void testBasicBlankUsernameThrows() {
        Map<String, Object> overrides = withAuthType("BASIC");
        overrides.put("auth.username", "");
        overrides.put("auth.password", "somepassword");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.username");
    }

    @Test(groups = {"regression"},
            description = "BASIC with a blank auth.password throws, naming that key")
    @Severity(SeverityLevel.CRITICAL)
    public void testBasicBlankPasswordThrows() {
        Map<String, Object> overrides = withAuthType("BASIC");
        overrides.put("auth.username", "someuser");
        overrides.put("auth.password", "");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.password");
    }

    @Test(groups = {"regression"},
            description = "DIGEST with a blank auth.username throws, naming that key")
    @Severity(SeverityLevel.NORMAL)
    public void testDigestBlankUsernameThrows() {
        Map<String, Object> overrides = withAuthType("DIGEST");
        overrides.put("auth.username", "");
        overrides.put("auth.password", "somepassword");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.username");
    }

    @Test(groups = {"regression"},
            description = "DIGEST with a blank auth.password throws, naming that key")
    @Severity(SeverityLevel.NORMAL)
    public void testDigestBlankPasswordThrows() {
        Map<String, Object> overrides = withAuthType("DIGEST");
        overrides.put("auth.username", "someuser");
        overrides.put("auth.password", "");
        AppConfig config = configWith(overrides);

        assertThatThrownBy(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.password");
    }

    @Test(groups = {"regression"},
            description = "NONE requires no credentials and applies without error")
    @Severity(SeverityLevel.NORMAL)
    public void testNoneAppliesWithoutCredentials() {
        AppConfig config = configWith(withAuthType("NONE"));

        assertThatCode(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .doesNotThrowAnyException();
    }

    @Test(groups = {"regression"},
            description = "A valid API_KEY configuration (App A's real shape) applies without error")
    @Severity(SeverityLevel.CRITICAL)
    public void testValidApiKeyConfigurationApplies() {
        Map<String, Object> overrides = withAuthType("API_KEY");
        overrides.put("auth.api.key.name", "x-api-key");
        overrides.put("auth.api.key.value", "real-value");
        AppConfig config = configWith(overrides);

        assertThatCode(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .doesNotThrowAnyException();
    }

    @Test(groups = {"regression"},
            description = "A valid BASIC configuration (App B's real shape) applies without error")
    @Severity(SeverityLevel.NORMAL)
    public void testValidBasicConfigurationApplies() {
        Map<String, Object> overrides = withAuthType("BASIC");
        overrides.put("auth.username", "admin");
        overrides.put("auth.password", "password123");
        AppConfig config = configWith(overrides);

        assertThatCode(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .doesNotThrowAnyException();
    }

    @Test(groups = {"regression"},
            description = "A valid BEARER_TOKEN configuration applies without error")
    @Severity(SeverityLevel.NORMAL)
    public void testValidBearerTokenConfigurationApplies() {
        Map<String, Object> overrides = withAuthType("BEARER_TOKEN");
        overrides.put("auth.token", "real-token");
        AppConfig config = configWith(overrides);

        assertThatCode(() -> AuthProvider.apply(new RequestSpecBuilder(), config))
                .doesNotThrowAnyException();
    }

    @Test(groups = {"regression"},
            description = "A valid OAUTH2 configuration adds the same Bearer header BEARER_TOKEN "
                    + "would, proving OAUTH2 genuinely runs through that shared path rather than "
                    + "merely not throwing")
    @Severity(SeverityLevel.NORMAL)
    public void testValidOauth2ConfigurationApplies() {
        Map<String, Object> overrides = withAuthType("OAUTH2");
        overrides.put("auth.token", "real-oauth2-token");
        AppConfig config = configWith(overrides);

        RequestSpecBuilder builder = new RequestSpecBuilder();
        assertThatCode(() -> AuthProvider.apply(builder, config)).doesNotThrowAnyException();

        Headers headers = ((FilterableRequestSpecification) builder.build()).getHeaders();
        assertThat(headers.getValue("Authorization")).isEqualTo("Bearer real-oauth2-token");
    }
}
