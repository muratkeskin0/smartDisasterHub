package com.caglamurat.smartDisasterHub.domain;

import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Reddit Post Entity
 * Stores Reddit posts fetched and analyzed for disaster relevance
 */
@Entity
@Table(name = "reddit_posts", indexes = {
    @Index(name = "idx_reddit_post_id", columnList = "reddit_post_id", unique = true),
    @Index(name = "idx_url", columnList = "url", unique = true),
    @Index(name = "idx_media_content_hash", columnList = "media_content_hash"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_moderation_status", columnList = "moderation_status"),
    @Index(name = "idx_analyzed_at", columnList = "analyzed_at"),
    @Index(name = "idx_fetched_at", columnList = "fetched_at")
})
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditPost extends BaseEntity {

    /**
     * Reddit post ID (unique identifier from Reddit)
     */
    @Column(name = "reddit_post_id", nullable = false, unique = true, length = 50)
    private String redditPostId;

    /**
     * Post title
     */
    @Column(nullable = false, length = 500)
    private String title;

    /**
     * Post content/body (can be null for link posts)
     */
    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Full URL to the Reddit post
     */
    @Column(nullable = false, unique = true, length = 500)
    private String url;

    /**
     * Media URL (direct image/link URL from Reddit listing JSON).
     * This may point to i.redd.it / v.redd.it / external domains.
     */
    @Column(name = "media_url", length = 1000)
    private String mediaUrl;

    /**
     * Comma-separated list of extracted image URLs (gallery/preview).
     * Kept denormalized for simplicity; frontend can split on comma.
     */
    @Column(name = "media_urls", columnDefinition = "TEXT")
    private String mediaUrls;

    /**
     * SHA-256 hash (hex) of downloaded media bytes for deduplication.
     * Only set when mediaUrl is an image and we successfully downloaded it.
     */
    @Column(name = "media_content_hash", length = 64)
    private String mediaContentHash;

    /**
     * If this media hash was seen before, points to the first post ID that had it.
     * (Simple dedup reference; no FK constraint for flexibility.)
     */
    @Column(name = "duplicate_of_post_id")
    private Long duplicateOfPostId;

    /**
     * FK to {@link RedditAuthor} (aggregated stats / trust for this Reddit username).
     */
    @Column(name = "reddit_author_id")
    private Long redditAuthorId;

    /**
     * Reddit {@code author_fullname}: one stable id per Reddit account ({@code t2_...}), unique across Reddit — not a display name.
     */
    @Column(name = "reddit_author_fullname", length = 64)
    private String redditAuthorFullname;

    /**
     * Reddit author username
     */
    @Column(length = 100)
    private String author;

    /**
     * Subreddit name
     */
    @Column(nullable = false, length = 100)
    private String subreddit;

    /**
     * Number of upvotes
     */
    @Column(name = "upvotes")
    private Integer upvotes;

    /**
     * Number of comments
     */
    @Column(name = "comment_count")
    private Integer commentCount;

    /**
     * Original creation time on Reddit
     */
    @Column(name = "reddit_created_at", nullable = false)
    private Instant redditCreatedAt;

    /**
     * When this post was fetched from Reddit
     */
    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    /**
     * ML Analysis result: Is this post disaster-related?
     */
    @Column(name = "is_disaster_related")
    private Boolean isDisasterRelated;

    /**
     * ML Analysis result: Relevance score (0.0 to 1.0)
     */
    @Column(name = "relevance_score")
    private Double relevanceScore;

    /**
     * Raw base relevance score returned by ML service before heuristic adjustments.
     */
    @Column(name = "base_relevance_score")
    private Double baseRelevanceScore;

    /**
     * Final relevance score after heuristic adjustments.
     * Kept separate for historical analytics; {@code relevanceScore} remains backward compatible.
     */
    @Column(name = "final_relevance_score")
    private Double finalRelevanceScore;

    /**
     * Difference between final and base relevance scores.
     */
    @Column(name = "relevance_adjustment_delta")
    private Double relevanceAdjustmentDelta;

    /**
     * Applied adjustment reasons encoded as compact text.
     * Example: confidence_adjustments=[image_mismatch:-0.50,duplicate_in_system:-0.50,low_trust:-0.06]
     */
    @Column(name = "relevance_adjustment_reasons", columnDefinition = "TEXT")
    private String relevanceAdjustmentReasons;

    /**
     * Author trust score snapshot used during relevance adjustment.
     */
    @Column(name = "applied_author_trust_score")
    private Double appliedAuthorTrustScore;

    /**
     * ML Analysis message
     */
    @Column(name = "analysis_message", columnDefinition = "TEXT")
    private String analysisMessage;

    /**
     * T2: Is this post a help request?
     */
    @Column(name = "is_help_request")
    private Boolean isHelpRequest;

    /**
     * T2: Help request probability (0.0 to 1.0).
     */
    @Column(name = "help_request_probability")
    private Double helpRequestProbability;

    /**
     * T2: Humanitarian category labels (comma-separated).
     * Example: "urgent_needs,infrastructure_damage"
     */
    @Column(name = "humanitarian_categories", length = 255)
    private String humanitarianCategories;

    /**
     * Vision: Is the image consistent with the post text?
     */
    @Column(name = "is_image_text_match")
    private Boolean isImageTextMatch;

    /**
     * Vision: match score (0.0 to 1.0)
     */
    @Column(name = "image_text_match_score")
    private Double imageTextMatchScore;

    /**
     * Vision: short caption generated from image
     */
    @Column(name = "image_caption", columnDefinition = "TEXT")
    private String imageCaption;

    /**
     * T3: whether image appears to contain physical damage.
     */
    @Column(name = "has_image_damage")
    private Boolean hasImageDamage;

    /**
     * T3: damage severity label (none/minor/moderate/severe/unknown).
     */
    @Column(name = "image_damage_severity", length = 20)
    private String imageDamageSeverity;

    /**
     * T3: damage score (0.0 to 1.0).
     */
    @Column(name = "image_damage_score")
    private Double imageDamageScore;

    /**
     * T3: raw JSON response for image damage analysis.
     */
    @Column(name = "image_damage_analysis_json", columnDefinition = "TEXT")
    private String imageDamageAnalysisJson;

    /**
     * Vision: analysis JSON (raw) for audit/debug
     */
    @Column(name = "image_analysis_json", columnDefinition = "TEXT")
    private String imageAnalysisJson;

    /**
     * When the image was analyzed
     */
    @Column(name = "image_analyzed_at")
    private Instant imageAnalyzedAt;

    /**
     * When the post was analyzed
     */
    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    /**
     * Current processing status
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RedditPostStatus status = RedditPostStatus.PENDING;

    /**
     * Human moderation (set when {@link #status} becomes {@link RedditPostStatus#ANALYZED}).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_status", length = 20)
    private PostModerationStatus moderationStatus;

    @Column(name = "moderation_reviewed_at")
    private Instant moderationReviewedAt;

    /** App user email who approved/rejected (from JWT principal). */
    @Column(name = "moderation_reviewed_by", length = 150)
    private String moderationReviewedBy;

    @Column(name = "moderation_notes", columnDefinition = "TEXT")
    private String moderationNotes;

    /** Manager who claimed this post for review (nullable until claimed). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_moderator_id")
    private User assignedModerator;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    /**
     * Raw location text extracted after labels such as Location: / Konum: (before geocoding).
     */
    @Column(name = "location_text", columnDefinition = "TEXT")
    private String locationText;

    /**
     * Latitude coordinate (optional, for location-based posts)
     */
    @Column(name = "latitude")
    private Double latitude;

    /**
     * Longitude coordinate (optional, for location-based posts)
     */
    @Column(name = "longitude")
    private Double longitude;

    /** Country display name from geocoder (e.g. Türkiye). */
    @Column(name = "location_country", length = 120)
    private String locationCountry;

    /** City / town / district label from geocoder (or il adı fallback). */
    @Column(name = "location_city", length = 200)
    private String locationCity;

    /**
     * Coarse region key for charts: {@code marmara}, {@code aegean}, {@code outside_tr}, …
     * Set at location enrichment from Nominatim + Turkish il→bölge map.
     */
    @Column(name = "location_region_key", length = 40)
    private String locationRegionKey;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = RedditPostStatus.PENDING;
        }
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }
}





