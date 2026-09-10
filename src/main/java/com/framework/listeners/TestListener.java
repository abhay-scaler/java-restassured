package com.framework.listeners;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import com.framework.config.ConfigManager;
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
        startTestNode(result, description);
        log.info("---> Starting test: {}", name);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        ensureTestNode(result).log(Status.PASS, "Test passed");
        log.info("<--- PASSED: {}", result.getMethod().getMethodName());
        ExtentTestManager.unload();
    }

    @Override
    public void onTestFailure(ITestResult result) {
        Throwable throwable = result.getThrowable();
        if (throwable != null) {
            ensureTestNode(result).log(Status.FAIL, throwable);
        } else {
            ensureTestNode(result).log(Status.FAIL, "Test failed");
        }
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
        ensureTestNode(result).log(Status.SKIP, "Skipped: " + reason);
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

    /**
     * Returns the current thread's ExtentTest, creating one on the fly if
     * onTestStart never ran for this result. This happens when a test is
     * skipped because an upstream @BeforeMethod/@BeforeClass step failed —
     * TestNG still calls onTestSkipped, but never called onTestStart, so
     * without this fallback ExtentTestManager.getTest() would return null
     * and the next line would NPE instead of reporting the skip.
     */
    private ExtentTest ensureTestNode(ITestResult result) {
        ExtentTest test = ExtentTestManager.getTest();
        if (test == null) {
            String name = result.getMethod().getMethodName();
            test = startTestNode(result, name);
        }
        return test;
    }

    /**
     * Creates the Extent node for a test result, identified by
     * "ClassName.methodName" rather than the bare method name (which is
     * ambiguous once a suite mixes more than one test class), and tagged
     * with the active application as an Extent category so runs stay
     * filterable/attributable in the report regardless of which
     * application's suite produced it.
     */
    private ExtentTest startTestNode(ITestResult result, String description) {
        String qualifiedName = result.getTestClass().getRealClass().getSimpleName()
                + "." + result.getMethod().getMethodName();
        ExtentTestManager.startTest(qualifiedName, description);
        ExtentTestManager.getTest().assignCategory(ConfigManager.getApplication());
        return ExtentTestManager.getTest();
    }
}
