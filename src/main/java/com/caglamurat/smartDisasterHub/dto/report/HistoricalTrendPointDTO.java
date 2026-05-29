package com.caglamurat.smartDisasterHub.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalTrendPointDTO {
    private LocalDate day;
    private long postCount;
    private double avgBaseScore;
    private double avgFinalScore;
    private double avgDelta;
}

