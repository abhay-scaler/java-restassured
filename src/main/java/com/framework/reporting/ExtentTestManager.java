package com.framework.reporting;

import com.aventstack.extentreports.ExtentTest;

/**
 * Thread-safe holder for the current ExtentTest.
 *
 * TestNG may execute tests in parallel, therefore each thread gets
 * its own ExtentTest instance.
 */
public final class ExtentTestManager {

    private static final ThreadLocal<ExtentTest> TEST =
            new ThreadLocal<>();

    private ExtentTestManager() {
    }

    public static void startTest(
            String name,
            String description) {

        ExtentTest test =
                ExtentManager.getInstance()
                        .createTest(name, description);

        TEST.set(test);
    }

    public static ExtentTest getTest() {
        return TEST.get();
    }

    public static boolean hasTest() {
        return TEST.get() != null;
    }

    public static void unload() {
        TEST.remove();
    }
}