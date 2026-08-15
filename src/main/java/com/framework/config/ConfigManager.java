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
 * Environment is selected using:
 *
 *     -Denv=qa
 *
 * Configuration is loaded explicitly from:
 *
 *     src/test/resources/config/<env>.properties
 *
 * Example:
 *
 *     src/test/resources/config/qa.properties
 *
 * System properties can still override individual configuration values.
 */
public final class ConfigManager {

    private static final String DEFAULT_ENV = "qa";

    private static final ThreadLocal<AppConfig> CONFIG =
            ThreadLocal.withInitial(ConfigManager::load);

    private ConfigManager() {
    }

    /**
     * Returns the active configuration.
     */
    public static AppConfig getConfig() {
        return CONFIG.get();
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
     * Loads configuration explicitly from the selected environment file.
     */
    private static AppConfig load() {

        String env = System.getProperty("env", DEFAULT_ENV);

        Path configPath = Paths.get(
                "src",
                "test",
                "resources",
                "config",
                env + ".properties"
        );

        System.out.println("========================================");
        System.out.println("Loading configuration");
        System.out.println("Environment: " + env);
        System.out.println("Config file: " + configPath.toAbsolutePath());
        System.out.println("========================================");

        if (!Files.exists(configPath)) {
            throw new IllegalStateException(
                    "Configuration file does not exist: "
                            + configPath.toAbsolutePath()
            );
        }

        Properties environmentProperties = new Properties();

        try (InputStream inputStream =
                     Files.newInputStream(configPath)) {

            environmentProperties.load(inputStream);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to load configuration file: "
                            + configPath.toAbsolutePath(),
                    e
            );
        }

        /*
         * Convert Properties into a Map for OWNER.
         */
        Map<String, Object> properties = new HashMap<>();

        for (String propertyName : environmentProperties.stringPropertyNames()) {
            properties.put(
                    propertyName,
                    environmentProperties.getProperty(propertyName)
            );
        }

        /*
         * System properties override environment properties.
         *
         * This allows:
         *
         * mvn test -Denv=qa -Dbase.url=https://example.com
         *
         * without changing qa.properties.
         */
        for (String propertyName :
                System.getProperties().stringPropertyNames()) {

            properties.put(
                    propertyName,
                    System.getProperty(propertyName)
            );
        }

        /*
         * Make env available to OWNER as well.
         */
        properties.put("env", env);

        AppConfig config = ConfigFactory.create(
                AppConfig.class,
                properties
        );

        printResolvedConfiguration(config);

        return config;
    }

    /**
     * Prints resolved configuration for diagnostics.
     */
    private static void printResolvedConfiguration(AppConfig config) {

        System.out.println("Resolved configuration:");
        System.out.println("base.url = " + config.baseUrl());
        System.out.println("base.path = " + config.basePath());
        System.out.println("auth.type = " + config.authType());
        System.out.println("auth.api.key.name = " + config.apiKeyName());
        System.out.println("max.retry.count = " + config.maxRetryCount());
        System.out.println("========================================");
    }
}