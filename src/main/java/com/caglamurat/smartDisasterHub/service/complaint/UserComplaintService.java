package com.caglamurat.smartDisasterHub.service.complaint;

import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.domain.UserComplaint;
import com.caglamurat.smartDisasterHub.dto.complaint.ComplaintCreateDTO;
import com.caglamurat.smartDisasterHub.dto.complaint.ComplaintDTO;
import com.caglamurat.smartDisasterHub.dto.complaint.ComplaintStatsDTO;
import com.caglamurat.smartDisasterHub.dto.reddit.PageRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.enums.ComplaintInboxScope;
import com.caglamurat.smartDisasterHub.enums.ComplaintStatus;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.mapper.complaint.UserComplaintMapper;
import com.caglamurat.smartDisasterHub.repository.IUserComplaintRepository;
import com.caglamurat.smartDisasterHub.repository.IUserRepository;
import com.caglamurat.smartDisasterHub.security.SecurityUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserComplaintService {

    private static final Set<ComplaintStatus> ACTIVE_STATUSES =
            EnumSet.of(ComplaintStatus.OPEN, ComplaintStatus.IN_PROGRESS);

    private static final Set<ComplaintStatus> RESOLVED_STATUSES =
            EnumSet.of(ComplaintStatus.RESOLVED, ComplaintStatus.CLOSED);

    private final IUserComplaintRepository complaintRepository;
    private final IUserRepository userRepository;
    private final UserComplaintMapper complaintMapper;
    private final SecurityUserContext securityUserContext;

    @Transactional
    public ComplaintDTO create(ComplaintCreateDTO dto) {
        User submitter = securityUserContext.requireCurrentUser();
        UserComplaint complaint = UserComplaint.builder()
                .submitter(submitter)
                .subject(dto.getSubject().trim())
                .body(dto.getBody().trim())
                .category(dto.getCategory())
                .status(ComplaintStatus.OPEN)
                .build();
        UserComplaint saved = complaintRepository.save(complaint);
        log.info("[COMPLAINT] Created id={} by {}", saved.getId(), submitter.getEmail());
        return complaintMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplaintDTO> findMine(PageRequest pageRequest) {
        User current = securityUserContext.requireCurrentUser();
        Pageable pageable = buildPageable(pageRequest);
        Page<UserComplaint> page = complaintRepository.findBySubmitterIdOrderByCreatedAtDesc(
                current.getId(), pageable);
        return PageResponse.of(
                complaintMapper.toDTOList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ComplaintDTO findById(Long id) {
        UserComplaint complaint = loadComplaint(id);
        assertCanView(complaint);
        return complaintMapper.toDTO(complaint);
    }

    @Transactional(readOnly = true)
    public ComplaintStatsDTO getInboxStats() {
        User current = securityUserContext.requireCurrentUser();
        if (!securityUserContext.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff can view complaint inbox stats");
        }
        return ComplaintStatsDTO.builder()
                .unassignedCount(complaintRepository.countActiveUnassigned(ACTIVE_STATUSES))
                .mineCount(complaintRepository.countActiveAssignedTo(ACTIVE_STATUSES, current.getId()))
                .allOpenCount(complaintRepository.countActive(ACTIVE_STATUSES))
                .resolvedCount(complaintRepository.countByStatusIn(RESOLVED_STATUSES))
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<ComplaintDTO> findInbox(PageRequest pageRequest, ComplaintInboxScope scope) {
        User current = securityUserContext.requireCurrentUser();
        if (!securityUserContext.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff can view the complaint inbox");
        }
        ComplaintInboxScope effective = resolveScope(scope);
        Pageable pageable = buildPageable(pageRequest);

        Page<UserComplaint> page = switch (effective) {
            case MINE -> complaintRepository.findActiveAssignedTo(ACTIVE_STATUSES, current.getId(), pageable);
            case UNASSIGNED -> complaintRepository.findActiveUnassigned(ACTIVE_STATUSES, pageable);
            case ALL -> complaintRepository.findActive(ACTIVE_STATUSES, pageable);
            case RESOLVED -> complaintRepository.findByStatusIn(RESOLVED_STATUSES, pageable);
        };

        return PageResponse.of(
                complaintMapper.toDTOList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    @Transactional
    public ComplaintDTO claim(Long complaintId) {
        User current = securityUserContext.requireCurrentUser();
        assertStaff();
        UserComplaint complaint = loadActive(complaintId);
        if (complaint.getAssignedStaff() != null) {
            if (complaint.getAssignedStaff().getId().equals(current.getId())) {
                return complaintMapper.toDTO(complaint);
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Complaint is already assigned to another staff member");
        }
        complaint.setAssignedStaff(current);
        complaint.setAssignedAt(Instant.now());
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        UserComplaint saved = complaintRepository.save(complaint);
        log.info("[COMPLAINT] Claimed id={} by {}", complaintId, current.getEmail());
        return complaintMapper.toDTO(saved);
    }

    @Transactional
    public ComplaintDTO release(Long complaintId) {
        User current = securityUserContext.requireCurrentUser();
        assertStaff();
        UserComplaint complaint = loadActive(complaintId);
        assertCanRelease(complaint, current);
        complaint.setAssignedStaff(null);
        complaint.setAssignedAt(null);
        complaint.setStatus(ComplaintStatus.OPEN);
        UserComplaint saved = complaintRepository.save(complaint);
        log.info("[COMPLAINT] Released id={} by {}", complaintId, current.getEmail());
        return complaintMapper.toDTO(saved);
    }

    @Transactional
    public ComplaintDTO assign(Long complaintId, Long staffUserId) {
        if (!securityUserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only administrators can assign complaints");
        }
        User admin = securityUserContext.requireCurrentUser();
        UserComplaint complaint = loadActive(complaintId);
        User target = loadStaffUser(staffUserId);
        complaint.setAssignedStaff(target);
        complaint.setAssignedAt(Instant.now());
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        UserComplaint saved = complaintRepository.save(complaint);
        log.info("[COMPLAINT] Admin {} assigned id={} to {}", admin.getEmail(), complaintId, target.getEmail());
        return complaintMapper.toDTO(saved);
    }

    @Transactional
    public ComplaintDTO resolve(Long complaintId, String notes) {
        User current = securityUserContext.requireCurrentUser();
        assertStaff();
        UserComplaint complaint = loadActive(complaintId);
        assertCanDecide(complaint, current);
        complaint.setStatus(ComplaintStatus.RESOLVED);
        complaint.setResolvedAt(Instant.now());
        complaint.setResolvedBy(current.getEmail());
        complaint.setStaffNotes(notes != null && !notes.isBlank() ? notes.trim() : null);
        complaint.setAssignedStaff(null);
        complaint.setAssignedAt(null);
        UserComplaint saved = complaintRepository.save(complaint);
        log.info("[COMPLAINT] Resolved id={} by {}", complaintId, current.getEmail());
        return complaintMapper.toDTO(saved);
    }

    private ComplaintInboxScope resolveScope(ComplaintInboxScope scope) {
        if (scope == null) {
            return securityUserContext.isAdmin() ? ComplaintInboxScope.UNASSIGNED : ComplaintInboxScope.MINE;
        }
        if (scope == ComplaintInboxScope.ALL && !securityUserContext.isAdmin()) {
            return ComplaintInboxScope.MINE;
        }
        return scope;
    }

    private Pageable buildPageable(PageRequest pageRequest) {
        Sort.Direction direction = pageRequest.getSortDirection() == PageRequest.SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String sortBy = pageRequest.getSortBy() != null ? pageRequest.getSortBy() : "createdAt";
        return org.springframework.data.domain.PageRequest.of(
                pageRequest.getPage(),
                pageRequest.getSize(),
                Sort.by(direction, sortBy));
    }

    private void assertStaff() {
        if (!securityUserContext.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff can manage complaints");
        }
    }

    private void assertCanView(UserComplaint complaint) {
        if (securityUserContext.isStaff()) {
            return;
        }
        User current = securityUserContext.requireCurrentUser();
        if (!complaint.getSubmitter().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You can only view your own complaints");
        }
    }

    private void assertCanDecide(UserComplaint complaint, User current) {
        if (securityUserContext.isAdmin()) {
            return;
        }
        if (complaint.getAssignedStaff() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Claim this complaint before resolving it");
        }
        if (!complaint.getAssignedStaff().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This complaint is assigned to another staff member");
        }
    }

    private void assertCanRelease(UserComplaint complaint, User current) {
        if (securityUserContext.isAdmin()) {
            return;
        }
        if (complaint.getAssignedStaff() == null
                || !complaint.getAssignedStaff().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You can only release complaints assigned to you");
        }
    }

    private UserComplaint loadComplaint(Long id) {
        return complaintRepository.findWithDetailsById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Complaint not found"));
    }

    private UserComplaint loadActive(Long id) {
        UserComplaint complaint = loadComplaint(id);
        if (!ACTIVE_STATUSES.contains(complaint.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Complaint is not in the active inbox");
        }
        return complaint;
    }

    private User loadStaffUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (user.getRole() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Target user must be staff");
        }
        UserRoleType role = user.getRole().getName();
        if (role != UserRoleType.ADMIN && role != UserRoleType.MANAGER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Target user must be ADMIN or MANAGER");
        }
        return user;
    }
}
