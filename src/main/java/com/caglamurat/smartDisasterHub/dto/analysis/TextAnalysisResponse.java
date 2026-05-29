package com.caglamurat.smartDisasterHub.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for text analysis
 * Maps to ML service response format
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TextAnalysisResponse {
    
    @JsonProperty("is_disaster_related")
    private boolean isDisasterRelated;
    
    @JsonProperty("relevance_score")
    private double relevanceScore;
    
    private String message;
    
    @JsonProperty("model_used")
    private String modelUsed;  // "logistic_regression" or "roberta"

    /**
     * Optional external-classifier rationale fields for metaphor/literal handling.
     */
    @JsonProperty("is_literal_disaster")
    private Boolean isLiteralDisaster;

    @JsonProperty("is_mecaz")
    private Boolean isMecaz;

    @JsonProperty("literal_confidence")
    private Double literalConfidence;

    @JsonProperty("is_real_world_incident_report")
    private Boolean isRealWorldIncidentReport;

    @JsonProperty("incident_report_confidence")
    private Double incidentReportConfidence;

    /**
     * Optional T2 result (help request + humanitarian categories) when calling /analyze endpoint.
     */
    @JsonProperty("t2")
    private T2ResultResponse t2;
}





