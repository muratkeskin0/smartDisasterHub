package com.caglamurat.smartDisasterHub.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private UserRoleDTO role;
    private Boolean isEmailVerified;
    /** New email awaiting confirmation; active login email stays {@link #email} until confirmed. */
    private String pendingEmail;
    private Boolean emailChangePending;
    private String profileImageBase64;  // Base64 encoded image
    private String profileImageContentType;
    private Instant createdAt;
    private Instant updatedAt;
}

