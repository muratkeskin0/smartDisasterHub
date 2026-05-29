package com.caglamurat.smartDisasterHub.config;

import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Düzeltme: afet adayı olmayan ama yanlışlıkla APPROVED işaretlenmiş satırları NOT_REQUIRED yapar.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(100)
public class ModerationNotRequiredMigration {

    private final IRedditPostRepository redditPostRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateAutoApprovedNonDisaster() {
        int n = redditPostRepository.reclassifyApprovedNonDisasterToNotRequired(
                PostModerationStatus.NOT_REQUIRED,
                PostModerationStatus.APPROVED);
        if (n > 0) {
            log.info("[MODERATION] Reclassified {} non-disaster APPROVED rows to NOT_REQUIRED", n);
        }
    }
}
