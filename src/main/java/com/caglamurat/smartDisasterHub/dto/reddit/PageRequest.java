package com.caglamurat.smartDisasterHub.dto.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pagination request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageRequest {
    
    @Builder.Default
    private int page = 0;
    
    @Builder.Default
    private int size = 50;
    
    private String sortBy = "analyzedAt";
    
    @Builder.Default
    private SortDirection sortDirection = SortDirection.DESC;
    
    public enum SortDirection {
        ASC, DESC
    }
}





