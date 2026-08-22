package com.framework.config;

/**
 * A named slice of configuration for one API/service, read from the same
 * per-environment properties file {@link AppConfig} already loads.
 *
 * Each value is looked up as {@code services.<name>.<key>} first (e.g.
 * {@code services.orders.base-url}), falling back to today's unprefixed
 * top-level key (e.g. {@code base.url}) when the service hasn't overridden
 * it. That fallback is what lets the existing "users" API keep working
 * with zero new properties — only a second service needs new keys.
 *
 * Deliberately not a second OWNER {@code Config} interface per service:
 * one properties file, one loading mechanism ({@link ConfigManager}), just
 * a naming convention read through {@link ConfigManager#getRawProperty}.
 */
public final class ServiceConfig {

    private final String name;

    private ServiceConfig(String name) {
        this.name = name;
    }

    public static ServiceConfig of(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("Service name cannot be null or blank");
        }
        return new ServiceConfig(serviceName);
    }

    public String name() {
        return name;
    }

    public String baseUrl() {
        return value("base.url", ConfigManager.getConfig().baseUrl());
    }

    public String basePath() {
        return value("base.path", ConfigManager.getConfig().basePath());
    }

    public int connectionTimeout() {
        return intValue("connection.timeout", ConfigManager.getConfig().connectionTimeout());
    }

    public int socketTimeout() {
        return intValue("socket.timeout", ConfigManager.getConfig().socketTimeout());
    }

    public String authType() {
        return value("auth.type", ConfigManager.getConfig().authType());
    }

    public String authUsername() {
        return value("auth.username", ConfigManager.getConfig().authUsername());
    }

    public String authPassword() {
        return value("auth.password", ConfigManager.getConfig().authPassword());
    }

    public String authToken() {
        return value("auth.token", ConfigManager.getConfig().authToken());
    }

    public String apiKeyName() {
        return value("auth.api.key.name", ConfigManager.getConfig().apiKeyName());
    }

    public String apiKeyValue() {
        return value("auth.api.key.value", ConfigManager.getConfig().apiKeyValue());
    }

    private String value(String key, String fallback) {
        String raw = ConfigManager.getRawProperty("services." + name + "." + key);
        return raw != null ? raw : fallback;
    }

    private int intValue(String key, int fallback) {
        String raw = ConfigManager.getRawProperty("services." + name + "." + key);
        return raw != null ? Integer.parseInt(raw) : fallback;
    }
}
