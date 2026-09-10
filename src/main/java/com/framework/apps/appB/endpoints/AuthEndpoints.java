package com.framework.apps.appB.endpoints;

/**
 * Restful Booker's own login endpoint. Not related to the framework's
 * AuthProvider - this is App B's business API, called like any other
 * endpoint through RestClient.
 */
public final class AuthEndpoints {

    private AuthEndpoints() {
    }

    public static final String AUTH = "/auth";
}
