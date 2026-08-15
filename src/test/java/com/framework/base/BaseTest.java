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

public abstract class BaseTest {

    protected static final Logger log =
            LoggerFactory.getLogger(BaseTest.class);

    protected RestClient client;

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {

        AppConfig config = ConfigManager.getConfig();

        log.info("========================================");
        log.info(
                "Test Environment : [{}]",
                ConfigManager.getEnvironment().getValue()
        );
        log.info(
                "Base URL         : [{}]",
                config.baseUrl()
        );
        log.info(
                "Base Path        : [{}]",
                config.basePath()
        );
        log.info(
                "Auth Type        : [{}]",
                config.authType()
        );
        log.info(
                "API Key Header   : [{}]",
                config.apiKeyName()
        );
        log.info(
                "Max Retry Count  : [{}]",
                config.maxRetryCount()
        );
        log.info("========================================");
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        client = new RestClient();
    }

    /**
     * Provides the RestClient to test classes.
     */
    protected RestClient client() {
        return client;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {

        if (!result.isSuccess()
                && result.getThrowable() != null) {

            Allure.addAttachment(
                    "Failure stacktrace",
                    result.getThrowable().toString()
            );
        }
    }
}