package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import org.springframework.stereotype.Service;

/**
 * Sets initial moderation after ML analysis completes.
 */
@Service
public class PostModerationStateService {

    /**
     * Yalnızca afet adayları kuyruğa; diğerleri {@link PostModerationStatus#NOT_REQUIRED} (APPROVED değil).
     */
    public void applyInitialStateAfterAnalysis(RedditPost post) {
        if (post == null) {
            return;
        }
        if (Boolean.TRUE.equals(post.getIsDisasterRelated())) {
            post.setModerationStatus(PostModerationStatus.PENDING_REVIEW);
            return;
        }
        post.setIsDisasterRelated(false);
        post.setModerationStatus(PostModerationStatus.NOT_REQUIRED);
    }
}
