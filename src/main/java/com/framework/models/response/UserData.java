package com.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserData {
    private int id;
    private String email;
    @com.fasterxml.jackson.annotation.JsonProperty("first_name")
    private String firstName;
    @com.fasterxml.jackson.annotation.JsonProperty("last_name")
    private String lastName;
    private String avatar;
}
