package com.framework.config;

import org.aeonbits.owner.ConfigFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Thread-safe accessor for the active {@link AppConfig}.
 * Environment is resolved once from -Denv=&lt;env&gt; (defaults to "qa")
 * and is shared across the whole run, which keeps parallel test threads
 * pointed at the same environment while still allowing per-property
 * system-property overrides (e.g. -Dbase.url=... for a one-off run).
 */
public final class ConfigManager {

    private static final ThreadLocal<AppConfig> CONFIG = ThreadLocal.withInitial(ConfigManager::load);

    private ConfigManager() {
    }

    public static AppConfig getConfig() {
        return CONFIG.get();
    }

    public static Environment getEnvironment() {
        return Environment.fromString(System.getProperty("env", "qa"));
    }

    private static AppConfig load() {
        String env = System.getProperty("env", "qa");
        Map<String, Object> params = new HashMap<>();
        params.put("env", env);
        return ConfigFactory.create(AppConfig.class, params);
    }
}
