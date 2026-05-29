package com.caglamurat.smartDisasterHub.config;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * One-time style backfill for rows analyzed before moderation columns existed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ModerationDataBackfill {

    private final IRedditPostRepository redditPostRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillMissingModerationStatus() {
        List<RedditPost> analyzed = redditPostRepository.findByStatus(RedditPostStatus.ANALYZED);
        int updated = 0;
        for (RedditPost post : analyzed) {
            if (post.getModerationStatus() != null) {
                continue;
            }
            if (Boolean.TRUE.equals(post.getIsDisasterRelated())) {
                post.setModerationStatus(PostModerationStatus.PENDING_REVIEW);
            } else {
                post.setIsDisasterRelated(false);
                post.setModerationStatus(PostModerationStatus.NOT_REQUIRED);
            }
            redditPostRepository.save(post);
            updated++;
        }
        if (updated > 0) {
            log.info("[MODERATION] Backfilled moderation_status on {} legacy analyzed posts", updated);
        }
    }
}
