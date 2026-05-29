package com.caglamurat.smartDisasterHub.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Distribution charts for disaster-related analyzed posts (humanitarian labels, calendar day, coarse region).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportBreakdownDto {
    private List<NamedCountDto> disasterTypes;
    private List<NamedCountDto> postsByRedditDay;
    private List<NamedCountDto> postsByRegion;
}
