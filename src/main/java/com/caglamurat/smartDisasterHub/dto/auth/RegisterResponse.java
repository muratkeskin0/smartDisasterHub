package com.caglamurat.smartDisasterHub.dto.auth;

import com.caglamurat.smartDisasterHub.dto.user.UserDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Response DTO for registration operation
 * Contains JWT token and newly registered user details
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RegisterResponse extends AuthResponse {

    private boolean emailVerificationRequired;
    private String activationSentTo;

    public RegisterResponse(boolean emailVerificationRequired, String activationSentTo, UserDTO user) {
        super(null, "Bearer", user);
        this.emailVerificationRequired = emailVerificationRequired;
        this.activationSentTo = activationSentTo;
    }
}

