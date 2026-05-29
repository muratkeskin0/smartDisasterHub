package com.caglamurat.smartDisasterHub.dto.auth;

import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Base authentication response containing token and user details
 * Used as parent class for LoginResponse and RegisterResponse
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {

    private String token;
    
    @lombok.Builder.Default
    private String type = "Bearer";
    
    private UserDTO user;
}

