package com.caglamurat.smartDisasterHub.dto.complaint;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplaintResolveRequest {

    @Size(max = 5000)
    private String notes;
}
