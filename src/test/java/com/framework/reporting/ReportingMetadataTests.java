package com.framework.reporting;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.model.Category;
import com.framework.base.BaseTest;
import com.framework.config.ConfigManager;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Network-free verification that {@link com.framework.listeners.TestListener}
 * and {@link ExtentManager} correctly attribute every Extent node to the
 * active application, identify it by class + method (not just the bare
 * method name), and still render PASS/FAIL/SKIP status and failure detail
 * exactly as before. No HTTP call is made anywhere in this class.
 *
 * Application-agnostic by construction: every assertion here reads
 * {@link ConfigManager#getApplication()} generically, so the same class
 * proves the same properties for App A and App B, whichever suite runs it.
 *
 * Deliberately not referenced by any {@code suites/<app>/*.xml} - the
 * intentional failure/skip below exist purely to exercise
 * {@code TestListener}'s real onTestFailure/onTestSkipped callbacks and
 * are meant to be run ad hoc (e.g. via {@code -Dtest=ReportingMetadataTests}
 * against a suite that registers {@code TestListener}), never as part of
 * the live App A/App B suites, where an always-failing test would break
 * the build.
 */
public class ReportingMetadataTests extends BaseTest {

    @Test(description = "Extent node for a passing test is named ClassName.methodName and "
            + "carries the active application as a category")
    public void testPassingNodeIdentifiesClassMethodAndApplication() {
        ExtentTest test = ExtentTestManager.getTest();
        Assert.assertNotNull(test,
                "TestListener should already have started an Extent node for this test");

        String expectedApp = ConfigManager.getApplication();
        String nodeName = test.getModel().getName();

        Assert.assertTrue(nodeName.contains(getClass().getSimpleName()),
                "Extent node name should include the test class name, was: " + nodeName);
        Assert.assertTrue(
                nodeName.contains("testPassingNodeIdentifiesClassMethodAndApplication"),
                "Extent node name should include the method name, was: " + nodeName);

        Set<String> categories = test.getModel().getCategorySet().stream()
                .map(Category::getName)
                .collect(Collectors.toSet());
        Assert.assertTrue(categories.contains(expectedApp),
                "Extent node category should contain the active application [" + expectedApp
                        + "], was: " + categories);
    }

    @Test(description = "Intentional failure - proves TestListener.onTestFailure still renders "
            + "failure/exception detail after the reporting changes in this branch")
    public void testFailureIsRenderedWithDetail() {
        Assert.fail("Intentional failure for reporting verification - not a real defect");
    }

    @Test(description = "Intentional skip - proves TestListener.onTestSkipped still renders "
            + "skip status after the reporting changes in this branch")
    public void testSkipIsRendered() {
        throw new SkipException("Intentional skip for reporting verification - not a real defect");
    }
}
