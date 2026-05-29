package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.domain.User;
import com.caglamurat.smartDisasterHub.dto.reddit.ModerationStatsDTO;
import com.caglamurat.smartDisasterHub.dto.reddit.PageRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.enums.ModerationQueueScope;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import com.caglamurat.smartDisasterHub.enums.UserRoleType;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.mapper.reddit.RedditPostMapper;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostModerationService {

    private final IRedditPostRepository redditPostRepository;
    private final RedditPostMapper redditPostMapper;
    private final RedditAuthorService redditAuthorService;
    private final SecurityUserContext securityUserContext;
    private final IUserRepository userRepository;

    @Transactional(readOnly = true)
    public ModerationStatsDTO getModerationStats() {
        User current = securityUserContext.requireCurrentUser();
        RedditPostStatus status = RedditPostStatus.ANALYZED;
        PostModerationStatus pending = PostModerationStatus.PENDING_REVIEW;

        long unassigned = redditPostRepository.countPendingUnassigned(status, pending);
        long mine = redditPostRepository.countPendingAssignedToModerator(status, pending, current.getId());
        long allPending = redditPostRepository.countByStatusAndModerationStatus(status, pending);

        Instant startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        long todayApproved = redditPostRepository.countReviewedByEmailSince(
                current.getEmail(), PostModerationStatus.APPROVED, startOfDay);
        long todayRejected = redditPostRepository.countReviewedByEmailSince(
                current.getEmail(), PostModerationStatus.REJECTED, startOfDay);

        return ModerationStatsDTO.builder()
                .unassignedCount(unassigned)
                .mineCount(mine)
                .allPendingCount(allPending)
                .todayApproved(todayApproved)
                .todayRejected(todayRejected)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<RedditPostDTO> findPendingReview(PageRequest pageRequest, ModerationQueueScope scope) {
        User current = securityUserContext.requireCurrentUser();
        ModerationQueueScope effective = resolveScope(scope, current);

        Sort.Direction direction = pageRequest.getSortDirection() == PageRequest.SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                pageRequest.getPage(),
                pageRequest.getSize(),
                RedditPostSortHelper.buildSort(pageRequest.getSortBy(), direction)
        );

        Page<RedditPost> page = switch (effective) {
            case MINE -> redditPostRepository.findPendingAssignedTo(
                    RedditPostStatus.ANALYZED,
                    PostModerationStatus.PENDING_REVIEW,
                    current.getId(),
                    pageable);
            case UNASSIGNED -> redditPostRepository.findPendingUnassigned(
                    RedditPostStatus.ANALYZED,
                    PostModerationStatus.PENDING_REVIEW,
                    pageable);
            case ALL -> redditPostRepository.findByStatusAndModerationStatus(
                    RedditPostStatus.ANALYZED,
                    PostModerationStatus.PENDING_REVIEW,
                    pageable);
        };

        return PageResponse.of(
                redditPostMapper.toDTOList(page.getContent()),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );
    }

    @Transactional
    public RedditPostDTO claim(Long postId) {
        User current = securityUserContext.requireCurrentUser();
        if (!securityUserContext.isStaff()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only staff can claim posts");
        }
        RedditPost post = loadPending(postId);
        if (post.getAssignedModerator() != null) {
            if (post.getAssignedModerator().getId().equals(current.getId())) {
                return redditPostMapper.toDTO(post);
            }
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Post is already assigned to another moderator");
        }
        post.setAssignedModerator(current);
        post.setAssignedAt(Instant.now());
        RedditPost saved = redditPostRepository.save(post);
        log.info("[MODERATION] Claimed post id={} by {}", postId, current.getEmail());
        return redditPostMapper.toDTO(saved);
    }

    @Transactional
    public RedditPostDTO release(Long postId) {
        User current = securityUserContext.requireCurrentUser();
        RedditPost post = loadPending(postId);
        assertCanRelease(post, current);
        post.setAssignedModerator(null);
        post.setAssignedAt(null);
        RedditPost saved = redditPostRepository.save(post);
        log.info("[MODERATION] Released post id={} by {}", postId, current.getEmail());
        return redditPostMapper.toDTO(saved);
    }

    @Transactional
    public RedditPostDTO assign(Long postId, Long managerUserId) {
        if (!securityUserContext.isAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only administrators can assign posts");
        }
        User admin = securityUserContext.requireCurrentUser();
        RedditPost post = loadPending(postId);
        User target = loadManagerUser(managerUserId);
        post.setAssignedModerator(target);
        post.setAssignedAt(Instant.now());
        RedditPost saved = redditPostRepository.save(post);
        log.info("[MODERATION] Admin {} assigned post id={} to {}", admin.getEmail(), postId, target.getEmail());
        return redditPostMapper.toDTO(saved);
    }

    @Transactional
    public RedditPostDTO approve(Long postId) {
        RedditPost post = loadForModeration(postId);
        assertCanDecide(post);
        if (post.getModerationStatus() != PostModerationStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Post is not pending review");
        }
        boolean wasDisaster = Boolean.TRUE.equals(post.getIsDisasterRelated());
        post.setModerationStatus(PostModerationStatus.APPROVED);
        post.setModerationReviewedAt(Instant.now());
        post.setModerationReviewedBy(currentReviewerEmail());
        post.setModerationNotes(null);
        clearAssignment(post);
        RedditPost saved = redditPostRepository.save(post);
        redditAuthorService.onModerationApproved(saved, wasDisaster);
        log.info("[MODERATION] Approved post id={} redditId={}", postId, saved.getRedditPostId());
        return redditPostMapper.toDTO(saved);
    }

    @Transactional
    public RedditPostDTO reject(Long postId, String notes) {
        RedditPost post = loadForModeration(postId);
        assertCanDecide(post);
        if (post.getModerationStatus() != PostModerationStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Post is not pending review");
        }
        boolean wasDisaster = Boolean.TRUE.equals(post.getIsDisasterRelated());
        post.setIsDisasterRelated(false);
        post.setModerationStatus(PostModerationStatus.REJECTED);
        post.setModerationReviewedAt(Instant.now());
        post.setModerationReviewedBy(currentReviewerEmail());
        post.setModerationNotes(notes != null && !notes.isBlank() ? notes.trim() : null);
        clearAssignment(post);
        RedditPost saved = redditPostRepository.save(post);
        redditAuthorService.onModerationRejected(saved, wasDisaster);
        log.info("[MODERATION] Rejected post id={} redditId={}", postId, saved.getRedditPostId());
        return redditPostMapper.toDTO(saved);
    }

    private ModerationQueueScope resolveScope(ModerationQueueScope scope, User current) {
        if (scope == null) {
            return securityUserContext.isAdmin() ? ModerationQueueScope.UNASSIGNED : ModerationQueueScope.MINE;
        }
        if (scope == ModerationQueueScope.ALL && !securityUserContext.isAdmin()) {
            return ModerationQueueScope.MINE;
        }
        return scope;
    }

    private void assertCanDecide(RedditPost post) {
        if (securityUserContext.isAdmin()) {
            return;
        }
        User current = securityUserContext.requireCurrentUser();
        if (post.getAssignedModerator() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Claim this post before approving or rejecting");
        }
        if (!post.getAssignedModerator().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "This post is assigned to another moderator");
        }
    }

    private void assertCanRelease(RedditPost post, User current) {
        if (securityUserContext.isAdmin()) {
            return;
        }
        if (post.getAssignedModerator() == null
                || !post.getAssignedModerator().getId().equals(current.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "You can only release posts assigned to you");
        }
    }

    private static void clearAssignment(RedditPost post) {
        post.setAssignedModerator(null);
        post.setAssignedAt(null);
    }

    private RedditPost loadPending(Long postId) {
        RedditPost post = redditPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Post not found"));
        if (post.getStatus() != RedditPostStatus.ANALYZED
                || post.getModerationStatus() != PostModerationStatus.PENDING_REVIEW) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Post is not in the moderation queue");
        }
        return post;
    }

    private RedditPost loadForModeration(Long postId) {
        RedditPost post = redditPostRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Post not found"));
        if (post.getStatus() != RedditPostStatus.ANALYZED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Only analyzed posts can be moderated");
        }
        return post;
    }

    private User loadManagerUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "User not found"));
        if (user.getRole() == null || user.getRole().getName() != UserRoleType.MANAGER) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Target user must have the MANAGER role");
        }
        return user;
    }

    private static String currentReviewerEmail() {
        return org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()
                : "system";
    }
}
