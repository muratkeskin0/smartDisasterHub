package com.caglamurat.smartDisasterHub.service.ml;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.dto.analysis.TextAnalysisResponse;
import com.caglamurat.smartDisasterHub.dto.analysis.T2ResultResponse;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import com.caglamurat.smartDisasterHub.service.location.PostLocationEnrichmentService;
import com.caglamurat.smartDisasterHub.service.reddit.PostConfidenceAdjustmentService;
import com.caglamurat.smartDisasterHub.service.reddit.PostModerationStateService;
import com.caglamurat.smartDisasterHub.service.reddit.RedditAuthorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Batch analysis service:
 * - Her çalıştığında PENDING durumundaki Reddit postlarını toplu alır
 * - Önce harici AI API'sine tek istekte (batch) gönderir
 * - Harici servis başarısız olursa eski tekil pipeline'a (MlAnalysisService + Python model) geri düşer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiBatchAnalysisService {

    private final IRedditPostRepository redditPostRepository;
    private final MlAnalysisService mlAnalysisService;
    private final OpenAiVisionMatchService openAiVisionMatchService;
    private final PostLocationEnrichmentService postLocationEnrichmentService;
    private final PostConfidenceAdjustmentService postConfidenceAdjustmentService;
    private final RedditAuthorService redditAuthorService;
    private final PostModerationStateService postModerationStateService;
    private final RestTemplate restTemplate;

    @Value("${app.ml.external.enabled:false}")
    private boolean externalMlEnabled;

    /**
     * Batch endpoint for external ML.
     * Beklenen format (önerilen):
     * Request:  { \"items\": [ { \"id\": 1, \"text\": \"...\" }, ... ] }
     * Response: { \"items\": [ { \"id\": 1, \"analysis\": { ...TextAnalysisResponse... } }, ... ] }
     */
    @Value("${app.ml.external.batch-url:}")
    private String externalBatchUrl;

    @Value("${app.ml.external.api-key:}")
    private String externalMlApiKey;

    /**
     * Pending postları toplu analiz et.
     *
     * @param limit maksimum kaç pending post alınacak
     * @return analiz edilen post sayısı
     */
    public int analyzePendingPostsBatch(int limit) {
        // Pending postları al (en eski fetchedAt ilk)
        List<RedditPost> pending = redditPostRepository.findByStatusOrderByFetchedAtAsc(RedditPostStatus.PENDING);
        if (pending.isEmpty()) {
            log.debug("[AI BATCH] No pending posts to analyze.");
            return 0;
        }
        if (limit > 0 && pending.size() > limit) {
            pending = pending.subList(0, limit);
        }

        log.info("[AI BATCH] Starting batch analysis for {} pending posts", pending.size());

        // 1) Önce batch external API dene
        boolean externalOk = false;
        if (externalMlEnabled && externalBatchUrl != null && !externalBatchUrl.isBlank()) {
            try {
                externalOk = callExternalBatchAndUpdate(pending);
            } catch (Exception e) {
                log.warn("[AI BATCH] External batch analysis failed: {}", e.getMessage());
                externalOk = false;
            }
        }

        // 2) Eğer external batch çalışmadıysa, eski tekil pipeline'a geri düş
        if (!externalOk) {
            log.info("[AI BATCH] Falling back to legacy per-post analysis pipeline.");
            int analyzedCount = 0;
            for (RedditPost post : pending) {
                try {
                    postLocationEnrichmentService.enrichFromTitleAndContent(post, true);
                    redditPostRepository.save(post);

                    // Aynı mantık RedditPostService.analyzePost ile uyumlu olacak şekilde
                    StringBuilder textToAnalyze = new StringBuilder();
                    if (post.getTitle() != null) {
                        textToAnalyze.append(post.getTitle());
                    }
                    if (post.getContent() != null && !post.getContent().trim().isEmpty()) {
                        textToAnalyze.append(" ").append(post.getContent());
                    }

                    TextAnalysisResponse analysis = mlAnalysisService.analyzeText(textToAnalyze.toString());
                    applyAnalysisToPost(post, analysis);

                    // Hash (dedup) is important: compute it for any image-like mediaUrl, regardless of relevanceScore.
                    if (post.getMediaUrl() != null && !post.getMediaUrl().isBlank()
                            && (post.getMediaContentHash() == null || post.getMediaContentHash().isBlank())) {
                        String hash = openAiVisionMatchService.computeImageContentHash(post.getMediaUrl());
                        post.setMediaContentHash(hash);
                        if (hash != null && !hash.isBlank()) {
                            redditPostRepository.findFirstByMediaContentHash(hash).ifPresent(existing -> {
                                if (!existing.getId().equals(post.getId())) {
                                    post.setDuplicateOfPostId(existing.getId());
                                }
                            });
                        }
                    }

                    if (post.getMediaUrl() != null && !post.getMediaUrl().isBlank()) {
                        var vision = openAiVisionMatchService.analyzeImageTextMatch(textToAnalyze.toString(), post.getMediaUrl());
                        if (vision != null) {
                            post.setIsImageTextMatch(vision.getIsMatch());
                            post.setImageTextMatchScore(vision.getScore());
                            post.setImageCaption(vision.getCaption());
                            post.setImageAnalysisJson(vision.getRawJson());
                            post.setImageAnalyzedAt(Instant.now());
                        }

                        // Run damage analysis whenever media exists.
                        var damage = openAiVisionMatchService.analyzeImageDamage(post.getMediaUrl());
                        if (damage != null) {
                            post.setHasImageDamage((Boolean) damage.get("hasDamage"));
                            post.setImageDamageSeverity((String) damage.get("damageSeverity"));
                            post.setImageDamageScore((Double) damage.get("damageScore"));
                            post.setImageDamageAnalysisJson((String) damage.get("rawJson"));
                            post.setImageAnalyzedAt(Instant.now());
                        }
                    }
                    postConfidenceAdjustmentService.adjust(post);
                    postModerationStateService.applyInitialStateAfterAnalysis(post);
                    redditPostRepository.save(post);
                    redditAuthorService.onAnalysisSuccess(post);
                    analyzedCount++;
                } catch (Exception e) {
                    log.error("[AI BATCH] Error analyzing post {} in fallback pipeline: {}", post.getId(), e.getMessage());
                    post.setStatus(RedditPostStatus.FAILED);
                    redditPostRepository.save(post);
                    redditAuthorService.onAnalysisFailed(post);
                }
            }
            log.info("[AI BATCH] Legacy pipeline analyzed {} posts", analyzedCount);
            return analyzedCount;
        }

        // External batch başarılı ise, pending listesindeki PENDING durumunda kalanları say
        long analyzed = pending.stream()
                .filter(p -> p.getStatus() == RedditPostStatus.ANALYZED)
                .count();
        log.info("[AI BATCH] External batch analysis completed. Analyzed {} posts", analyzed);
        return (int) analyzed;
    }

    /**
     * External batch endpoint'i çağır ve gelen sonuçlara göre postları güncelle.
     *
     * @return true if call succeeded, false otherwise
     */
    @SuppressWarnings("unchecked")
    private boolean callExternalBatchAndUpdate(List<RedditPost> posts) {
        if (posts.isEmpty()) {
            return true;
        }

        Map<String, Object> payload = new HashMap<>();
        // items: [{id, text}]
        var items = posts.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            StringBuilder text = new StringBuilder();
            if (p.getTitle() != null) {
                text.append(p.getTitle());
            }
            if (p.getContent() != null && !p.getContent().trim().isEmpty()) {
                text.append(" ").append(p.getContent());
            }
            m.put("text", text.toString());
            return m;
        }).toList();
        payload.put("items", items);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (externalMlApiKey != null && !externalMlApiKey.isBlank()) {
            headers.setBearerAuth(externalMlApiKey);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        log.info("[AI BATCH] Calling external batch ML: {} items -> {}", items.size(), externalBatchUrl);

        ResponseEntity<Map> response = restTemplate.postForEntity(externalBatchUrl, entity, Map.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            log.warn("[AI BATCH] External batch returned non-2xx or empty body: {}", response.getStatusCode());
            return false;
        }

        Object itemsObj = response.getBody().get("items");
        if (!(itemsObj instanceof List<?> responseItems)) {
            log.warn("[AI BATCH] External batch response has no 'items' array");
            return false;
        }

        // Map: id -> RedditPost
        Map<Long, RedditPost> postById = new HashMap<>();
        for (RedditPost p : posts) {
            postById.put(p.getId(), p);
        }

        int updated = 0;
        for (Object o : responseItems) {
            if (!(o instanceof Map<?, ?> m)) continue;
            Object idObj = m.get("id");
            if (!(idObj instanceof Number)) continue;
            long id = ((Number) idObj).longValue();
            RedditPost post = postById.get(id);
            if (post == null) continue;

            postLocationEnrichmentService.enrichFromTitleAndContent(post, true);

            Object analysisObj = m.get("analysis");
            if (!(analysisObj instanceof Map<?, ?> a)) continue;

            // analysis map -> TextAnalysisResponse benzeri alanlar
            TextAnalysisResponse analysis = mapToTextAnalysisResponse(a);
            applyAnalysisToPost(post, analysis);
            postConfidenceAdjustmentService.adjust(post);
            postModerationStateService.applyInitialStateAfterAnalysis(post);
            redditPostRepository.save(post);
            redditAuthorService.onAnalysisSuccess(post);
            updated++;
        }

        log.info("[AI BATCH] External batch updated {} posts", updated);
        return updated > 0;
    }

    /**
     * Generic Map -> TextAnalysisResponse çevirimi (external batch responsu için).
     */
    @SuppressWarnings("unchecked")
    private TextAnalysisResponse mapToTextAnalysisResponse(Map<?, ?> m) {
        TextAnalysisResponse res = new TextAnalysisResponse();
        Object isRel = m.get("is_disaster_related");
        if (isRel instanceof Boolean b) {
            res.setDisasterRelated(b);
        }
        Object score = m.get("relevance_score");
        if (score instanceof Number n) {
            res.setRelevanceScore(n.doubleValue());
        }
        Object msg = m.get("message");
        if (msg instanceof String s) {
            res.setMessage(s);
        }
        Object model = m.get("model_used");
        if (model instanceof String s) {
            res.setModelUsed(s);
        }

        Object t2Obj = m.get("t2");
        if (t2Obj instanceof Map<?, ?> t2Map) {
            T2ResultResponse t2 = new T2ResultResponse();
            Object help = t2Map.get("is_help_request");
            if (help instanceof Boolean b) {
                t2.setHelpRequest(b);
            }
            Object helpProb = t2Map.get("help_request_probability");
            if (helpProb instanceof Number n) {
                t2.setHelpRequestProbability(n.doubleValue());
            }
            Object labels = t2Map.get("humanitarian_labels");
            if (labels instanceof List<?> list) {
                t2.setHumanitarianLabels((List<String>) (List<?>) list);
            }
            Object probs = t2Map.get("category_probabilities");
            if (probs instanceof Map<?, ?> map) {
                t2.setCategoryProbabilities((Map<String, Double>) (Map<?, ?>) map);
            }
            res.setT2(t2);
        }
        return res;
    }

    /**
     * Tekil TextAnalysisResponse sonucunu ilgili RedditPost kaydına uygula.
     * (RedditPostService.analyzePost ile aynı alanlar)
     */
    private void applyAnalysisToPost(RedditPost post, TextAnalysisResponse analysisResult) {
        if (analysisResult == null) {
            return;
        }

        post.setIsDisasterRelated(analysisResult.isDisasterRelated());
        post.setBaseRelevanceScore(analysisResult.getRelevanceScore());
        post.setRelevanceScore(analysisResult.getRelevanceScore());
        post.setFinalRelevanceScore(analysisResult.getRelevanceScore());
        post.setRelevanceAdjustmentDelta(0.0);
        post.setRelevanceAdjustmentReasons(null);
        post.setAnalysisMessage(analysisResult.getMessage());
        post.setAnalyzedAt(Instant.now());
        post.setStatus(RedditPostStatus.ANALYZED);

        if (analysisResult.getT2() != null) {
            T2ResultResponse t2 = analysisResult.getT2();
            post.setIsHelpRequest(t2.isHelpRequest());
            post.setHelpRequestProbability(t2.getHelpRequestProbability());
            if (t2.getHumanitarianLabels() != null && !t2.getHumanitarianLabels().isEmpty()) {
                post.setHumanitarianCategories(String.join(",", t2.getHumanitarianLabels()));
            } else {
                post.setHumanitarianCategories(null);
            }
        }
    }
}

