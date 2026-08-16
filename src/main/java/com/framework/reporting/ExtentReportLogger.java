package com.framework.reporting;

import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.markuputils.ExtentColor;
import com.aventstack.extentreports.markuputils.MarkupHelper;

import java.util.Map;

/**
 * Centralized Extent logging utility.
 *
 * Keeps reporting code out of RestClient and the TestNG listener.
 */
public final class ExtentReportLogger {

    private ExtentReportLogger() {
    }

    public static void info(String message) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        ExtentTestManager.getTest().log(
                Status.INFO,
                message
        );
    }

    public static void pass(String message) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        ExtentTestManager.getTest().log(
                Status.PASS,
                message
        );
    }

    public static void warning(String message) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        ExtentTestManager.getTest().log(
                Status.WARNING,
                message
        );
    }

    public static void fail(String message) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        ExtentTestManager.getTest().log(
                Status.FAIL,
                message
        );
    }

    public static void section(
            String title,
            ExtentColor color) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        ExtentTestManager.getTest().log(
                Status.INFO,
                MarkupHelper.createLabel(
                        title,
                        color
                )
        );
    }

    public static void code(
            String title,
            String content) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        String safeContent =
                content == null
                        ? ""
                        : content;

        ExtentTestManager.getTest().log(
                Status.INFO,
                "<b>" + escapeHtml(title) + "</b><br>"
                        + "<pre>"
                        + escapeHtml(safeContent)
                        + "</pre>"
        );
    }

    public static void map(
            String title,
            Map<?, ?> values) {

        if (!ExtentTestManager.hasTest()) {
            return;
        }

        if (values == null || values.isEmpty()) {
            info("<b>" + escapeHtml(title) + ":</b> None");
            return;
        }

        StringBuilder html =
                new StringBuilder();

        html.append("<b>")
                .append(escapeHtml(title))
                .append(":</b><br>");

        html.append("<table border='1' "
                + "style='border-collapse:collapse;"
                + "width:100%'>");

        html.append("<tr>")
                .append("<th style='padding:5px'>Name</th>")
                .append("<th style='padding:5px'>Value</th>")
                .append("</tr>");

        values.forEach((key, value) -> {

            html.append("<tr>")
                    .append("<td style='padding:5px'>")
                    .append(escapeHtml(
                            String.valueOf(key)
                    ))
                    .append("</td>")
                    .append("<td style='padding:5px'>")
                    .append(escapeHtml(
                            String.valueOf(value)
                    ))
                    .append("</td>")
                    .append("</tr>");
        });

        html.append("</table>");

        info(html.toString());
    }

    public static String escapeHtml(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}