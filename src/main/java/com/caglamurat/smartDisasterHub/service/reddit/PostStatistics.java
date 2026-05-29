package com.caglamurat.smartDisasterHub.service.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics about Reddit posts
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostStatistics {
    private long totalPosts;
    private long pendingPosts;
    private long analyzedPosts;
    private long failedPosts;
    private long disasterRelatedPosts;
    private double disasterPercentage; // Percentage of analyzed posts that are disaster-related
    /** Kamuya açık: onaylı afet postları. */
    private long approvedDisasterPosts;
    private long pendingModerationPosts;
    private long rejectedModerationPosts;
}





