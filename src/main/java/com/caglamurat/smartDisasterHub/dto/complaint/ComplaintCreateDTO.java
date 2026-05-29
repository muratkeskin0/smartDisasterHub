package com.caglamurat.smartDisasterHub.dto.complaint;

import com.caglamurat.smartDisasterHub.enums.ComplaintCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ComplaintCreateDTO {

    @NotBlank
    @Size(max = 200)
    private String subject;

    @NotBlank
    @Size(max = 5000)
    private String body;

    @NotNull
    private ComplaintCategory category;
}
