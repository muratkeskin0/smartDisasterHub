package com.caglamurat.smartDisasterHub.dto.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for token verification operation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VerifyTokenResponse {

    private boolean valid;
    private String email;  // Email from token (if valid)
    private String role;   // Role from token (if valid)
}

