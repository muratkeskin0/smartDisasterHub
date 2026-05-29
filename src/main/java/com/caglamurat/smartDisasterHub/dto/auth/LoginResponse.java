package com.caglamurat.smartDisasterHub.dto.auth;

import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Response DTO for login operation
 * Contains JWT token and authenticated user details
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class LoginResponse extends AuthResponse {

    public LoginResponse(String token, UserDTO user) {
        super(token, "Bearer", user);
    }
}

