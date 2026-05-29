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
public class RedditIntegrationStatusDTO {

    private boolean configured;
    private boolean enabled;
    private String configSource;
    private Instant lastTestAt;
    private Boolean lastTestSuccess;
    private String lastTestMessage;
}
