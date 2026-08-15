package com.framework.filters;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs each request/response pair through SLF4J instead of RestAssured's
 * own console logging, so output goes through logback (files + console,
 * consistent formatting) rather than straight to stdout.
 */
public class RequestResponseLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger("API");

    @Override
    public Response filter(FilterableRequestSpecification requestSpec,
                            FilterableResponseSpecification responseSpec,
                            FilterContext ctx) {

        long start = System.currentTimeMillis();
        log.info(">> {} {}", requestSpec.getMethod(), requestSpec.getURI());
        if (!requestSpec.getHeaders().asList().isEmpty()) {
            log.debug(">> Headers: {}", requestSpec.getHeaders());
        }
        if (requestSpec.getBody() != null) {
            log.debug(">> Body: {}", requestSpec.getBody().toString());
        }

        Response response = ctx.next(requestSpec, responseSpec);
        long durationMs = System.currentTimeMillis() - start;

        log.info("<< {} {} ({} ms)", response.getStatusCode(), requestSpec.getURI(), durationMs);
        log.debug("<< Body: {}", response.getBody().asPrettyString());

        return response;
    }
}
