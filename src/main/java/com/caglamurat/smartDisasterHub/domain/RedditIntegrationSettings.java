package com.caglamurat.smartDisasterHub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "reddit_integration_settings")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditIntegrationSettings extends BaseEntity {

    @Column(name = "client_id", length = 128)
    private String clientId;

    @Column(name = "client_secret_encrypted", columnDefinition = "TEXT")
    private String clientSecretEncrypted;

    @Column(name = "username", length = 128)
    private String username;

    @Column(name = "password_encrypted", columnDefinition = "TEXT")
    private String passwordEncrypted;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "subreddits", length = 500)
    private String subreddits;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "last_test_at")
    private Instant lastTestAt;

    @Column(name = "last_test_success")
    private Boolean lastTestSuccess;

    @Column(name = "last_test_message", length = 500)
    private String lastTestMessage;

    @Column(name = "last_fetch_at")
    private Instant lastFetchAt;

    @Column(name = "last_fetch_count")
    private Integer lastFetchCount;
}
