package com.framework.config;

import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for the multi-app configuration mechanism itself -
 * filesystem/configuration only, no live HTTP calls to either
 * application's real API. Every test here uses {@link ConfigManager#loadFor}
 * / {@link ConfigManager#resolveRawProperty}, the stateless, parameterized
 * entry points added in Step 1 specifically so App A's and App B's
 * configuration can be resolved side by side in one process without
 * touching the ambient ThreadLocal that -Dapp/-Denv drive - no test here
 * ever calls System.setProperty, and none depends on which application the
 * outer `mvn test` invocation happened to select.
 *
 * Runs under either app's suite (it doesn't touch either app's live API),
 * so it also stands as evidence that adding this coverage required no
 * change to ServiceConfig, AuthProvider, RestClient, or RequestSpecFactory.
 */
@Epic("Framework")
@Feature("Multi-application configuration")
public class ConfigManagerTests {

    private static final String APP_A = "appA";
    private static final String APP_B = "appB";
    private static final String QA = "qa";

    // ------------------------------------------------------------------
    // 1-2: app+env resolves the right application's configuration
    // ------------------------------------------------------------------

    @Test(groups = {"regression"}, description = "appA + qa resolves App A's own configuration")
    @Severity(SeverityLevel.BLOCKER)
    public void testAppAQaResolvesAppAConfiguration() {
        AppConfig config = ConfigManager.loadFor(APP_A, QA);

        assertThat(config.baseUrl()).isEqualTo("https://reqres.in");
        assertThat(config.basePath()).isEqualTo("/api");
        assertThat(config.authType()).isEqualTo("API_KEY");
    }

    @Test(groups = {"regression"}, description = "appB + qa resolves App B's own configuration")
    @Severity(SeverityLevel.BLOCKER)
    public void testAppBQaResolvesAppBConfiguration() {
        AppConfig config = ConfigManager.loadFor(APP_B, QA);

        assertThat(config.baseUrl()).isEqualTo("https://restful-booker.herokuapp.com");
        assertThat(config.authType()).isEqualTo("BASIC");
        assertThat(config.authUsername()).isEqualTo("admin");
    }

    // ------------------------------------------------------------------
    // 3: both applications resolvable independently in one process
    // ------------------------------------------------------------------

    @Test(groups = {"regression"},
            description = "App A and App B resolve independently in the same test process via the stateless loadFor mechanism")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Neither call touches the ambient ThreadLocal ConfigManager.getConfig() relies on - " +
            "loadFor() is what makes this side-by-side comparison possible at all.")
    public void testAppAAndAppBResolveIndependentlyInSameProcess() {
        AppConfig appAConfig = ConfigManager.loadFor(APP_A, QA);
        AppConfig appBConfig = ConfigManager.loadFor(APP_B, QA);

        assertThat(appAConfig.baseUrl()).isEqualTo("https://reqres.in");
        assertThat(appBConfig.baseUrl()).isEqualTo("https://restful-booker.herokuapp.com");
        assertThat(appAConfig.baseUrl()).isNotEqualTo(appBConfig.baseUrl());
    }

    // ------------------------------------------------------------------
    // 4-5: no accidental cross-application leakage
    // ------------------------------------------------------------------

    @Test(groups = {"regression", "negative"}, description = "App A's resolved base URL never accidentally matches App B's")
    @Severity(SeverityLevel.CRITICAL)
    public void testAppAPropertyNeverResolvesFromAppB() {
        AppConfig appAConfig = ConfigManager.loadFor(APP_A, QA);

        assertThat(appAConfig.baseUrl()).isNotEqualTo("https://restful-booker.herokuapp.com");
        assertThat(appAConfig.authType()).isNotEqualTo("BASIC");
    }

    @Test(groups = {"regression", "negative"}, description = "App B's resolved base URL never accidentally matches App A's")
    @Severity(SeverityLevel.CRITICAL)
    public void testAppBPropertyNeverResolvesFromAppA() {
        AppConfig appBConfig = ConfigManager.loadFor(APP_B, QA);

        assertThat(appBConfig.baseUrl()).isNotEqualTo("https://reqres.in");
        assertThat(appBConfig.authType()).isNotEqualTo("API_KEY");
    }

    // ------------------------------------------------------------------
    // 6: default.properties fallback still works for both applications
    // ------------------------------------------------------------------

    @Test(groups = {"regression"},
            description = "config/default.properties fallback applies to both applications for keys their own env file omits, " +
                    "while each keeps its own explicit override for keys it does set")
    @Severity(SeverityLevel.NORMAL)
    @Description("appA/dev.properties and appB/dev.properties both omit connection.timeout/socket.timeout, " +
            "so both must fall back to the same shared default.properties value - while each still keeps " +
            "its own distinct auth.type, which its dev.properties does set explicitly.")
    public void testDefaultPropertiesFallbackAppliesToBothApplications() {
        AppConfig appADev = ConfigManager.loadFor(APP_A, "dev");
        AppConfig appBDev = ConfigManager.loadFor(APP_B, "dev");

        assertThat(appADev.connectionTimeout()).isEqualTo(15000);
        assertThat(appBDev.connectionTimeout()).isEqualTo(15000);
        assertThat(appADev.socketTimeout()).isEqualTo(30000);
        assertThat(appBDev.socketTimeout()).isEqualTo(30000);

        assertThat(appADev.authType()).isEqualTo("API_KEY");
        assertThat(appBDev.authType()).isEqualTo("BASIC");
    }

    // ------------------------------------------------------------------
    // 7: system-property override precedence, for both applications
    // ------------------------------------------------------------------

    @Test(groups = {"regression"},
            description = "A -D system property overrides each application's own file value, with the expected precedence")
    @Severity(SeverityLevel.NORMAL)
    @Description("Uses config.override.probe, a fixed, always-present test-support system property " +
            "(pom.xml systemPropertyVariables) rather than mutating System properties from inside this test, " +
            "which would be unsafe to do from a running test under parallel execution. Each app's own qa.properties " +
            "sets a distinct file value for the same key, so a passing test proves the override genuinely wins, " +
            "not merely that the key is present.")
    public void testSystemPropertyOverridesFileValueForBothApplications() {
        String override = System.getProperty("config.override.probe");
        assertThat(override).as("test-support system property must be set via pom.xml").isEqualTo("system-property-wins");

        String appAResolved = ConfigManager.resolveRawProperty(APP_A, QA, "config.override.probe");
        String appBResolved = ConfigManager.resolveRawProperty(APP_B, QA, "config.override.probe");

        assertThat(appAResolved).isEqualTo("system-property-wins");
        assertThat(appBResolved).isEqualTo("system-property-wins");
    }

    // ------------------------------------------------------------------
    // 8-9: validation errors
    // ------------------------------------------------------------------

    @Test(groups = {"regression", "negative"}, description = "An unknown application produces a clear, specific validation error")
    @Severity(SeverityLevel.NORMAL)
    public void testUnknownApplicationProducesValidationError() {
        assertThatThrownBy(() -> ConfigManager.loadFor("appZ", QA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown application 'appZ'")
                .hasMessageContaining("Expected configuration directory");
    }

    @Test(groups = {"regression", "negative"},
            description = "A known application with an unknown environment produces a distinct, specific configuration-file error")
    @Severity(SeverityLevel.NORMAL)
    public void testKnownApplicationUnknownEnvironmentProducesConfigurationFileError() {
        assertThatThrownBy(() -> ConfigManager.loadFor(APP_A, "bogusenv"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Configuration file not found for application 'appA' and environment 'bogusenv'");
    }

    // ------------------------------------------------------------------
    // ServiceConfig's underlying data: isolated per application
    // ------------------------------------------------------------------

    @Test(groups = {"regression"},
            description = "The raw services.configtestonly.* data ServiceConfig reads is isolated per application")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Proves the mechanism ServiceConfig itself relies on (ConfigManager's resolved property map) " +
            "keeps a same-named service's values distinct per application - without touching ServiceConfig, " +
            "which only ever reads whichever application is ambient for the current thread and so cannot be " +
            "asked for two applications' values side by side in one test the way this stateless check can. " +
            "The service name itself is deliberately \"configtestonly\", not a plausible real service name " +
            "like \"orders\", so this can never be mistaken for a real service dependency.")
    public void testServiceConfigUnderlyingDataIsIsolatedPerApplication() {
        String appAConfigTestUrl = ConfigManager.resolveRawProperty(APP_A, QA, "services.configtestonly.base.url");
        String appBConfigTestUrl = ConfigManager.resolveRawProperty(APP_B, QA, "services.configtestonly.base.url");

        assertThat(appAConfigTestUrl).isEqualTo("https://appa-configtestonly.test.example.com");
        assertThat(appBConfigTestUrl).isEqualTo("https://appb-configtestonly.test.example.com");
        assertThat(appAConfigTestUrl).isNotEqualTo(appBConfigTestUrl);
    }

    // ------------------------------------------------------------------
    // Parallel validation: concurrent resolution never leaks
    // ------------------------------------------------------------------

    @Test(groups = {"regression"},
            description = "Resolving App A and App B configuration concurrently from separate threads never leaks one into the other")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Deterministic and network-free: this only exercises loadFor()'s file-read + system-property " +
            "merge, run concurrently across a small fixed thread pool, interleaving both applications.")
    public void testConcurrentResolutionOfBothApplicationsDoesNotLeak() throws Exception {
        int tasksPerApp = 10;
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            List<Callable<String[]>> tasks = new java.util.ArrayList<>();

            for (int i = 0; i < tasksPerApp; i++) {
                tasks.add(() -> new String[]{APP_A, ConfigManager.loadFor(APP_A, QA).baseUrl()});
                tasks.add(() -> new String[]{APP_B, ConfigManager.loadFor(APP_B, QA).baseUrl()});
            }

            List<Future<String[]>> futures = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);

            for (Future<String[]> future : futures) {
                String[] result = future.get();
                String requestedApp = result[0];
                String resolvedBaseUrl = result[1];

                if (requestedApp.equals(APP_A)) {
                    assertThat(resolvedBaseUrl).isEqualTo("https://reqres.in");
                } else {
                    assertThat(resolvedBaseUrl).isEqualTo("https://restful-booker.herokuapp.com");
                }
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
