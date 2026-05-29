package com.caglamurat.smartDisasterHub.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateResultDTO {
    private UserDTO user;
    private String message;
    private boolean emailChangePending;
    private String activationSentTo;
}
