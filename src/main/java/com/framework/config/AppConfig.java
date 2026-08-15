package com.framework.config;

import org.aeonbits.owner.Config;

/**
 * Type-safe application configuration backed by OWNER.
 *
 * Environment-specific properties are loaded explicitly by
 * ConfigManager.
 *
 * System properties may override individual values.
 */
@Config.Sources({
        "system:properties",
        "classpath:config/default.properties"
})
public interface AppConfig extends Config {

    @Key("base.url")
    @DefaultValue("https://reqres.in")
    String baseUrl();

    @Key("base.path")
    @DefaultValue("")
    String basePath();

    @Key("connection.timeout")
    @DefaultValue("15000")
    int connectionTimeout();

    @Key("socket.timeout")
    @DefaultValue("30000")
    int socketTimeout();

    @Key("max.retry.count")
    @DefaultValue("2")
    int maxRetryCount();

    @Key("retry.delay.ms")
    @DefaultValue("1000")
    long retryDelayMs();

    @Key("request.logging.enabled")
    @DefaultValue("true")
    boolean requestLoggingEnabled();

    @Key("response.logging.enabled")
    @DefaultValue("true")
    boolean responseLoggingEnabled();

    @Key("auth.type")
    @DefaultValue("NONE")
    String authType();

    @Key("auth.username")
    @DefaultValue("")
    String authUsername();

    @Key("auth.password")
    @DefaultValue("")
    String authPassword();

    @Key("auth.token")
    @DefaultValue("")
    String authToken();

    @Key("auth.api.key.name")
    @DefaultValue("api_key")
    String apiKeyName();

    @Key("auth.api.key.value")
    @DefaultValue("")
    String apiKeyValue();

    @Key("report.extent.path")
    @DefaultValue("target/extent-reports/ExtentReport.html")
    String extentReportPath();

    @Key("parallel.thread.count")
    @DefaultValue("3")
    int parallelThreadCount();
}