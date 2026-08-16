package com.framework.reporting;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

import java.io.File;

/**
 * Singleton owner of the ExtentReports instance.
 */
public final class ExtentManager {

    private static final String REPORT_PATH =
            "target/extent-report/ExtentReport.html";

    private static final ExtentReports EXTENT =
            createInstance();

    private ExtentManager() {
    }

    public static ExtentReports getInstance() {
        return EXTENT;
    }

    private static ExtentReports createInstance() {

        File reportFile =
                new File(REPORT_PATH);

        File parent =
                reportFile.getParentFile();

        if (parent != null) {
            parent.mkdirs();
        }

        ExtentSparkReporter reporter =
                new ExtentSparkReporter(
                        reportFile
                );

        reporter.config()
                .setDocumentTitle(
                        "API Automation Report"
                );

        reporter.config()
                .setReportName(
                        "REST Assured API Test Report"
                );

        ExtentReports extent =
                new ExtentReports();

        extent.attachReporter(
                reporter
        );

        extent.setSystemInfo(
                "Framework",
                "REST Assured + TestNG"
        );

        extent.setSystemInfo(
                "Reporting",
                "ExtentReports"
        );

        return extent;
    }

    public static synchronized void flush() {
        EXTENT.flush();
    }
}