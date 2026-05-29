package com.caglamurat.smartDisasterHub.dto.reddit;

import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTO for RedditPost
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditPostDTO {
    
    private Long id;
    private String redditPostId;
    private String title;
    private String content;
    private String url;
    private String mediaUrl;
    private List<String> mediaUrls;
    private String mediaContentHash;
    private Long duplicateOfPostId;
    /** {@code reddit_post_id} of the canonical post when this row duplicates another (same image hash). */
    private String duplicateOfRedditPostId;
    /** FK to reddit_authors.id (aggregated Reddit user stats). */
    private Long redditAuthorId;
    /** Reddit API {@code author_fullname}, e.g. {@code t2_xxx}. */
    private String redditAuthorFullname;
    private String author;
    private String subreddit;
    private Integer upvotes;
    private Integer commentCount;
    private Instant redditCreatedAt;
    private Instant fetchedAt;
    private Boolean isDisasterRelated;
    private Double relevanceScore;
    private Double baseRelevanceScore;
    private Double finalRelevanceScore;
    private Double relevanceAdjustmentDelta;
    private String relevanceAdjustmentReasons;
    private Double appliedAuthorTrustScore;
    private String analysisMessage;
    private Boolean isHelpRequest;
    private Double helpRequestProbability;
    private String humanitarianCategories;
    private Boolean isImageTextMatch;
    private Double imageTextMatchScore;
    private String imageCaption;
    private Boolean hasImageDamage;
    private String imageDamageSeverity;
    private Double imageDamageScore;
    private Instant imageAnalyzedAt;
    private Instant analyzedAt;
    private RedditPostStatus status;
    private PostModerationStatus moderationStatus;
    private Instant moderationReviewedAt;
    private String moderationReviewedBy;
    private String moderationNotes;
    private Long assignedModeratorId;
    private String assignedModeratorEmail;
    private String assignedModeratorName;
    private Instant assignedAt;
    private String locationText;
    private Double latitude;
    private Double longitude;
    private String locationCountry;
    private String locationCity;
    private String locationRegionKey;
    private Instant createdAt;
    private Instant updatedAt;
}





