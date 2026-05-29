package com.caglamurat.smartDisasterHub.dto.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for map markers
 * Groups posts by location for display on map
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MapMarkerDTO {
    private Double latitude;
    private Double longitude;
    private Integer count;
    private List<MapPostInfo> posts;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MapPostInfo {
        private Long id;
        private String title;
        private String url;
        /** Short excerpt for popup / detail panel */
        private String contentPreview;
        /** Extracted location phrase (e.g. after Konum:) */
        private String locationText;
        private String locationCountry;
        private String locationCity;
        private String locationRegionKey;
    }
}





