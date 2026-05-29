package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.repository.IRedditAuthorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Applies heuristic adjustments on top of ML relevance score.
 * Strongly penalizes image-text mismatch, duplicate media/posts in-system, and lightly adjusts by author trust.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostConfidenceAdjustmentService {

    private final IRedditAuthorRepository redditAuthorRepository;

    public void adjust(RedditPost post) {
        if (post == null || (post.getRelevanceScore() == null && post.getBaseRelevanceScore() == null)) {
            return;
        }

        double base = clamp01(post.getBaseRelevanceScore() != null
                ? post.getBaseRelevanceScore()
                : post.getRelevanceScore());
        double adjusted = base;
        List<String> reasons = new ArrayList<>();

        boolean hasMedia = post.getMediaUrl() != null && !post.getMediaUrl().isBlank();
        if (!hasMedia) {
            adjusted -= 0.15;
            reasons.add("no_image:-0.15");
        } else if (Boolean.FALSE.equals(post.getIsImageTextMatch())) {
            // At least −0.50 from base (0–1 scale): image present but contradicts text.
            adjusted = Math.max(0, base - 0.5);
            reasons.add("image_mismatch:-0.50");
        } else if (post.getIsImageTextMatch() == null) {
            adjusted -= 0.04;
            reasons.add("image_match_unknown:-0.04");
        }

        if (post.getDuplicateOfPostId() != null) {
            double floorDuplicate = Math.max(0, base - 0.5);
            adjusted = Math.min(adjusted, floorDuplicate);
            reasons.add("duplicate_in_system:-0.50");
        }

        Double trust = resolveTrust(post);
        post.setAppliedAuthorTrustScore(trust);
        if (trust != null) {
            if (trust < 0.20) {
                adjusted -= 0.12;
                reasons.add("very_low_trust:-0.12");
            } else if (trust < 0.40) {
                adjusted -= 0.06;
                reasons.add("low_trust:-0.06");
            } else if (trust > 0.90) {
                adjusted += 0.02;
                reasons.add("high_trust:+0.02");
            }
        }

        adjusted = clamp01(adjusted);
        post.setBaseRelevanceScore(base);
        post.setFinalRelevanceScore(adjusted);
        post.setRelevanceAdjustmentDelta(adjusted - base);
        post.setRelevanceAdjustmentReasons(reasons.isEmpty() ? null : String.join(",", reasons));
        post.setRelevanceScore(adjusted);
        post.setIsDisasterRelated(adjusted >= 0.50);

        if (!reasons.isEmpty()) {
            String suffix = " | confidence_adjustments=[" + String.join(",", reasons) + "]";
            if (post.getAnalysisMessage() == null || post.getAnalysisMessage().isBlank()) {
                post.setAnalysisMessage("Adjusted relevance score." + suffix);
            } else if (!post.getAnalysisMessage().contains("confidence_adjustments=[")) {
                post.setAnalysisMessage(post.getAnalysisMessage() + suffix);
            }
        }

        log.debug("[CONFIDENCE] postId={}, base={}, adjusted={}, reasons={}",
                post.getId(), base, adjusted, reasons);
    }

    private Double resolveTrust(RedditPost post) {
        if (post.getRedditAuthorId() != null) {
            return redditAuthorRepository.findById(post.getRedditAuthorId())
                    .map(a -> a.getTrustScore())
                    .orElse(null);
        }
        if (post.getAuthor() == null || post.getAuthor().isBlank()) {
            return null;
        }
        String key = post.getAuthor().trim().toLowerCase(Locale.ROOT);
        return redditAuthorRepository.findByRedditUsername(key)
                .map(a -> a.getTrustScore())
                .orElse(null);
    }

    private static double clamp01(double v) {
        return Math.min(1.0, Math.max(0.0, v));
    }
}

