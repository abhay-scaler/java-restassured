package com.framework.config;

import org.aeonbits.owner.ConfigFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Central configuration manager.
 *
 * Application and environment are selected using:
 *
 *     -Dapp=appA -Denv=qa
 *
 * Configuration is loaded explicitly from:
 *
 *     src/test/resources/config/<app>/<env>.properties
 *
 * Example:
 *
 *     src/test/resources/config/appA/qa.properties
 *
 * Any key missing from that file falls back to
 * src/test/resources/config/default.properties (via AppConfig's own
 * OWNER {@code @Config.Sources} classpath fallback — unchanged).
 * System properties can still override individual configuration values,
 * on top of both files.
 *
 * "Application" is deliberately a plain, validated String — not an enum
 * or a registry — so adding a new application never means editing this
 * class. Validity is whatever exists as a directory under config/.
 */
public final class ConfigManager {

    /** The one place the default application is defined. */
    private static final String DEFAULT_APP = "appA";

    private static final String DEFAULT_ENV = "qa";

    private static final ThreadLocal<AppConfig> CONFIG =
            ThreadLocal.withInitial(ConfigManager::loadAmbient);

    /**
     * The same file + system-property values used to build {@link #CONFIG}
     * for the current thread, kept as a raw map so {@link ServiceConfig}
     * can look up "services.&lt;name&gt;.&lt;key&gt;" overrides that
     * AppConfig has no static accessor for — without a second
     * config-loading mechanism. ServiceConfig never reads this directly
     * by app/env; it only ever asks for "whatever's active on this
     * thread", which is exactly what keeps it application-agnostic.
     */
    private static final ThreadLocal<Map<String, Object>> RAW_PROPERTIES =
            new ThreadLocal<>();

    private ConfigManager() {
    }

    /**
     * Returns the active configuration (application + environment
     * resolved from -Dapp/-Denv once per thread, then cached).
     */
    public static AppConfig getConfig() {
        return CONFIG.get();
    }

