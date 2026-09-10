package com.framework.apps.appB.models.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body for PATCH /booking/{id}. Unlike Create/UpdateBookingRequest,
 * totalprice/depositpaid are boxed (Integer/Boolean) rather than
 * primitive, so an unset field is genuinely null and gets omitted by
 * @JsonInclude(NON_NULL) - a primitive would always serialize (as 0/false),
 * defeating the point of a partial-update body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PartialBookingRequest {
    private String firstname;
    private String lastname;
    private Integer totalprice;
    private Boolean depositpaid;
    private BookingDates bookingdates;
    private String additionalneeds;
}
