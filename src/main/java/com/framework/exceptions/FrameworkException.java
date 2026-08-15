package com.framework.exceptions;

/**
 * Unchecked base exception for all framework-level failures (config
 * loading, file I/O, serialization). Kept distinct from {@link ApiException}
 * and {@link ValidationException} so callers can catch selectively.
 */
public class FrameworkException extends RuntimeException {

    public FrameworkException(String message) {
        super(message);
    }

    public FrameworkException(String message, Throwable cause) {
        super(message, cause);
    }
}
