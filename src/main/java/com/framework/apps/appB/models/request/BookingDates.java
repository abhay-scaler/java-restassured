package com.framework.apps.appB.models.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Used both when building a request body and when deserializing a
 * booking response (see {@code models.response.BookingResponse}) -
 * Restful Booker uses the identical shape both ways, so one class
 * covers both directions rather than duplicating it per package.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BookingDates {
    private String checkin;
    private String checkout;
}
