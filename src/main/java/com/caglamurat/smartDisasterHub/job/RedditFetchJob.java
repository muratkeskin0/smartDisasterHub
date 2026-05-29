package com.caglamurat.smartDisasterHub.job;

import com.caglamurat.smartDisasterHub.service.integration.RedditIntegrationSettingsService;
import com.caglamurat.smartDisasterHub.service.reddit.IRedditApiService;
import com.caglamurat.smartDisasterHub.service.reddit.IRedditPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Background job for fetching Reddit posts
 * Sadece Reddit'ten veri çeker ve veritabanına kaydeder
 * Analiz işlemi ayrı bir job tarafından yapılır (RedditAnalysisJob)
 * Runs every 15 minutes (900,000 milliseconds)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedditFetchJob {

    private final IRedditApiService redditApiService;
    private final IRedditPostService redditPostService;
    private final RedditIntegrationSettingsService integrationSettingsService;

    @Value("${app.reddit.subreddits:smartDisasterHub}")
    private String subredditsConfig;

    @Value("${app.reddit.posts-per-subreddit:100}")
    private int postsPerSubreddit;

    /**
     * Fetch Reddit posts from configured subreddits
     * Sadece veri çeker ve kaydeder, analiz yapmaz
     * Runs every 15 minutes (900,000 milliseconds)
     * Can also be called manually via controller
     */
    @Scheduled(fixedDelayString = "${app.reddit.fetch-interval:900000}", initialDelay = 30000)
    public void fetchAndSaveRedditPosts() {
        executeFetchJob();
    }
    
    /**
     * Manual trigger for Reddit fetch job
     * Can be called from controller to manually fetch posts
     */
    public void executeFetchJob() {
        log.info("Starting Reddit fetch job...");

        try {
            if (!integrationSettingsService.isConfigured()) {
                log.warn(
                        "Reddit OAuth is not configured — fetch will try public JSON, then RSS fallback."
                );
            }
            if (!integrationSettingsService.isEnabled()) {
                log.info("Reddit integration is disabled — skipping fetch job");
                return;
            }

            List<String> subreddits = integrationSettingsService.getSubreddits();
            if (subreddits.isEmpty()) {
                subreddits = parseSubreddits(subredditsConfig);
            }
            log.info("Fetching posts from subreddits: {}", subreddits);

            // Fetch posts from Reddit
            var redditPosts = redditApiService.fetchPostsFromMultipleSubreddits(
                    subreddits, 
                    postsPerSubreddit
            );

            log.info("Fetched {} posts from Reddit", redditPosts.size());
            integrationSettingsService.recordFetchResult(redditPosts.size());

            if (redditPosts.isEmpty()) {
                log.warn("No posts fetched from Reddit");
                return;
            }

            var savedPosts = redditPostService.saveOrUpdatePosts(redditPosts);
            log.info("Saved {} posts to database (status: PENDING)", savedPosts.size());

            log.info("Reddit fetch job completed successfully. Posts will be analyzed by RedditAnalysisJob.");

        } catch (Exception e) {
            log.error("Error in Reddit fetch job: {}", e.getMessage(), e);
        }
    }

    /**
     * Parse comma-separated subreddits string to list
     */
    private List<String> parseSubreddits(String subredditsString) {
        if (subredditsString == null || subredditsString.trim().isEmpty()) {
            return Arrays.asList("worldnews", "news"); // Default subreddits
        }

        return Arrays.stream(subredditsString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith("r/") ? s.substring(2) : s) // Remove "r/" prefix if present
                .toList();
    }
}





