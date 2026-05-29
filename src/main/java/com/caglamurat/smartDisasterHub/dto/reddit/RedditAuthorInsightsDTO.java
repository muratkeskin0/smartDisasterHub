package com.caglamurat.smartDisasterHub.dto.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RedditAuthorInsightsDTO {
    private long totalAuthors;
    private Double averageTrust;
    private List<RedditAuthorDTO> topByTrust;
    private List<RedditAuthorDTO> topByPostVolume;
}
