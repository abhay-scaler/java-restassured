package com.framework.listeners;

import com.aventstack.extentreports.Status;
import com.framework.reporting.ExtentManager;
import com.framework.reporting.ExtentTestManager;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Bridges TestNG's lifecycle to both reporting backends so individual test
 * classes never call reporting APIs directly — they just write tests, and
 * pass/fail/skip states are captured centrally here.
 */
public class TestListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(TestListener.class);

    @Override
    public void onStart(ITestContext context) {
        log.info("===== Suite started: {} =====", context.getName());
    }

    @Override
    public void onTestStart(ITestResult result) {
        String name = result.getMethod().getMethodName();
        String description = result.getMethod().getDescription() != null
                ? result.getMethod().getDescription() : name;
        ExtentTestManager.startTest(name, description);
        log.info("---> Starting test: {}", name);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ExtentTestManager.getTest().log(Status.PASS, "Test passed");
        log.info("<--- PASSED: {}", result.getMethod().getMethodName());
        ExtentTestManager.unload();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        Throwable throwable = result.getThrowable();
        ExtentTestManager.getTest().log(Status.FAIL, throwable);
        log.error("<--- FAILED: {}", result.getMethod().getMethodName(), throwable);

        // Attach failure details to the Allure report too.
        String message = throwable != null ? throwable.toString() : "Unknown failure";
        Allure.addAttachment("Failure details", new ByteArrayInputStream(
                message.getBytes(StandardCharsets.UTF_8)));

        ExtentTestManager.unload();
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        String reason = result.getThrowable() != null
                ? result.getThrowable().getMessage() : "Skipped";
        ExtentTestManager.getTest().log(Status.SKIP, "Skipped: " + reason);
        log.warn("<--- SKIPPED: {} ({})", result.getMethod().getMethodName(), reason);
        ExtentTestManager.unload();
    }

    @Override
    public void onFinish(ITestContext context) {
        ExtentManager.flush();
        log.info("===== Suite finished: {} | Passed: {} Failed: {} Skipped: {} =====",
                context.getName(),
                context.getPassedTests().size(),
                context.getFailedTests().size(),
                context.getSkippedTests().size());
    }
}
