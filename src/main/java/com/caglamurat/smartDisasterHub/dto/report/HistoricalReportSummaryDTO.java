package com.caglamurat.smartDisasterHub.dto.report;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalReportSummaryDTO {
    private long analyzedWithHistoricalScores;
    private double averageBaseScore;
    private double averageFinalScore;
    private double averageAdjustmentDelta;
    private long penalizedCount;
    private long boostedCount;
    private long imageMismatchCount;
    private long noImagePenaltyCount;
    private long lowTrustPenaltyCount;
}

