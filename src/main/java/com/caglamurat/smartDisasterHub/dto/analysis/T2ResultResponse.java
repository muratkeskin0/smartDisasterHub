package com.caglamurat.smartDisasterHub.dto.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Nested T2 result from ML service (help request + humanitarian categories).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class T2ResultResponse {

    @JsonProperty("is_help_request")
    private boolean isHelpRequest;

    @JsonProperty("help_request_probability")
    private double helpRequestProbability;

    @JsonProperty("humanitarian_labels")
    private List<String> humanitarianLabels;

    /**
     * Raw category probabilities returned by ML service.
     * Key: category name, Value: probability (0-1).
     */
    @JsonProperty("category_probabilities")
    private Map<String, Double> categoryProbabilities;
}

