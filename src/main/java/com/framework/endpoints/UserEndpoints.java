package com.framework.endpoints;

/**
 * Centralized endpoint definitions for the demo "Users" resource.
 * Keeping paths here (instead of scattered string literals in tests)
 * means an API version bump or path change is a one-file edit.
 */
public final class UserEndpoints {

    private UserEndpoints() {
    }

    public static final String USERS = "/users";
    public static final String USER_BY_ID = "/users/{id}";
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";
    public static final String DELAYED_USERS = "/users?delay={seconds}";
}
