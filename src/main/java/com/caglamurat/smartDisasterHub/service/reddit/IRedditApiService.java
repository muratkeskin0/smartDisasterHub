package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.dto.reddit.RedditApiPost;

import java.util.List;

/**
 * Service interface for fetching posts from Reddit API
 */
public interface IRedditApiService {

    /**
     * Fetch posts from a specific subreddit
     * 
     * @param subreddit Subreddit name (e.g., "worldnews")
     * @param limit Maximum number of posts to fetch
     * @return List of Reddit posts
     */
    List<RedditApiPost> fetchPosts(String subreddit, int limit);

    /**
     * Fetch posts from multiple subreddits
     * 
     * @param subreddits List of subreddit names
     * @param limitPerSubreddit Maximum number of posts per subreddit
     * @return List of Reddit posts
     */
    List<RedditApiPost> fetchPostsFromMultipleSubreddits(List<String> subreddits, int limitPerSubreddit);
}





