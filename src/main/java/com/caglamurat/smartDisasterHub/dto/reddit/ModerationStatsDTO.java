package com.caglamurat.smartDisasterHub.dto.reddit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationStatsDTO {
    private long unassignedCount;
    private long mineCount;
    private long allPendingCount;
    private long todayApproved;
    private long todayRejected;
}