    /**
     * Returns a single raw property value (current thread's active
     * application+environment file, overridden by any matching -D system
     * property), or {@code null} if the key isn't set anywhere. Used for
     * the "services.&lt;name&gt;.*" per-service key convention — see
     * {@link ServiceConfig}.
     */
    public static String getRawProperty(String key) {
        getConfig(); // ensures loadAmbient() has populated RAW_PROPERTIES for this thread
        Map<String, Object> properties = RAW_PROPERTIES.get();
        Object value = properties != null ? properties.get(key) : null;
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Returns the active execution environment.
     */
    public static Environment getEnvironment() {
        return Environment.fromString(
                System.getProperty("env", DEFAULT_ENV)
        );
    }

    /**
     * Returns the active application name, validated against the
     * filesystem (a config/&lt;app&gt;/ directory must exist) — not
     * against an enum or registry, so a new application never requires a
     * code change here.
     */
    public static String getApplication() {
        String app = System.getProperty("app", DEFAULT_APP);
        validateApplication(app, applicationDir(app));
        return app;
    }

    /**
     * Resolves configuration for an explicit application + environment,
     * independent of -Dapp/-Denv and independent of the per-thread cache
     * {@link #getConfig()} uses. Every call re-reads the file and returns
     * a fresh, uncached {@link AppConfig} — intentionally, so tests can
     * compare two different applications' configuration side by side in
     * one run without racing the ambient ThreadLocal (which, correctly,
     * only ever resolves one application+environment per thread for the
     * life of the JVM).
     */
    public static AppConfig loadFor(String app, String env) {
        return ConfigFactory.create(AppConfig.class, resolveProperties(app, env));
    }

    /**
     * Same independence as {@link #loadFor}, but returns one raw property
     * value for an explicit application + environment rather than a full
     * AppConfig. Used by regression tests to prove, e.g., that
     * App A's and App B's "services.orders.*" values never collide.
     */
    public static String resolveRawProperty(String app, String env, String key) {
        Object value = resolveProperties(app, env).get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Builds the per-thread ambient configuration from -Dapp/-Denv (or
     * their defaults). Read exactly once per thread by {@link #CONFIG}'s
     * ThreadLocal initializer — never re-read, never mutated at runtime,
     * which is what keeps application/environment selection safe under
     * TestNG's parallel execution without any shared mutable state.
     */
    private static AppConfig loadAmbient() {
        String app = System.getProperty("app", DEFAULT_APP);
        String env = System.getProperty("env", DEFAULT_ENV);

        Map<String, Object> properties = resolveProperties(app, env);
        RAW_PROPERTIES.set(properties);

        AppConfig config = ConfigFactory.create(AppConfig.class, properties);
        printResolvedConfiguration(app, env, config);
        return config;
    }

    /**
     * The pure, parameterized core: given an application and environment,
     * loads config/&lt;app&gt;/&lt;env&gt;.properties, layers -D system
     * properties on top, and returns the merged map. Holds no state and
     * touches no ThreadLocal — both {@link #loadAmbient()} (cached,
     * ambient) and {@link #loadFor}/{@link #resolveRawProperty} (uncached,
     * explicit) build on this one implementation.
     */
    private static Map<String, Object> resolveProperties(String app, String env) {

        if (app == null || app.isBlank()) {
            throw new IllegalArgumentException("Application ('-Dapp') cannot be null or blank");
        }
        if (env == null || env.isBlank()) {
            throw new IllegalArgumentException("Environment ('-Denv') cannot be null or blank");
        }

        Path appDir = applicationDir(app);
        validateApplication(app, appDir);

        Path configPath = appDir.resolve(env + ".properties");
        validateConfigFile(configPath, app, env);

        System.out.println("========================================");
        System.out.println("Loading configuration");
        System.out.println("Application: " + app);
        System.out.println("Environment: " + env);
        System.out.println("Config file: " + configPath.toAbsolutePath());
        System.out.println("========================================");

        Properties fileProperties = new Properties();

        try (InputStream inputStream = Files.newInputStream(configPath)) {
            fileProperties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load configuration file: " + configPath.toAbsolutePath(), e
            );
        }

        /*
         * Convert Properties into a Map for OWNER.
         */
        Map<String, Object> properties = new HashMap<>();

        for (String propertyName : fileProperties.stringPropertyNames()) {
            properties.put(propertyName, fileProperties.getProperty(propertyName));
        }

        /*
         * System properties override file properties.
         *
         * This allows:
         *
         * mvn test -Dapp=appA -Denv=qa -Dbase.url=https://example.com
         *
         * without changing appA/qa.properties.
         */
        for (String propertyName : System.getProperties().stringPropertyNames()) {
            properties.put(propertyName, System.getProperty(propertyName));
        }

        /*
         * Make app/env available to OWNER as well.
         */
        properties.put("app", app);
        properties.put("env", env);

        return properties;
    }

    private static Path applicationDir(String app) {
        return configRoot().resolve(app);
    }

    private static Path configRoot() {
        return Paths.get("src", "test", "resources", "config");
    }

    /**
     * "Unknown application" — the app directory itself doesn't exist.
     * Distinct from {@link #validateConfigFile}, which fires for a known
     * application missing one specific environment's file.
     */
    private static void validateApplication(String app, Path appDir) {
        if (!Files.isDirectory(appDir)) {
            throw new IllegalStateException(
                    "Unknown application '" + app + "'." + System.lineSeparator()
                            + "Expected configuration directory: " + appDir.toAbsolutePath() + System.lineSeparator()
                            + "Expected configuration file:      "
                            + appDir.resolve("<env>.properties").toAbsolutePath()
            );
        }
    }

    /**
     * "Configuration file not found" — the application is known (its
     * directory exists) but this specific environment's file doesn't.
     */
    private static void validateConfigFile(Path configPath, String app, String env) {
        if (!Files.exists(configPath)) {
            throw new IllegalStateException(
                    "Configuration file not found for application '" + app
                            + "' and environment '" + env + "':" + System.lineSeparator()
                            + configPath.toAbsolutePath()
            );
        }
    }

    /**
     * Prints resolved configuration for diagnostics.
     */
    private static void printResolvedConfiguration(String app, String env, AppConfig config) {

        System.out.println("Resolved configuration:");
        System.out.println("application = " + app);
        System.out.println("environment = " + env);
        System.out.println("base.url = " + config.baseUrl());
        System.out.println("base.path = " + config.basePath());
        System.out.println("auth.type = " + config.authType());
        System.out.println("auth.api.key.name = " + config.apiKeyName());
        System.out.println("http.retry.count = " + config.httpRetryCount());
        System.out.println("test.retry.count = " + config.testRetryCount());
        System.out.println("========================================");
    }
}
