package com.caglamurat.smartDisasterHub.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostAdjustmentRowDTO {
    private Long id;
    private String redditPostId;
    private String title;
    private String subreddit;
    private Double baseRelevanceScore;
    private Double finalRelevanceScore;
    private Double relevanceAdjustmentDelta;
    private String relevanceAdjustmentReasons;
    private Double appliedAuthorTrustScore;
    private Instant analyzedAt;
}

