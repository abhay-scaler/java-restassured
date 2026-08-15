package com.framework.reporting;

import com.aventstack.extentreports.ExtentTest;

/**
 * Holds the current test's {@link ExtentTest} per thread. Required because
 * TestNG can run test methods on multiple threads in parallel — without a
 * ThreadLocal here, log lines from one test would bleed into another
 * test's report node.
 */
public final class ExtentTestManager {

    private static final ThreadLocal<ExtentTest> TEST = new ThreadLocal<>();

    private ExtentTestManager() {
    }

    public static void startTest(String name, String description) {
        ExtentTest test = ExtentManager.getInstance().createTest(name, description);
        TEST.set(test);
    }

    public static ExtentTest getTest() {
        return TEST.get();
    }

    public static void unload() {
        TEST.remove();
    }
}
