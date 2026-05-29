package com.caglamurat.smartDisasterHub.dto.complaint;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ComplaintStatsDTO {
    private long unassignedCount;
    private long mineCount;
    private long allOpenCount;
    private long resolvedCount;
}
