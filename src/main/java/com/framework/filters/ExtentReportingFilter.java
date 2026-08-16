package com.framework.filters;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.markuputils.CodeLanguage;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;
import com.framework.reporting.ExtentTestManager;
import com.framework.utils.HttpLogFormatter;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

/**
 * Attaches full request/response detail (method, URI, headers, body,
 * status, timing) to the current test's Extent node as a collapsible
 * child step. This is what makes the HTML report self-contained — a
 * reviewer can see exactly what was sent and received without opening
 * Allure or the log files.
 *
 * Runs alongside {@link RequestResponseLoggingFilter} (SLF4J) and Allure's
 * own filter. Each backend has its own filter so removing one doesn't
 * affect the others.
 */
public class ExtentReportingFilter implements Filter {

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                            FilterableResponseSpecification responseSpec,
                            FilterContext ctx) {

        long start = System.currentTimeMillis();
        Response response = ctx.next(requestSpec, responseSpec);
        long durationMs = System.currentTimeMillis() - start;

        // No active Extent test on this thread — e.g. a call made outside
        // a @Test method (@BeforeSuite health check, etc.). Nothing to
        // attach to; don't fail the request over a reporting gap.
        ExtentTest test = ExtentTestManager.getTest();
        if (test == null) {
            return response;
        }

        try {
            logToReport(test, requestSpec, response, durationMs);
        } catch (Exception reportingFailure) {
            // Reporting must never break a test run. If rendering the node
            // fails for any reason, note it plainly and move on.
            test.warning("Failed to render request/response detail: "
                    + reportingFailure.getMessage());
        }

        return response;
    }

    private void logToReport(ExtentTest test,
                              FilterableRequestSpecification requestSpec,
                              Response response,
                              long durationMs) {

        String method = requestSpec.getMethod();
        String uri = requestSpec.getURI();
        int status = response.getStatusCode();

        ExtentTest node = test.createNode(method + " " + uri);

        node.info(MarkupHelper.createLabel("REQUEST", ExtentColor.BLUE));
        node.info("Headers:<br>" + HttpLogFormatter.formatHeaders(requestSpec.getHeaders())
                .replace("\n", "<br>"));

        String requestBody = HttpLogFormatter.formatBody(requestSpec.getBody());
        if (requestBody != null && !requestBody.isBlank()) {
            node.info(MarkupHelper.createCodeBlock(requestBody, CodeLanguage.JSON));
        }

        ExtentColor statusColor = statusColor(status);
        node.info(MarkupHelper.createLabel(
                "RESPONSE [" + status + "] — " + durationMs + " ms", statusColor));
        node.info("Headers:<br>" + HttpLogFormatter.formatHeaders(response.getHeaders())
                .replace("\n", "<br>"));

        String responseBody = response.getBody().asString();
        if (responseBody != null && !responseBody.isBlank()) {
            node.info(MarkupHelper.createCodeBlock(
                    HttpLogFormatter.formatBody(responseBody), CodeLanguage.JSON));
        }
    }

    private ExtentColor statusColor(int status) {
        if (status >= 200 && status < 300) return ExtentColor.GREEN;
        if (status >= 400) return ExtentColor.RED;
        return ExtentColor.ORANGE;
    }
}
