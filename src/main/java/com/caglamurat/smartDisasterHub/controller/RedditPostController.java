package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.MapMarkerDTO;
import com.caglamurat.smartDisasterHub.dto.reddit.PageRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.dto.report.HistoricalReportSummaryDTO;
import com.caglamurat.smartDisasterHub.dto.report.HistoricalTrendPointDTO;
import com.caglamurat.smartDisasterHub.dto.report.ReportBreakdownDto;
import com.caglamurat.smartDisasterHub.job.RedditAnalysisJob;
import com.caglamurat.smartDisasterHub.job.RedditFetchJob;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.service.reddit.HistoricalReportsService;
import com.caglamurat.smartDisasterHub.service.reddit.IRedditPostService;
import com.caglamurat.smartDisasterHub.service.reddit.PostStatistics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reddit Post Controller
 * Handles requests for Reddit post data and analysis results
 */
@RestController
@RequestMapping("/api/reddit-posts")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class RedditPostController {

    private final IRedditPostService redditPostService;
    private final HistoricalReportsService historicalReportsService;
    private final RedditFetchJob redditFetchJob;
    private final RedditAnalysisJob redditAnalysisJob;

    /**
     * Get all analyzed posts (disaster-related and non-disaster-related)
     * Supports pagination and sorting via query parameters
     * 
     * Query parameters:
     * - page: Page number (default: 0)
     * - size: Page size (default: 50)
     * - sortBy: Field to sort by (default: analyzedAt) - options: analyzedAt, fetchedAt, relevanceScore, upvotes, title, subreddit
     * - sortDirection: Sort direction (default: DESC) - options: ASC, DESC
     * - from, to: optional instant range; when either is set, results are limited by Reddit post time ({@code redditCreatedAt})
     * 
     * @return Paginated list of analyzed Reddit posts
     */
    @GetMapping("/analyzed")
    public ResponseEntity<ApiResponse<PageResponse<RedditPostDTO>>> getAnalyzedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "analyzedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) PostModerationStatus moderationStatus) {
        log.debug("REST request to get analyzed posts - page: {}, size: {}, sortBy: {}, sortDirection: {}, from: {}, to: {}, moderationStatus: {}",
                page, size, sortBy, sortDirection, from, to, moderationStatus);
        
        PageRequest.SortDirection direction;
        try {
            direction = PageRequest.SortDirection.valueOf(sortDirection.toUpperCase());
        } catch (IllegalArgumentException e) {
            direction = PageRequest.SortDirection.DESC;
        }
        
        PageRequest pageRequest = PageRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
        
        PageResponse<RedditPostDTO> response = redditPostService.findAnalyzedPosts(pageRequest, from, to, moderationStatus);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get disaster-related posts only
     * Supports pagination and sorting via query parameters
     * 
     * Query parameters:
     * - page: Page number (default: 0)
     * - size: Page size (default: 50)
     * - sortBy: Field to sort by (default: analyzedAt) - options: analyzedAt, fetchedAt, relevanceScore, upvotes, title, subreddit
     * - sortDirection: Sort direction (default: DESC) - options: ASC, DESC
     * - from, to: optional range on Reddit post time ({@code redditCreatedAt})
     * 
     * @return Paginated list of disaster-related Reddit posts
     */
    @GetMapping("/disaster-related")
    public ResponseEntity<ApiResponse<PageResponse<RedditPostDTO>>> getDisasterRelatedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "analyzedAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("REST request to get disaster-related posts - page: {}, size: {}, sortBy: {}, sortDirection: {}, from: {}, to: {}",
                page, size, sortBy, sortDirection, from, to);
        
        PageRequest.SortDirection direction;
        try {
            direction = PageRequest.SortDirection.valueOf(sortDirection.toUpperCase());
        } catch (IllegalArgumentException e) {
            direction = PageRequest.SortDirection.DESC;
        }
        
        PageRequest pageRequest = PageRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
        
        PageResponse<RedditPostDTO> response = redditPostService.findDisasterRelatedPosts(pageRequest, from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get post statistics.
     * Optional {@code from}/{@code to} limit analyzed/disaster counts by Reddit post time ({@code redditCreatedAt}).
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<PostStatistics>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("REST request to get Reddit post statistics from: {} to: {}", from, to);
        PostStatistics statistics = redditPostService.getStatistics(from, to);
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    /** Report KPIs; optional {@code from}/{@code to} filter by Reddit post time ({@code redditCreatedAt}). */
    @GetMapping("/reports/summary")
    public ResponseEntity<ApiResponse<HistoricalReportSummaryDTO>> getHistoricalSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        HistoricalReportSummaryDTO summary = historicalReportsService.getSummary(from, to);
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    /** Distributions for pie charts: humanitarian tags, Reddit post dates, coarse map regions (optional from/to). */
    @GetMapping("/reports/breakdown")
    public ResponseEntity<ApiResponse<ReportBreakdownDto>> getReportBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        ReportBreakdownDto breakdown = historicalReportsService.getBreakdown(from, to);
        return ResponseEntity.ok(ApiResponse.success(breakdown));
    }

    /** Daily buckets by Reddit post date; optional {@code from}/{@code to} or {@code days} when unbounded. */
    @GetMapping("/reports/trend")
    public ResponseEntity<ApiResponse<List<HistoricalTrendPointDTO>>> getHistoricalTrend(
            @RequestParam(defaultValue = "14") int days,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        List<HistoricalTrendPointDTO> trend =
                (from != null || to != null)
                        ? historicalReportsService.getDailyTrend(from, to)
                        : historicalReportsService.getDailyTrend(days);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    /** Top adjusted posts; optional {@code from}/{@code to} on Reddit post time. */
    @GetMapping("/reports/top-adjusted")
    public ResponseEntity<ApiResponse<PageResponse<RedditPostDTO>>> getTopAdjustedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        PageResponse<RedditPostDTO> response = historicalReportsService.getTopAdjustedPosts(page, size, from, to);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Get a specific post by Reddit post ID
     * 
     * @param redditPostId Reddit post ID
     * @return Reddit post details
     */
    @GetMapping("/{redditPostId}")
    public ResponseEntity<ApiResponse<RedditPostDTO>> getPostByRedditId(
            @PathVariable String redditPostId) {
        log.debug("REST request to get post by Reddit ID: {}", redditPostId);
        RedditPostDTO post = redditPostService.findByRedditPostId(redditPostId);
        if (post == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiResponse.success(post));
    }

    /**
     * Map markers; optional {@code from}/{@code to} filter markers by Reddit post time ({@code redditCreatedAt}).
     * Managers may view approved posts on the map (read-only).
     */
    @GetMapping("/map")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<List<MapMarkerDTO>>> getMapMarkers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        log.debug("REST request to get map markers from: {} to: {}", from, to);
        List<MapMarkerDTO> markers = redditPostService.getMapMarkers(from, to);
        return ResponseEntity.ok(ApiResponse.success(markers));
    }

    /**
     * Manually trigger Reddit fetch job
     * Fetches new posts from Reddit and saves them to database
     * 
     * @return Response with job execution result
     */
    @PostMapping("/jobs/fetch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerFetchJob() {
        log.info("REST request to manually trigger Reddit fetch job");
        
        try {
            redditFetchJob.executeFetchJob();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Reddit fetch job executed successfully");
            
            return ResponseEntity.ok(ApiResponse.success("Fetch job triggered successfully", result));
        } catch (Exception e) {
            log.error("Error triggering fetch job: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", "Error executing fetch job: " + e.getMessage());
            
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to trigger fetch job", null));
        }
    }

    /**
     * Manually trigger Reddit analysis job
     * Analyzes pending posts using ML service
     * 
     * @return Response with job execution result
     */
    @PostMapping("/jobs/analyze")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerAnalysisJob() {
        log.info("REST request to manually trigger Reddit analysis job");
        
        try {
            redditAnalysisJob.executeAnalysisJob();
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Reddit analysis job executed successfully");
            
            return ResponseEntity.ok(ApiResponse.success("Analysis job triggered successfully", result));
        } catch (Exception e) {
            log.error("Error triggering analysis job: {}", e.getMessage(), e);
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "error");
            result.put("message", "Error executing analysis job: " + e.getMessage());
            
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to trigger analysis job", null));
        }
    }

    /**
     * Manually trigger both jobs (fetch and analyze)
     * First fetches new posts, then analyzes pending posts
     * 
     * @return Response with job execution results
     */
    @PostMapping("/jobs/refresh")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerRefresh() {
        log.info("REST request to manually trigger refresh (fetch + analyze)");
        
        Map<String, Object> results = new HashMap<>();
        
        try {
            // First, fetch new posts
            log.info("Executing fetch job...");
            redditFetchJob.executeFetchJob();
            results.put("fetch", Map.of("status", "success", "message", "Fetch job completed"));
            
            // Wait a bit for posts to be saved
            Thread.sleep(1000);
            
            // Then, analyze pending posts
            log.info("Executing analysis job...");
            redditAnalysisJob.executeAnalysisJob();
            results.put("analyze", Map.of("status", "success", "message", "Analysis job completed"));
            
            results.put("status", "success");
            results.put("message", "Refresh completed successfully");
            
            return ResponseEntity.ok(ApiResponse.success("Refresh completed successfully", results));
        } catch (Exception e) {
            log.error("Error triggering refresh: {}", e.getMessage(), e);
            
            results.put("status", "error");
            results.put("message", "Error executing refresh: " + e.getMessage());
            
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("Failed to trigger refresh", null));
        }
    }
}





