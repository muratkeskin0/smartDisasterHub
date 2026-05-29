package com.caglamurat.smartDisasterHub.dto.reddit;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModerationAssignRequest {
    @NotNull(message = "Manager user id is required")
    private Long managerUserId;
}
