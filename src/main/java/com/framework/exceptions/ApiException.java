package com.framework.exceptions;

/**
 * Thrown for failures executing an HTTP call itself (connection refused,
 * timeout after retries exhausted, malformed request). Not for assertion
 * failures on a returned response — those are {@link ValidationException}.
 */
public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
