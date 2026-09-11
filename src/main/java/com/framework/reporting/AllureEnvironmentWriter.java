package com.framework.reporting;

import com.framework.config.ConfigManager;
import com.framework.constants.FrameworkConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * Writes {@code environment.properties} into the Allure results directory
 * so the generated Allure report's Environment widget is populated —
 * independently of ExtentReports, which sets the same Application/Environment
 * facts as its own report-level system info via {@link ConfigManager}.
 *
 * Allure only reads this file at report-generation time (e.g. {@code allure
 * serve}, {@code mvn allure:report}), so it just needs to exist in the
 * results directory by then; write timing within a suite run doesn't matter.
 */
public final class AllureEnvironmentWriter {

    private static final Logger log = LoggerFactory.getLogger(AllureEnvironmentWriter.class);

    private AllureEnvironmentWriter() {
    }

    /**
     * Overwrites {@code environment.properties} with the current thread's
     * ambient application/environment. Safe to call once per suite run —
     * every call writes the same two facts for the active -Dapp/-Denv, so a
     * repeat invocation (e.g. a future suite XML with more than one
     * {@code <test>} block) is a harmless, idempotent overwrite, not a race.
     *
     * Reporting must never break a test run: any failure here is logged as
     * a warning, never thrown.
     */
    public static void write() {
        File resultsDir = new File(FrameworkConstants.ALLURE_RESULTS_DIR);

        try {
            if (!resultsDir.exists() && !resultsDir.mkdirs() && !resultsDir.exists()) {
                throw new IOException("Unable to create directory: " + resultsDir.getAbsolutePath());
            }

            File environmentFile = new File(resultsDir, "environment.properties");

            try (Writer writer = new FileWriter(environmentFile)) {
                writer.write("Application=" + ConfigManager.getApplication() + System.lineSeparator());
                writer.write("Environment=" + ConfigManager.getEnvironment().getValue() + System.lineSeparator());
            }
        } catch (Exception writeFailure) {
            log.warn("Failed to write Allure environment.properties: {}", writeFailure.getMessage());
        }
    }
}
