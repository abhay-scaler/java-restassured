package com.framework.reporting;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.framework.config.ConfigManager;

/**
 * Creates a single {@link ExtentReports} instance for the whole suite run.
 * ExtentReports itself is thread-safe for concurrent ExtentTest creation,
 * so one shared instance (not one per thread) is correct here — contrast
 * with {@link ExtentTestManager}, which does need a ThreadLocal.
 */
public final class ExtentManager {

    private static volatile ExtentReports extent;

    private ExtentManager() {
    }

    public static synchronized ExtentReports getInstance() {
        if (extent == null) {
            String path = ConfigManager.getConfig().extentReportPath();
            ExtentSparkReporter sparkReporter = new ExtentSparkReporter(path);
            sparkReporter.config().setTheme(Theme.STANDARD);
            sparkReporter.config().setDocumentTitle("API Test Automation Report");
            sparkReporter.config().setReportName("API Testing Framework — Execution Report");

            extent = new ExtentReports();
            extent.attachReporter(sparkReporter);
            extent.setSystemInfo("Environment", ConfigManager.getEnvironment().getValue());
            extent.setSystemInfo("Base URL", ConfigManager.getConfig().baseUrl());
            extent.setSystemInfo("OS", System.getProperty("os.name"));
            extent.setSystemInfo("Java Version", System.getProperty("java.version"));
        }
        return extent;
    }

    public static synchronized void flush() {
        if (extent != null) {
            extent.flush();
        }
    }
}
