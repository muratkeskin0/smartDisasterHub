package com.caglamurat.smartDisasterHub.dto.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO representing a Reddit post from Reddit API response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditApiPost {
    
    private String id;
    private String title;
    private String selftext; // Post content/body
    private String url;
    /**
     * Original media/link URL from Reddit listing JSON (e.g. i.redd.it image).
     * This is NOT the Reddit permalink; it may be an external link or a direct media URL.
     */
    private String mediaUrl;
    /**
     * All image URLs we can extract for this post (gallery + preview).
     * If present, the first item is preferred for display and vision analysis.
     */
    private List<String> mediaUrls;
    private String author;
    /**
     * Reddit account fullname from JSON {@code author_fullname}, e.g. {@code t2_abc123} (stable id).
     */
    private String authorFullname;
    private String subreddit;
    private Integer ups; // Upvotes
    private Integer numComments;
    private Long createdUtc; // Unix timestamp
    private String permalink; // Reddit permalink
}





