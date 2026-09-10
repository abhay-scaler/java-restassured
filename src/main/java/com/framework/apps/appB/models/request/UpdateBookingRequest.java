package com.framework.apps.appB.models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Full replacement body for PUT /booking/{id}. Same shape as
 * CreateBookingRequest but kept as a distinct type, matching this
 * framework's existing convention (e.g. App A's CreateUserRequest /
 * UpdateUserRequest) of not coupling create and update payloads just
 * because they happen to look alike today.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateBookingRequest {
    private String firstname;
    private String lastname;
    private int totalprice;
    private boolean depositpaid;
    private BookingDates bookingdates;
    private String additionalneeds;
}
