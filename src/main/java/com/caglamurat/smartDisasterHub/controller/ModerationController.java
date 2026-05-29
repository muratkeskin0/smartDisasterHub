package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.ModerationAssignRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.ModerationStatsDTO;
import com.caglamurat.smartDisasterHub.dto.reddit.ModerationRejectRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.enums.ModerationQueueScope;
import com.caglamurat.smartDisasterHub.service.reddit.PostModerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class ModerationController {

    private final PostModerationService postModerationService;

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ModerationStatsDTO>> getModerationStats() {
        return ResponseEntity.ok(ApiResponse.success(postModerationService.getModerationStats()));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<RedditPostDTO>>> getModerationPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "relevanceScore") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(required = false) ModerationQueueScope scope) {
        PageRequest.SortDirection direction;
        try {
            direction = PageRequest.SortDirection.valueOf(sortDirection.toUpperCase());
        } catch (IllegalArgumentException e) {
            direction = PageRequest.SortDirection.DESC;
        }
        PageRequest pageRequest = PageRequest.builder()
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(direction)
                .build();
        return ResponseEntity.ok(ApiResponse.success(
                postModerationService.findPendingReview(pageRequest, scope)));
    }

    @PostMapping("/pending/{postId}/claim")
    public ResponseEntity<ApiResponse<RedditPostDTO>> claimPost(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.success(postModerationService.claim(postId)));
    }

    @PostMapping("/pending/{postId}/release")
    public ResponseEntity<ApiResponse<RedditPostDTO>> releasePost(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.success(postModerationService.release(postId)));
    }

    @PostMapping("/pending/{postId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<RedditPostDTO>> assignPost(
            @PathVariable Long postId,
            @Valid @RequestBody ModerationAssignRequest body) {
        return ResponseEntity.ok(ApiResponse.success(
                postModerationService.assign(postId, body.getManagerUserId())));
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<ApiResponse<RedditPostDTO>> approvePost(@PathVariable Long postId) {
        return ResponseEntity.ok(ApiResponse.success(postModerationService.approve(postId)));
    }

    @PostMapping("/{postId}/reject")
    public ResponseEntity<ApiResponse<RedditPostDTO>> rejectPost(
            @PathVariable Long postId,
            @RequestBody(required = false) ModerationRejectRequest body) {
        String notes = body != null ? body.getNotes() : null;
        return ResponseEntity.ok(ApiResponse.success(postModerationService.reject(postId, notes)));
    }
}
