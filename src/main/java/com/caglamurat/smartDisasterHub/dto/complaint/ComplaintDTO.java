package com.caglamurat.smartDisasterHub.dto.complaint;

import com.caglamurat.smartDisasterHub.enums.ComplaintCategory;
import com.caglamurat.smartDisasterHub.enums.ComplaintStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ComplaintDTO {
    private Long id;
    private String subject;
    private String body;
    private ComplaintCategory category;
    private ComplaintStatus status;
    private Long submitterId;
    private String submitterName;
    private String submitterEmail;
    private Long assignedStaffId;
    private String assignedStaffName;
    private String staffNotes;
    private Instant assignedAt;
    private Instant resolvedAt;
    private String resolvedBy;
    private Instant createdAt;
    private Instant updatedAt;
}
