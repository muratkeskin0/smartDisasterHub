package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.integration.RedditIntegrationSettingsDTO;
import com.caglamurat.smartDisasterHub.dto.integration.RedditIntegrationSettingsUpdateDTO;
import com.caglamurat.smartDisasterHub.dto.integration.RedditIntegrationStatusDTO;
import com.caglamurat.smartDisasterHub.job.RedditAnalysisJob;
import com.caglamurat.smartDisasterHub.job.RedditFetchJob;
import com.caglamurat.smartDisasterHub.service.integration.RedditIntegrationSettingsService;
import com.caglamurat.smartDisasterHub.service.reddit.RedditOAuthTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/integrations/reddit")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class RedditIntegrationController {

    private final RedditIntegrationSettingsService integrationSettingsService;
    private final RedditOAuthTokenProvider oauthTokenProvider;
    private final RedditFetchJob redditFetchJob;
    private final RedditAnalysisJob redditAnalysisJob;

    @GetMapping
    public ResponseEntity<ApiResponse<RedditIntegrationSettingsDTO>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(integrationSettingsService.getSettings()));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<RedditIntegrationStatusDTO>> getStatus() {
        return ResponseEntity.ok(ApiResponse.success(integrationSettingsService.getStatus()));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<RedditIntegrationSettingsDTO>> updateSettings(
            @Valid @RequestBody RedditIntegrationSettingsUpdateDTO update) {
        log.info("REST request to update Reddit integration settings");
        RedditIntegrationSettingsDTO saved = integrationSettingsService.updateSettings(update);
        oauthTokenProvider.clearTokenCache();
        return ResponseEntity.ok(ApiResponse.success("Reddit integration settings saved", saved));
    }

    @PostMapping("/test")
    public ResponseEntity<ApiResponse<RedditIntegrationSettingsDTO>> testConnection() {
        log.info("REST request to test Reddit integration connection");
        oauthTokenProvider.clearTokenCache();
        RedditIntegrationSettingsDTO result;
        try {
            oauthTokenProvider.getAccessToken();
            result = integrationSettingsService.recordTestResult(true, "OAuth token obtained successfully");
            return ResponseEntity.ok(ApiResponse.success("Reddit connection successful", result));
        } catch (Exception e) {
            log.warn("Reddit connection test failed: {}", e.getMessage());
            result = integrationSettingsService.recordTestResult(false, e.getMessage());
            return ResponseEntity.ok(ApiResponse.success("Reddit connection failed", result));
        }
    }

    @PostMapping("/fetch-now")
    public ResponseEntity<ApiResponse<Map<String, Object>>> fetchNow() {
        log.info("REST request to trigger Reddit fetch from integration settings");
        if (!integrationSettingsService.isConfigured()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Reddit OAuth is not configured", null));
        }
        try {
            redditFetchJob.executeFetchJob();
            Thread.sleep(1000);
            redditAnalysisJob.executeAnalysisJob();
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Fetch and analysis jobs completed");
            result.put("settings", integrationSettingsService.getSettings());
            return ResponseEntity.ok(ApiResponse.success("Reddit fetch triggered", result));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Interrupted while running fetch", null));
        } catch (Exception e) {
            log.error("Fetch now failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Fetch failed: " + e.getMessage(), null));
        }
    }
}
