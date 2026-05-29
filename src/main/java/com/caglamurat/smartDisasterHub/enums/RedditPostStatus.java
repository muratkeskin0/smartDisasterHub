package com.caglamurat.smartDisasterHub.enums;

/**
 * Status of Reddit post processing
 */
public enum RedditPostStatus {
    /**
     * Post has been fetched but not yet analyzed
     */
    PENDING,
    
    /**
     * Post has been successfully analyzed
     */
    ANALYZED,
    
    /**
     * Analysis failed for this post
     */
    FAILED
}





