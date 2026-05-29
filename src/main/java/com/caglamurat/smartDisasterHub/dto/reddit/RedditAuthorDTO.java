package com.caglamurat.smartDisasterHub.dto.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditAuthorDTO {
    private Long id;
    private String redditUsername;
    private String redditUserId;
    private int totalPosts;
    private int analyzedPosts;
    private int disasterRelatedPosts;
    private int failedAnalysisPosts;
    private Double trustScore;
    private Instant firstSeenAt;
    private Instant lastPostAt;
    private Instant createdAt;
    private Instant updatedAt;
}
