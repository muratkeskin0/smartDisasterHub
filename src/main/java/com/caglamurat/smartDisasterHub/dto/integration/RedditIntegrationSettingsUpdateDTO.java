package com.caglamurat.smartDisasterHub.dto.integration;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditIntegrationSettingsUpdateDTO {

    @Size(max = 128)
    private String clientId;

    /** Leave blank to keep the existing secret. */
    @Size(max = 256)
    private String clientSecret;

    @Size(max = 128)
    private String username;

    /** Leave blank to keep the existing password. */
    @Size(max = 256)
    private String password;

    @Size(max = 255)
    private String userAgent;

    @Size(max = 500)
    private String subreddits;

    private Boolean enabled;
}
