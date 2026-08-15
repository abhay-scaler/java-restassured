package com.framework.exceptions;

/**
 * Thrown by {@code ResponseValidator} / {@code SchemaValidator} when a
 * response fails an assertion. Extends AssertionError so TestNG still
 * reports it as a test failure (not an error) in the results.
 */
public class ValidationException extends AssertionError {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
