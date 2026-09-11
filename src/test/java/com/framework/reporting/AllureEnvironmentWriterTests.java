package com.framework.reporting;

import com.framework.config.ConfigManager;
import com.framework.constants.FrameworkConstants;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Network-free verification that {@link TestListener#onStart} has already
 * written {@code target/allure-results/environment.properties} by the time
 * any {@code @Test} method runs, and that it carries the same
 * Application/Environment values {@link ConfigManager} resolves for the
 * active -Dapp/-Denv.
 *
 * Application-agnostic by construction, like {@link
 * com.framework.config.ConfigManagerTests}: every assertion reads {@link
 * ConfigManager#getApplication()}/{@link ConfigManager#getEnvironment()}
 * generically, so the same class proves the property for App A and App B,
 * whichever suite runs it. Registered in {@code testng.xml}/{@code
 * regression.xml} for both apps, matching where {@code ConfigManagerTests}
 * already lives, not in {@code smoke.xml} — smoke stays scoped to each
 * app's own critical-path checks.
 */
@Epic("Framework")
@Feature("Reporting")
public class AllureEnvironmentWriterTests {

    @Test(groups = {"regression"},
            description = "environment.properties exists and matches the active app/environment")
    @Severity(SeverityLevel.NORMAL)
    public void testEnvironmentPropertiesMatchesActiveConfig() throws IOException {
        File environmentFile = new File(FrameworkConstants.ALLURE_RESULTS_DIR, "environment.properties");

        assertThat(environmentFile).exists();

        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(environmentFile)) {
            properties.load(inputStream);
        }

        assertThat(properties.getProperty("Application")).isEqualTo(ConfigManager.getApplication());
        assertThat(properties.getProperty("Environment"))
                .isEqualTo(ConfigManager.getEnvironment().getValue());
    }
}
