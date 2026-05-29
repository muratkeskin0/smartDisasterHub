package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.domain.RedditAuthor;
import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import com.caglamurat.smartDisasterHub.repository.IRedditAuthorRepository;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tracks Reddit usernames seen in fetched posts: counts and a simple trust score from history (T6-style).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedditAuthorService {

    private final IRedditAuthorRepository redditAuthorRepository;
    private final IRedditPostRepository redditPostRepository;

    public static boolean shouldSkipAuthor(String raw) {
        if (raw == null) {
            return true;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        return "[deleted]".equals(lower) || "automoderator".equals(lower);
    }

    public static String normalizeUsername(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Call when a new {@link RedditPost} row is created (ingest). Links {@code redditAuthorId} on the post.
     */
    @Transactional
    public void registerNewPost(RedditPost post) {
        if (post == null || shouldSkipAuthor(post.getAuthor())) {
            return;
        }
        String key = normalizeUsername(post.getAuthor());
        Instant now = Instant.now();
        RedditAuthor author = redditAuthorRepository.findByRedditUsername(key).orElseGet(() ->
                RedditAuthor.builder()
                        .redditUsername(key)
                        .totalPosts(0)
                        .analyzedPosts(0)
                        .disasterRelatedPosts(0)
                        .failedAnalysisPosts(0)
                        .moderationApprovedPosts(0)
                        .moderationRejectedPosts(0)
                        .trustScore(0.35)
                        .firstSeenAt(now)
                        .build()
        );
        author.setTotalPosts(author.getTotalPosts() + 1);
        author.setLastPostAt(now);
        if (author.getFirstSeenAt() == null) {
            author.setFirstSeenAt(now);
        }
        applyRedditUserIdFromPost(author, post);
        author.setTrustScore(computeTrust(author));
        RedditAuthor saved = redditAuthorRepository.save(author);
        post.setRedditAuthorId(saved.getId());
    }

    /**
     * Sets {@link RedditAuthor#setRedditUserId(String)} from post’s {@code redditAuthorFullname} (API {@code author_fullname}).
     */
    private void applyRedditUserIdFromPost(RedditAuthor author, RedditPost post) {
        if (post.getRedditAuthorFullname() == null || post.getRedditAuthorFullname().isBlank()) {
            return;
        }
        String id = post.getRedditAuthorFullname().trim();
        if (author.getRedditUserId() == null || author.getRedditUserId().isBlank()) {
            author.setRedditUserId(id);
        }
    }

    /**
     * Call after ML analysis finishes successfully ({@link RedditPostStatus#ANALYZED}).
     */
    @Transactional
    public void onAnalysisSuccess(RedditPost post) {
        if (post == null || shouldSkipAuthor(post.getAuthor())) {
            return;
        }
        String key = normalizeUsername(post.getAuthor());
        RedditAuthor author = findOrAttach(post, key);
        applyRedditUserIdFromPost(author, post);
        author.setAnalyzedPosts(author.getAnalyzedPosts() + 1);
        if (Boolean.TRUE.equals(post.getIsDisasterRelated())) {
            author.setDisasterRelatedPosts(author.getDisasterRelatedPosts() + 1);
        }
        author.setTrustScore(computeTrust(author));
        redditAuthorRepository.save(author);
    }

    /**
     * Human approved a pending disaster candidate.
     */
    @Transactional
    public void onModerationApproved(RedditPost post, boolean wasDisasterCandidate) {
        if (post == null || shouldSkipAuthor(post.getAuthor())) {
            return;
        }
        RedditAuthor author = findOrAttach(post, normalizeUsername(post.getAuthor()));
        author.setModerationApprovedPosts(author.getModerationApprovedPosts() + 1);
        author.setTrustScore(computeTrust(author));
        redditAuthorRepository.save(author);
    }

    /**
     * Human rejected a pending disaster candidate; lowers trust via rejection ratio.
     */
    @Transactional
    public void onModerationRejected(RedditPost post, boolean wasDisasterCandidate) {
        if (post == null || shouldSkipAuthor(post.getAuthor())) {
            return;
        }
        RedditAuthor author = findOrAttach(post, normalizeUsername(post.getAuthor()));
        author.setModerationRejectedPosts(author.getModerationRejectedPosts() + 1);
        if (wasDisasterCandidate && author.getDisasterRelatedPosts() > 0) {
            author.setDisasterRelatedPosts(author.getDisasterRelatedPosts() - 1);
        }
        author.setTrustScore(computeTrust(author));
        redditAuthorRepository.save(author);
    }

    /**
     * Call when analysis ends in {@link RedditPostStatus#FAILED}.
     */
    @Transactional
    public void onAnalysisFailed(RedditPost post) {
        if (post == null || shouldSkipAuthor(post.getAuthor())) {
            return;
        }
        String key = normalizeUsername(post.getAuthor());
        RedditAuthor author = findOrAttach(post, key);
        applyRedditUserIdFromPost(author, post);
        author.setFailedAnalysisPosts(author.getFailedAnalysisPosts() + 1);
        author.setTrustScore(computeTrust(author));
        redditAuthorRepository.save(author);
    }

    private RedditAuthor findOrAttach(RedditPost post, String key) {
        if (post.getRedditAuthorId() != null) {
            return redditAuthorRepository.findById(post.getRedditAuthorId())
                    .orElseGet(() -> redditAuthorRepository.findByRedditUsername(key)
                            .orElseGet(() -> createStubAuthor(key)));
        }
        Optional<RedditAuthor> byName = redditAuthorRepository.findByRedditUsername(key);
        RedditAuthor author = byName.orElseGet(() -> createStubAuthor(key));
        post.setRedditAuthorId(author.getId());
        return author;
    }

    private RedditAuthor createStubAuthor(String key) {
        Instant now = Instant.now();
        RedditAuthor a = RedditAuthor.builder()
                .redditUsername(key)
                .totalPosts(0)
                .analyzedPosts(0)
                .disasterRelatedPosts(0)
                .failedAnalysisPosts(0)
                .trustScore(0.35)
                .firstSeenAt(now)
                .lastPostAt(now)
                .build();
        return redditAuthorRepository.save(a);
    }

    /**
     * Full rebuild from {@code reddit_posts} (for migration / repair). Overwrites counters and trust.
     */
    @Transactional
    public int rebuildAllFromPosts() {
        List<String> keys = redditPostRepository.findDistinctNormalizedAuthors();
        int n = 0;
        for (String key : keys) {
            if (shouldSkipAuthor(key)) {
                continue;
            }
            long total = redditPostRepository.countByAuthorNormalized(key);
            long analyzed = redditPostRepository.countByAuthorNormalizedAndStatus(key, RedditPostStatus.ANALYZED);
            long failed = redditPostRepository.countByAuthorNormalizedAndStatus(key, RedditPostStatus.FAILED);
            long disaster = redditPostRepository.countDisasterRelatedByAuthorNormalized(key, RedditPostStatus.ANALYZED);
            long modApproved = redditPostRepository.countByAuthorNormalizedAndModerationStatus(
                    key, PostModerationStatus.APPROVED);
            long modRejected = redditPostRepository.countByAuthorNormalizedAndModerationStatus(
                    key, PostModerationStatus.REJECTED);

            RedditAuthor author = redditAuthorRepository.findByRedditUsername(key).orElseGet(() ->
                    RedditAuthor.builder().redditUsername(key).build());
            author.setTotalPosts((int) Math.min(Integer.MAX_VALUE, total));
            author.setAnalyzedPosts((int) Math.min(Integer.MAX_VALUE, analyzed));
            author.setFailedAnalysisPosts((int) Math.min(Integer.MAX_VALUE, failed));
            author.setDisasterRelatedPosts((int) Math.min(Integer.MAX_VALUE, disaster));
            author.setModerationApprovedPosts((int) Math.min(Integer.MAX_VALUE, modApproved));
            author.setModerationRejectedPosts((int) Math.min(Integer.MAX_VALUE, modRejected));
            author.setTrustScore(computeTrust(author));
            if (author.getFirstSeenAt() == null) {
                author.setFirstSeenAt(Instant.now());
            }
            author.setLastPostAt(Instant.now());
            Page<RedditPost> fullnamePage =
                    redditPostRepository.findLatestWithAuthorFullname(key, PageRequest.of(0, 1));
            fullnamePage.getContent().stream()
                    .findFirst()
                    .map(RedditPost::getRedditAuthorFullname)
                    .filter(s -> s != null && !s.isBlank())
                    .ifPresent(fid -> {
                        if (author.getRedditUserId() == null || author.getRedditUserId().isBlank()) {
                            author.setRedditUserId(fid.trim());
                        }
                    });
            redditAuthorRepository.save(author);
            n++;
        }
        log.info("[REDDIT_AUTHORS] Rebuilt {} author rows from reddit_posts", n);
        return n;
    }

    static double computeTrust(RedditAuthor a) {
        int analyzed = Math.max(0, a.getAnalyzedPosts());
        int disaster = Math.max(0, a.getDisasterRelatedPosts());
        int failed = Math.max(0, a.getFailedAnalysisPosts());

        if (analyzed == 0 && failed == 0) {
            return clamp01(0.35);
        }

        double disasterRatio = analyzed == 0 ? 0.0 : (double) disaster / (double) analyzed;
        int attempts = analyzed + failed;
        double failureRatio = attempts == 0 ? 0.0 : (double) failed / (double) attempts;

        double volumeFactor = Math.min(1.0, analyzed / 20.0);

        int modApproved = Math.max(0, a.getModerationApprovedPosts());
        int modRejected = Math.max(0, a.getModerationRejectedPosts());
        int modDecisions = modApproved + modRejected;
        double moderationRejectRatio = modDecisions == 0 ? 0.0 : (double) modRejected / (double) modDecisions;

        double score = 0.18 + 0.52 * disasterRatio + 0.22 * volumeFactor - 0.25 * failureRatio
                - 0.30 * moderationRejectRatio;
        return clamp01(score);
    }

    private static double clamp01(double v) {
        return Math.min(1.0, Math.max(0.05, v));
    }
}
