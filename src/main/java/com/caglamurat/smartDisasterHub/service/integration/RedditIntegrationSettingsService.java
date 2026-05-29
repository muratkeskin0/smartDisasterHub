package com.caglamurat.smartDisasterHub.service.integration;

import com.caglamurat.smartDisasterHub.domain.RedditIntegrationSettings;
import com.caglamurat.smartDisasterHub.dto.integration.RedditIntegrationSettingsDTO;
import com.caglamurat.smartDisasterHub.dto.integration.RedditIntegrationSettingsUpdateDTO;
import com.caglamurat.smartDisasterHub.dto.integration.RedditIntegrationStatusDTO;
import com.caglamurat.smartDisasterHub.repository.IRedditIntegrationSettingsRepository;
import com.caglamurat.smartDisasterHub.service.security.SecretEncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedditIntegrationSettingsService {

    public static final String MASK_PLACEHOLDER = "********";

    private final IRedditIntegrationSettingsRepository repository;
    private final SecretEncryptionService encryptionService;

    @Value("${app.reddit.client-id:}")
    private String propertyClientId;

    @Value("${app.reddit.client-secret:}")
    private String propertyClientSecret;

    @Value("${app.reddit.username:}")
    private String propertyUsername;

    @Value("${app.reddit.password:}")
    private String propertyPassword;

    @Value("${app.reddit.user-agent:SmartDisasterHub/1.0}")
    private String propertyUserAgent;

    @Value("${app.reddit.subreddits:smartDisasterHub}")
    private String propertySubreddits;

    @Transactional(readOnly = true)
    public RedditIntegrationSettingsDTO getSettings() {
        Optional<RedditIntegrationSettings> db = repository.findTopByOrderByIdAsc();
        ResolvedCredentials resolved = resolveCredentials(db.orElse(null));
        RedditIntegrationSettings entity = db.orElse(null);

        return RedditIntegrationSettingsDTO.builder()
                .clientId(firstNonBlank(entity != null ? entity.getClientId() : null, propertyClientId))
                .clientSecretConfigured(hasSecret(entity != null ? entity.getClientSecretEncrypted() : null, propertyClientSecret))
                .username(firstNonBlank(entity != null ? entity.getUsername() : null, propertyUsername))
                .passwordConfigured(hasSecret(entity != null ? entity.getPasswordEncrypted() : null, propertyPassword))
                .userAgent(firstNonBlank(
                        entity != null ? entity.getUserAgent() : null,
                        propertyUserAgent,
                        "SmartDisasterHub/1.0"))
                .subreddits(firstNonBlank(
                        entity != null ? entity.getSubreddits() : null,
                        propertySubreddits,
                        "smartDisasterHub"))
                .enabled(entity != null ? entity.isEnabled() : true)
                .configured(resolved.configured())
                .configSource(resolved.source().name())
                .lastTestAt(entity != null ? entity.getLastTestAt() : null)
                .lastTestSuccess(entity != null ? entity.getLastTestSuccess() : null)
                .lastTestMessage(entity != null ? entity.getLastTestMessage() : null)
                .lastFetchAt(entity != null ? entity.getLastFetchAt() : null)
                .lastFetchCount(entity != null ? entity.getLastFetchCount() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public RedditIntegrationStatusDTO getStatus() {
        RedditIntegrationSettingsDTO settings = getSettings();
        return RedditIntegrationStatusDTO.builder()
                .configured(settings.isConfigured())
                .enabled(settings.isEnabled())
                .configSource(settings.getConfigSource())
                .lastTestAt(settings.getLastTestAt())
                .lastTestSuccess(settings.getLastTestSuccess())
                .lastTestMessage(settings.getLastTestMessage())
                .build();
    }

    @Transactional
    public RedditIntegrationSettingsDTO updateSettings(RedditIntegrationSettingsUpdateDTO update) {
        RedditIntegrationSettings entity = repository.findTopByOrderByIdAsc()
                .orElseGet(() -> RedditIntegrationSettings.builder().build());

        if (update.getClientId() != null) {
            entity.setClientId(trimToNull(update.getClientId()));
        }
        if (shouldReplaceSecret(update.getClientSecret())) {
            entity.setClientSecretEncrypted(encryptionService.encrypt(update.getClientSecret().trim()));
        }
        if (update.getUsername() != null) {
            entity.setUsername(trimToNull(update.getUsername()));
        }
        if (shouldReplaceSecret(update.getPassword())) {
            entity.setPasswordEncrypted(encryptionService.encrypt(update.getPassword().trim()));
        }
        if (update.getUserAgent() != null) {
            entity.setUserAgent(trimToNull(update.getUserAgent()));
        }
        if (update.getSubreddits() != null) {
            entity.setSubreddits(trimToNull(update.getSubreddits()));
        }
        if (update.getEnabled() != null) {
            entity.setEnabled(update.getEnabled());
        }

        repository.save(entity);
        log.info("Reddit integration settings updated (DB row id={})", entity.getId());
        return getSettings();
    }

    @Transactional
    public RedditIntegrationSettingsDTO recordTestResult(boolean success, String message) {
        RedditIntegrationSettings entity = repository.findTopByOrderByIdAsc().orElse(null);
        updateTestResult(entity, success, truncate(message, 480));
        return getSettings();
    }

    @Transactional
    public void recordFetchResult(int fetchedCount) {
        repository.findTopByOrderByIdAsc().ifPresent(entity -> {
            entity.setLastFetchAt(Instant.now());
            entity.setLastFetchCount(fetchedCount);
            repository.save(entity);
        });
    }

    public boolean isConfigured() {
        return resolveCredentials(repository.findTopByOrderByIdAsc().orElse(null)).configured();
    }

    public boolean isEnabled() {
        return repository.findTopByOrderByIdAsc()
                .map(RedditIntegrationSettings::isEnabled)
                .orElse(true);
    }

    public String getUserAgent() {
        return resolveCredentials(repository.findTopByOrderByIdAsc().orElse(null)).userAgent();
    }

    public List<String> getSubreddits() {
        String raw = resolveCredentials(repository.findTopByOrderByIdAsc().orElse(null)).subreddits();
        if (raw == null || raw.isBlank()) {
            return List.of("smartDisasterHub");
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("r/") ? s.substring(2) : s)
                .toList();
    }

    public ResolvedCredentials resolveCredentials() {
        return resolveCredentials(repository.findTopByOrderByIdAsc().orElse(null));
    }

    private ResolvedCredentials resolveCredentials(RedditIntegrationSettings entity) {
        String dbClientId = entity != null ? entity.getClientId() : null;
        String dbSecret = entity != null && entity.getClientSecretEncrypted() != null
                ? safeDecrypt(entity.getClientSecretEncrypted()) : null;
        String dbUser = entity != null ? entity.getUsername() : null;
        String dbPass = entity != null && entity.getPasswordEncrypted() != null
                ? safeDecrypt(entity.getPasswordEncrypted()) : null;
        String dbAgent = entity != null ? entity.getUserAgent() : null;
        String dbSubs = entity != null ? entity.getSubreddits() : null;

        boolean dbComplete = !isBlank(dbClientId) && !isBlank(dbSecret) && !isBlank(dbUser) && !isBlank(dbPass);
        if (dbComplete) {
            return new ResolvedCredentials(
                    dbClientId.trim(),
                    dbSecret,
                    dbUser.trim(),
                    dbPass,
                    firstNonBlank(dbAgent, propertyUserAgent, "SmartDisasterHub/1.0"),
                    firstNonBlank(dbSubs, propertySubreddits, "smartDisasterHub"),
                    ConfigSource.DATABASE
            );
        }

        boolean propsComplete = !isBlank(propertyClientId) && !isBlank(propertyClientSecret)
                && !isBlank(propertyUsername) && !isBlank(propertyPassword);
        if (propsComplete) {
            return new ResolvedCredentials(
                    propertyClientId.trim(),
                    propertyClientSecret,
                    propertyUsername.trim(),
                    propertyPassword,
                    firstNonBlank(propertyUserAgent, "SmartDisasterHub/1.0"),
                    firstNonBlank(propertySubreddits, "smartDisasterHub"),
                    ConfigSource.PROPERTIES
            );
        }

        return new ResolvedCredentials(
                null, null, null, null,
                firstNonBlank(dbAgent, propertyUserAgent, "SmartDisasterHub/1.0"),
                firstNonBlank(dbSubs, propertySubreddits, "smartDisasterHub"),
                ConfigSource.NONE
        );
    }

    private void updateTestResult(RedditIntegrationSettings entity, boolean success, String message) {
        RedditIntegrationSettings target = entity != null
                ? entity
                : repository.findTopByOrderByIdAsc().orElseGet(() -> repository.save(RedditIntegrationSettings.builder().build()));
        target.setLastTestAt(Instant.now());
        target.setLastTestSuccess(success);
        target.setLastTestMessage(message);
        repository.save(target);
    }

    private String safeDecrypt(String encrypted) {
        try {
            return encryptionService.decrypt(encrypted);
        } catch (Exception e) {
            log.error("Failed to decrypt Reddit integration secret: {}", e.getMessage());
            return null;
        }
    }

    private static boolean shouldReplaceSecret(String value) {
        return value != null && !value.isBlank() && !MASK_PLACEHOLDER.equals(value.trim());
    }

    private static boolean hasSecret(String encrypted, String propertyPlain) {
        return (encrypted != null && !encrypted.isBlank()) || !isBlank(propertyPlain);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (!isBlank(v)) return v.trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    public enum ConfigSource {
        DATABASE, PROPERTIES, NONE
    }

    public record ResolvedCredentials(
            String clientId,
            String clientSecret,
            String username,
            String password,
            String userAgent,
            String subreddits,
            ConfigSource source
    ) {
        public boolean configured() {
            return !isBlank(clientId) && !isBlank(clientSecret) && !isBlank(username) && !isBlank(password);
        }
    }
}
