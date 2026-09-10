package com.framework.apps.appB.tests;

import com.framework.base.BaseTest;
import com.framework.config.ServiceConfig;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Configuration only - no HTTP calls. Proves the real, unmodified
 * ServiceConfig class automatically becomes scoped to App B simply by
 * virtue of this suite running under -Dapp=appB; ServiceConfig itself
 * has no notion of "application" at all. Deliberately kept separate
 * from the live-API test classes in this package so the config-only
 * vs. live-API distinction stays obvious from the class name alone.
 *
 * The service name "configtestonly" is deliberate - not a plausible
 * real service name like "orders" - so this test-support config can
 * never be mistaken for a real service dependency.
 */
@Epic("Restful Booker API")
@Feature("Service configuration")
public class AppBServiceConfigTests extends BaseTest {

    @Test(groups = {"regression"},
            description = "ServiceConfig(\"configtestonly\") resolves App B's own test-only " +
                    "services.configtestonly.base.url, not App A's")
    @Severity(SeverityLevel.NORMAL)
    @Description("Config-only assertion - ServiceConfig never makes a network call on its own.")
    public void testNamedServiceResolvesAppBsTestValue() {
        ServiceConfig configTestOnly = ServiceConfig.of("configtestonly");

        assertThat(configTestOnly.baseUrl()).isEqualTo("https://appb-configtestonly.test.example.com");
        assertThat(configTestOnly.baseUrl()).isNotEqualTo("https://appa-configtestonly.test.example.com");
    }
}
