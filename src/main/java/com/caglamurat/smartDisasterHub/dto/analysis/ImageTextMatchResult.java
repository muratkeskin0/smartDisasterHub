package com.caglamurat.smartDisasterHub.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of matching a post's text with its image content.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageTextMatchResult {
    private Boolean isMatch;
    /**
     * Score from 0.0 to 1.0
     */
    private Double score;
    private String caption;
    private List<String> reasons;
    private List<String> contradictions;
    /**
     * Confidence from 0.0 to 1.0
     */
    private Double confidence;
    private String modelUsed;
    /**
     * Raw JSON returned by the model (for audit/debug).
     */
    private String rawJson;
}

