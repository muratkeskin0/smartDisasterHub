package com.caglamurat.smartDisasterHub.service.reddit;

import org.springframework.data.domain.Sort;

/**
 * Shared sort field mapping for Reddit post list endpoints.
 */
public final class RedditPostSortHelper {

    private RedditPostSortHelper() {
    }

    public static String validateSortField(String sortBy) {
        if (sortBy == null) {
            return "analyzedAt";
        }
        return switch (sortBy.toLowerCase()) {
            case "analyzedat", "analyzed_at" -> "analyzedAt";
            case "fetchedat", "fetched_at" -> "fetchedAt";
            case "redditcreatedat", "reddit_created_at" -> "redditCreatedAt";
            case "relevancescore", "relevance_score" -> "relevanceScore";
            case "finalrelevancescore", "final_relevance_score" -> "finalRelevanceScore";
            case "upvotes" -> "upvotes";
            case "title" -> "title";
            case "subreddit" -> "subreddit";
            default -> "analyzedAt";
        };
    }

    public static Sort buildSort(String sortBy, Sort.Direction direction) {
        return Sort.by(direction != null ? direction : Sort.Direction.DESC, validateSortField(sortBy));
    }
}
