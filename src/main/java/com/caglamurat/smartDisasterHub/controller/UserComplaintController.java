package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.complaint.*;
import com.caglamurat.smartDisasterHub.dto.reddit.PageRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.enums.ComplaintInboxScope;
import com.caglamurat.smartDisasterHub.service.complaint.UserComplaintService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/complaints")
@RequiredArgsConstructor
public class UserComplaintController {

    private final UserComplaintService complaintService;

    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintDTO>> create(@Valid @RequestBody ComplaintCreateDTO body) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.create(body)));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<ComplaintDTO>>> findMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        return ResponseEntity.ok(ApiResponse.success(
                complaintService.findMine(buildPageRequest(page, size, sortBy, sortDirection))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ComplaintDTO>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.findById(id)));
    }

    @GetMapping("/inbox/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ComplaintStatsDTO>> inboxStats() {
        return ResponseEntity.ok(ApiResponse.success(complaintService.getInboxStats()));
    }

    @GetMapping("/inbox")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<PageResponse<ComplaintDTO>>> inbox(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) ComplaintInboxScope scope) {
        return ResponseEntity.ok(ApiResponse.success(
                complaintService.findInbox(buildPageRequest(page, size, sortBy, sortDirection), scope)));
    }

    @PostMapping("/inbox/{id}/claim")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ComplaintDTO>> claim(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.claim(id)));
    }

    @PostMapping("/inbox/{id}/release")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ComplaintDTO>> release(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.release(id)));
    }

    @PostMapping("/inbox/{id}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ComplaintDTO>> assign(
            @PathVariable Long id,
            @Valid @RequestBody ComplaintAssignRequest body) {
        return ResponseEntity.ok(ApiResponse.success(complaintService.assign(id, body.getStaffUserId())));
    }

    @PostMapping("/inbox/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ApiResponse<ComplaintDTO>> resolve(
            @PathVariable Long id,
            @RequestBody(required = false) ComplaintResolveRequest body) {
        String notes = body != null ? body.getNotes() : null;
        return ResponseEntity.ok(ApiResponse.success(complaintService.resolve(id, notes)));
    }

    private static PageRequest buildPageRequest(int page, int size, String sortBy, String sortDirection) {
        PageRequest.SortDirection direction;
        try {
            direction = PageRequest.SortDirection.valueOf(sortDirection.toUpperCase());
        } catch (IllegalArgumentException e) {
            direction = PageRequest.SortDirection.DESC;
        }
        return PageRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
    }
}
