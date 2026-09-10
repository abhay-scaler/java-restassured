package com.framework.apps.appB.api;

import com.framework.apps.appB.endpoints.AuthEndpoints;
import com.framework.apps.appB.models.request.AuthRequest;
import com.framework.apps.appB.models.response.AuthResponse;
import com.framework.clients.RestClient;
import io.restassured.response.Response;

/**
 * Thin wrapper over the shared {@link RestClient} for Restful Booker's
 * own POST /auth. This is App B's business-domain login flow, not a
 * framework auth strategy - AuthProvider has no knowledge this exists.
 *
 * The resulting token is returned as a plain String; callers hold it in
 * whatever local scope their test needs (a method-local variable) and
 * pass it on to {@link BookingApi} explicitly - never stored here,
 * never static, never shared across tests or threads.
 */
public class AuthApi {

    private final RestClient client;

    public AuthApi(RestClient client) {
        this.client = client;
    }

    /** Returns the raw response, e.g. to assert on a failed-login shape ({"reason": "..."}). */
    public Response createTokenRaw(AuthRequest request) {
        return client.post(AuthEndpoints.AUTH, request);
    }

    /** Convenience for the success path: returns the issued token directly. */
    public String createToken(AuthRequest request) {
        return createTokenRaw(request).as(AuthResponse.class).getToken();
    }
}
