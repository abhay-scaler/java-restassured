package com.framework.utils;

import io.restassured.http.Headers;

/**
 * Shared formatting used by every place that renders a request/response
 * (SLF4J logs, Extent report nodes). Centralizing it means "pretty-print
 * JSON" and "mask secrets" are each implemented once, not once per
 * reporting backend — so a fix or a new sensitive-header pattern only
 * needs to change here.
 */
public final class HttpLogFormatter {

    private HttpLogFormatter() {
    }

    /**
     * Pretty-prints a body for display. Accepts a POJO (serialized via
     * Jackson), a JSON string (re-formatted), or any other object/string
     * (returned as-is — e.g. form-encoded or XML payloads).
     */
    public static String formatBody(Object rawBody) {
        if (rawBody == null) {
            return null;
        }
        if (rawBody instanceof String stringBody) {
            String trimmed = stringBody.trim();
            if (trimmed.isEmpty()) {
                return "";
            }
            try {
                return JsonUtils.mapper().readTree(trimmed).toPrettyString();
            } catch (Exception notJson) {
                return stringBody;
            }
        }
        try {
            return JsonUtils.toPrettyJson(rawBody);
        } catch (Exception e) {
            return String.valueOf(rawBody);
        }
    }

    /**
     * Renders headers one-per-line, masking values for header names that
     * commonly carry secrets (Authorization, API keys, tokens, cookies) so
     * logs and HTML reports don't become a place credentials leak from.
     */
    public static String formatHeaders(Headers headers) {
        if (headers == null || headers.asList().isEmpty()) {
            return "(none)";
        }
        StringBuilder sb = new StringBuilder();
        headers.forEach(h -> sb.append(h.getName())
                .append(": ")
                .append(mask(h.getName(), h.getValue()))
                .append(System.lineSeparator()));
        return sb.toString().stripTrailing();
    }

    private static String mask(String headerName, String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String lower = headerName.toLowerCase();
        boolean sensitive = lower.contains("authorization")
                || lower.contains("api-key")
                || lower.contains("apikey")
                || lower.contains("token")
                || lower.contains("cookie")
                || lower.contains("secret");
        if (!sensitive) {
            return value;
        }
        if (value.length() <= 6) {
            return "****";
        }
        return value.substring(0, 4) + "****(masked)";
    }
}
