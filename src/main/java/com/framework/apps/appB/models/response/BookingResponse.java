package com.framework.apps.appB.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.framework.apps.appB.models.request.BookingDates;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The bare booking shape returned by GET /booking/{id}, and nested
 * inside {@link BookingIdResponse} for POST /booking.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingResponse {
    private String firstname;
    private String lastname;
    private int totalprice;
    private boolean depositpaid;
    private BookingDates bookingdates;
    private String additionalneeds;
}
