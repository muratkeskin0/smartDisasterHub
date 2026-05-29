package com.caglamurat.smartDisasterHub.dto.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditIntegrationSettingsDTO {

    private String clientId;
    private boolean clientSecretConfigured;
    private String username;
    private boolean passwordConfigured;
    private String userAgent;
    private String subreddits;
    private boolean enabled;
    private boolean configured;
    private String configSource;

    private Instant lastTestAt;
    private Boolean lastTestSuccess;
    private String lastTestMessage;
    private Instant lastFetchAt;
    private Integer lastFetchCount;
}
