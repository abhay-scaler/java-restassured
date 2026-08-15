package com.framework.config;

/**
 * Supported execution environments. Add new environments here and create a
 * matching {@code <env>.properties} file under src/test/resources/config.
 */
public enum Environment {
    DEV("dev"),
    QA("qa"),
    STAGE("stage"),
    PROD("prod");

    private final String value;

    Environment(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Environment fromString(String value) {
        for (Environment env : values()) {
            if (env.value.equalsIgnoreCase(value)) {
                return env;
            }
        }
        throw new IllegalArgumentException("Unknown environment: " + value
                + ". Supported: dev, qa, stage, prod");
    }
}
