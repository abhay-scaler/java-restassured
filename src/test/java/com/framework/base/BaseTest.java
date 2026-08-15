package com.framework.base;

import com.framework.clients.RestClient;
import com.framework.config.AppConfig;
import com.framework.config.ConfigManager;
import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

/**
 * Every test class extends this. Centralizes per-test setup (fresh
 * RestClient so no state leaks between tests) and captures response/failure
 * context into Allure automatically on failure.
 */
public abstract class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);
    protected RestClient client;

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        AppConfig config = ConfigManager.getConfig();
        log.info("Running against environment [{}], base URL: {}",
                ConfigManager.getEnvironment().getValue(), config.baseUrl());
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        client = new RestClient();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        if (!result.isSuccess() && result.getThrowable() != null) {
            Allure.addAttachment("Failure stacktrace", result.getThrowable().toString());
        }
    }
}
