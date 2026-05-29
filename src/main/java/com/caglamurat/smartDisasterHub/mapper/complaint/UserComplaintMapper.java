package com.caglamurat.smartDisasterHub.mapper.complaint;

import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.domain.UserComplaint;
import com.caglamurat.smartDisasterHub.dto.complaint.ComplaintDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserComplaintMapper {

    public ComplaintDTO toDTO(UserComplaint entity) {
        if (entity == null) {
            return null;
        }
        User submitter = entity.getSubmitter();
        User assigned = entity.getAssignedStaff();
        return ComplaintDTO.builder()
                .id(entity.getId())
                .subject(entity.getSubject())
                .body(entity.getBody())
                .category(entity.getCategory())
                .status(entity.getStatus())
                .submitterId(submitter != null ? submitter.getId() : null)
                .submitterName(submitter != null ? submitter.getFirstName() + " " + submitter.getLastName() : null)
                .submitterEmail(submitter != null ? submitter.getEmail() : null)
                .assignedStaffId(assigned != null ? assigned.getId() : null)
                .assignedStaffName(assigned != null ? assigned.getFirstName() + " " + assigned.getLastName() : null)
                .staffNotes(entity.getStaffNotes())
                .assignedAt(entity.getAssignedAt())
                .resolvedAt(entity.getResolvedAt())
                .resolvedBy(entity.getResolvedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public List<ComplaintDTO> toDTOList(List<UserComplaint> entities) {
        return entities.stream().map(this::toDTO).toList();
    }
}
