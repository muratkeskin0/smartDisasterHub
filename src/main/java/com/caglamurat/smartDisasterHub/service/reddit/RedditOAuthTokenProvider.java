package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.service.integration.RedditIntegrationSettingsService;
import com.caglamurat.smartDisasterHub.service.integration.RedditIntegrationSettingsService.ResolvedCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * Obtains and caches Reddit OAuth2 access tokens (script-app password grant).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RedditOAuthTokenProvider {

    private static final String TOKEN_URL = "https://www.reddit.com/api/v1/access_token";

    private final RestTemplate restTemplate;
    private final RedditIntegrationSettingsService integrationSettingsService;

    private volatile String accessToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public boolean isConfigured() {
        return integrationSettingsService.isConfigured();
    }

    public synchronized String getAccessToken() {
        ResolvedCredentials creds = integrationSettingsService.resolveCredentials();
        if (!creds.configured()) {
            throw new IllegalStateException("Reddit OAuth credentials are not configured");
        }
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return accessToken;
        }
        refreshAccessToken(creds);
        return accessToken;
    }

    public synchronized void clearTokenCache() {
        accessToken = null;
        expiresAt = Instant.EPOCH;
    }

    private void refreshAccessToken(ResolvedCredentials creds) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set(HttpHeaders.USER_AGENT, creds.userAgent());
        headers.set(HttpHeaders.AUTHORIZATION, basicAuthHeader(creds.clientId(), creds.clientSecret()));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("username", creds.username());
        body.add("password", creds.password());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    TOKEN_URL,
                    new HttpEntity<>(body, headers),
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Reddit token request failed: " + response.getStatusCode());
            }

            Map<?, ?> payload = response.getBody();
            Object token = payload.get("access_token");
            Object expiresIn = payload.get("expires_in");

            if (token == null || token.toString().isBlank()) {
                throw new IllegalStateException("Reddit token response missing access_token");
            }

            accessToken = token.toString();
            long ttlSeconds = 3600;
            if (expiresIn instanceof Number number) {
                ttlSeconds = number.longValue();
            } else if (expiresIn != null) {
                try {
                    ttlSeconds = Long.parseLong(expiresIn.toString());
                } catch (NumberFormatException ignored) {
                    // keep default
                }
            }
            expiresAt = Instant.now().plusSeconds(Math.max(60, ttlSeconds));
            log.info("Reddit OAuth access token refreshed (source={}, expires in {}s)",
                    creds.source(), ttlSeconds);
        } catch (RestClientException e) {
            accessToken = null;
            expiresAt = Instant.EPOCH;
            throw new IllegalStateException("Failed to obtain Reddit OAuth token: " + e.getMessage(), e);
        }
    }

    private static String basicAuthHeader(String clientId, String clientSecret) {
        String credentials = clientId + ":" + clientSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
