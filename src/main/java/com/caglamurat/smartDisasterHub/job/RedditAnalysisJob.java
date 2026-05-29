package com.caglamurat.smartDisasterHub.job;

import com.caglamurat.smartDisasterHub.service.ml.AiBatchAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job for analyzing Reddit posts
 * PENDING durumundaki postları analiz eder
 * Veri geldikten 1 dakika sonra çalışır (60,000 milliseconds)
 * Her 1 dakikada bir pending postları kontrol eder
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedditAnalysisJob {

    private final AiBatchAnalysisService aiBatchAnalysisService;

    @Value("${app.reddit.analysis-batch-size:50}")
    private int analysisBatchSize;

    @Value("${app.reddit.analysis-enabled:true}")
    private boolean analysisEnabled;

    /**
     * Analyze pending Reddit posts
     * Veri geldikten 1 dakika sonra başlar ve her 1 dakikada bir çalışır
     * Initial delay: 60,000 ms (1 dakika)
     * Fixed delay: 60,000 ms (1 dakika)
     * Can also be called manually via controller
     */
    @Scheduled(fixedDelayString = "${app.reddit.analysis-interval:60000}", initialDelayString = "${app.reddit.analysis-initial-delay:60000}")
    public void analyzePendingPosts() {
        executeAnalysisJob();
    }
    
    /**
     * Manual trigger for Reddit analysis job
     * Can be called from controller to manually analyze pending posts
     */
    public void executeAnalysisJob() {
        if (!analysisEnabled) {
            log.debug("Analysis job is disabled, skipping...");
            return;
        }

        log.info("Starting Reddit analysis job...");

        try {
            // Analyze pending posts in batches using AI batch service
            int analyzedCount = aiBatchAnalysisService.analyzePendingPostsBatch(analysisBatchSize);
            
            if (analyzedCount > 0) {
                log.info("Analyzed {} pending posts", analyzedCount);
            } else {
                log.debug("No pending posts to analyze");
            }

            log.info("Reddit analysis job completed successfully");

        } catch (Exception e) {
            log.error("Error in Reddit analysis job: {}", e.getMessage(), e);
        }
    }
}




