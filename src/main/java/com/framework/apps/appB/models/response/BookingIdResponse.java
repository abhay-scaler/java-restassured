package com.framework.apps.appB.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /booking's actual response shape - {"bookingid": N, "booking": {...}} -
 * confirmed against the live API before writing this class.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingIdResponse {
    private int bookingid;
    private BookingResponse booking;
}
