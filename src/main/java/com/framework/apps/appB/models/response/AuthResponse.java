package com.framework.apps.appB.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Covers both of Restful Booker's real /auth response shapes: a
 * successful login returns {"token": "..."}, a failed one returns
 * {"reason": "Bad credentials"} - both fields nullable so one class
 * deserializes either.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthResponse {
    private String token;
    private String reason;
}
