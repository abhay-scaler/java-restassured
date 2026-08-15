package com.framework.retry;

import com.framework.config.ConfigManager;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * Reruns a failed @Test up to the configured max.retry.count before it's
 * reported as a final failure. This is for flaky-infrastructure resilience
 * (a genuine transient blip), not a substitute for fixing a broken test —
 * keep max.retry.count low (1-2) so a truly broken test still fails fast.
 */
public class RetryAnalyzer implements IRetryAnalyzer {

    private int retryCount = 0;

    @Override
    public boolean retry(ITestResult result) {
        int maxRetries = ConfigManager.getConfig().maxRetryCount();
        if (retryCount < maxRetries) {
            retryCount++;
            result.setStatus(ITestResult.FAILURE);
            return true;
        }
        return false;
    }
}
