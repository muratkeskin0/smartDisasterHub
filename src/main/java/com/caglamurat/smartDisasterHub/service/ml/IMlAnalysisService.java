package com.caglamurat.smartDisasterHub.service.ml;

import com.caglamurat.smartDisasterHub.dto.analysis.TextAnalysisResponse;

/**
 * ML Analysis Service Interface
 * Handles communication with ML service for disaster relevance classification
 */
public interface IMlAnalysisService {
    
    /**
     * Analyze text for disaster relevance (T1)
     * 
     * @param text Text to analyze
     * @return TextAnalysisResponse with disaster relevance information
     */
    TextAnalysisResponse analyzeText(String text);
    
    /**
     * Check ML service health
     * 
     * @return true if service is healthy, false otherwise
     */
    boolean checkHealth();
}





