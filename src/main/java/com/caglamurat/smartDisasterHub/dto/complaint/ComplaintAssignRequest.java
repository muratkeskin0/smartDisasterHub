package com.caglamurat.smartDisasterHub.dto.complaint;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ComplaintAssignRequest {

    @NotNull
    private Long staffUserId;
}
