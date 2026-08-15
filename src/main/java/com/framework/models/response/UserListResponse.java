package com.framework.models.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserListResponse {
    private int page;
    @com.fasterxml.jackson.annotation.JsonProperty("per_page")
    private int perPage;
    private int total;
    @com.fasterxml.jackson.annotation.JsonProperty("total_pages")
    private int totalPages;
    private List<UserData> data;
}
