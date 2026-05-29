package com.caglamurat.smartDisasterHub.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Aggregated stats per Reddit username (from fetched posts): post counts and trust score (T6-style signal).
 */
@Entity
@Table(name = "reddit_authors", indexes = {
        @Index(name = "idx_reddit_authors_username", columnList = "reddit_username", unique = true)
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditAuthor extends BaseEntity {

    /**
     * Normalized Reddit login (lowercase). Not the app User table.
     */
    @Column(name = "reddit_username", nullable = false, unique = true, length = 100)
    private String redditUsername;

    /**
     * Reddit API {@code author_fullname}: unique per account (e.g. {@code t2_wnf9qoikfg}), not a person's legal name.
     */
    @Column(name = "reddit_user_id", length = 64)
    private String redditUserId;

    @Column(name = "total_posts", nullable = false)
    @Builder.Default
    private int totalPosts = 0;

    @Column(name = "analyzed_posts", nullable = false)
    @Builder.Default
    private int analyzedPosts = 0;

    @Column(name = "disaster_related_posts", nullable = false)
    @Builder.Default
    private int disasterRelatedPosts = 0;

    @Column(name = "failed_analysis_posts", nullable = false)
    @Builder.Default
    private int failedAnalysisPosts = 0;

    @Column(name = "moderation_approved_posts", nullable = false)
    @Builder.Default
    private int moderationApprovedPosts = 0;

    @Column(name = "moderation_rejected_posts", nullable = false)
    @Builder.Default
    private int moderationRejectedPosts = 0;

    /**
     * 0.0–1.0 heuristic from historical posts (disaster ratio, volume, failures).
     */
    @Column(name = "trust_score")
    private Double trustScore;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_post_at")
    private Instant lastPostAt;
}
