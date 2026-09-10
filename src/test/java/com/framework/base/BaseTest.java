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
 *
 * Thread-safety note: TestNG creates a single instance of each test class
 * and reuses it for every @Test method in that class — including when
 * parallel="methods" runs several of those methods concurrently on
 * different threads. A plain instance field for the client would then be
 * shared across threads: one thread's @BeforeMethod can overwrite the
 * field right after another thread's @BeforeMethod set it, and both
 * threads end up chaining .pathParam()/.queryParam() calls onto the same
 * RequestSpecification — which is exactly what caused parameters from
 * unrelated tests to bleed into each other's requests. A ThreadLocal
 * gives each thread its own independent RestClient regardless of
 * class-instance sharing.
 */
public abstract class BaseTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseTest.class);

    private static final ThreadLocal<RestClient> CLIENT = new ThreadLocal<>();

    @BeforeSuite(alwaysRun = true)
    public void beforeSuite() {
        AppConfig config = ConfigManager.getConfig();

        log.info("========================================");
        log.info("Application      : [{}]", ConfigManager.getApplication());
        log.info("Test Environment : [{}]", ConfigManager.getEnvironment().getValue());
        log.info("Base URL         : [{}]", config.baseUrl());
        log.info("Base Path        : [{}]", config.basePath());
        log.info("Auth Type        : [{}]", config.authType());
        log.info("API Key Header   : [{}]", config.apiKeyName());
        log.info("HTTP Retry Count : [{}]", config.httpRetryCount());
        log.info("Test Retry Count : [{}]", config.testRetryCount());
        log.info("========================================");
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        CLIENT.set(new RestClient());
    }

    /** Returns the RestClient for the current thread's in-progress test. */
    protected RestClient client() {
        RestClient client = CLIENT.get();
        if (client == null) {
            // Defensive fallback — should never happen since @BeforeMethod
            // always runs first, but avoids a confusing NPE if it ever does.
            client = new RestClient();
            CLIENT.set(client);
        }
        return client;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(ITestResult result) {
        if (!result.isSuccess() && result.getThrowable() != null) {
            Allure.addAttachment("Failure stacktrace", result.getThrowable().toString());
        }
        CLIENT.remove();
    }
}