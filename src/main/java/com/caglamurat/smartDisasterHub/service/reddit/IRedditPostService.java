package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;

import java.time.Instant;
import java.util.List;

/**
 * Service interface for managing Reddit posts
 */
public interface IRedditPostService {

    /**
     * Save or update a Reddit post
     * If post exists (by redditPostId), it will be updated
     * 
     * @param redditApiPost Post data from Reddit API
     * @return Saved RedditPostDTO
     */
    RedditPostDTO saveOrUpdatePost(com.caglamurat.smartDisasterHub.dto.reddit.RedditApiPost redditApiPost);

    /**
     * Save multiple posts
     * 
     * @param redditApiPosts List of posts from Reddit API
     * @return List of saved RedditPostDTOs
     */
    List<RedditPostDTO> saveOrUpdatePosts(List<com.caglamurat.smartDisasterHub.dto.reddit.RedditApiPost> redditApiPosts);

    /**
     * Find post by Reddit post ID
     */
    RedditPostDTO findByRedditPostId(String redditPostId);

    /**
     * Find posts that need analysis (status = PENDING)
     */
    List<RedditPostDTO> findPendingPosts(int limit);

    /**
     * Analyze a post using ML service
     * 
     * @param postId Database ID of the post
     * @return Updated RedditPostDTO with analysis results
     */
    RedditPostDTO analyzePost(Long postId);

    /**
     * Analyze all pending posts
     * 
     * @param limit Maximum number of posts to analyze
     * @return Number of posts analyzed
     */
    int analyzePendingPosts(int limit);

    /**
     * Find analyzed posts (both disaster-related and non-disaster-related)
     */
    List<RedditPostDTO> findAnalyzedPosts(int limit);

    /**
     * Find analyzed posts with pagination and sorting
     */
    com.caglamurat.smartDisasterHub.dto.reddit.PageResponse<RedditPostDTO> findAnalyzedPosts(
            com.caglamurat.smartDisasterHub.dto.reddit.PageRequest pageRequest,
            Instant reportedFrom,
            Instant reportedTo,
            PostModerationStatus moderationStatus);

    /**
     * Find disaster-related posts
     */
    List<RedditPostDTO> findDisasterRelatedPosts(int limit);

    /**
     * Find disaster-related posts with pagination and sorting
     */
    com.caglamurat.smartDisasterHub.dto.reddit.PageResponse<RedditPostDTO> findDisasterRelatedPosts(
            com.caglamurat.smartDisasterHub.dto.reddit.PageRequest pageRequest,
            Instant reportedFrom,
            Instant reportedTo);

    /**
     * Get statistics about posts
     */
    PostStatistics getStatistics(Instant reportedFrom, Instant reportedTo);

    /**
     * Get map markers - disaster-related posts grouped by location
     * 
     * @return List of map markers with location and post information
     */
    List<com.caglamurat.smartDisasterHub.dto.reddit.MapMarkerDTO> getMapMarkers(
            Instant reportedFrom,
            Instant reportedTo);
}





