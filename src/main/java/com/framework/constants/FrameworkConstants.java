package com.framework.constants;

public final class FrameworkConstants {

    private FrameworkConstants() {
    }

    public static final String TEST_DATA_DIR = "src/test/resources/testdata/";
    public static final String SCHEMA_DIR = "src/test/resources/schemas/";
    public static final String EXTENT_REPORT_DIR = "target/extent-reports/";
    public static final String ALLURE_RESULTS_DIR = "target/allure-results/";
    public static final String LOG_DIR = "target/logs/";

    public static final int DEFAULT_RESPONSE_TIME_THRESHOLD_MS = 3000;
}
