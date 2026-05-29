package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.dto.analysis.TextAnalysisResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.MapMarkerDTO;
import com.caglamurat.smartDisasterHub.dto.reddit.PageRequest;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditApiPost;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import com.caglamurat.smartDisasterHub.mapper.reddit.RedditPostMapper;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import com.caglamurat.smartDisasterHub.service.location.PostLocationEnrichmentService;
import com.caglamurat.smartDisasterHub.service.ml.IMlAnalysisService;
import com.caglamurat.smartDisasterHub.service.ml.OpenAiVisionMatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for managing Reddit posts
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedditPostService implements IRedditPostService {

    private final IRedditPostRepository redditPostRepository;
    private final RedditPostMapper redditPostMapper;
    private final IMlAnalysisService mlAnalysisService;
    private final OpenAiVisionMatchService openAiVisionMatchService;
    private final PostLocationEnrichmentService postLocationEnrichmentService;
    private final RedditAuthorService redditAuthorService;
    private final PostConfidenceAdjustmentService postConfidenceAdjustmentService;
    private final PostModerationStateService postModerationStateService;

    private static final PostModerationStatus PUBLIC_MOD = PostModerationStatus.APPROVED;

    @Override
    @Transactional
    public RedditPostDTO saveOrUpdatePost(RedditApiPost redditApiPost) {
        if (redditApiPost == null || redditApiPost.getId() == null) {
            log.warn("Cannot save null post or post without ID");
            return null;
        }

        // Check if post already exists
        Optional<RedditPost> existingPostOpt = redditPostRepository.findByRedditPostId(redditApiPost.getId());

        if (existingPostOpt.isPresent()) {
            // Post already exists, skip saving (prevent duplicate)
            log.debug("Reddit post {} already exists, skipping duplicate", redditApiPost.getId());
            return redditPostMapper.toDTO(existingPostOpt.get());
        }

        // Create new post (only if it doesn't exist)
        log.debug("Creating new Reddit post: {}", redditApiPost.getId());
        RedditPost post = new RedditPost();
        post.setRedditPostId(redditApiPost.getId());
        post.setTitle(redditApiPost.getTitle());
        post.setContent(redditApiPost.getSelftext());
        post.setUrl(redditApiPost.getUrl());
        // Prefer extracted image URLs (preview/gallery); fallback to raw mediaUrl
        if (redditApiPost.getMediaUrls() != null && !redditApiPost.getMediaUrls().isEmpty()) {
            post.setMediaUrl(redditApiPost.getMediaUrls().get(0));
            post.setMediaUrls(String.join(",", redditApiPost.getMediaUrls()));
        } else {
            post.setMediaUrl(redditApiPost.getMediaUrl());
            post.setMediaUrls(null);
        }
        post.setAuthor(redditApiPost.getAuthor());
        if (redditApiPost.getAuthorFullname() != null && !redditApiPost.getAuthorFullname().isBlank()) {
            post.setRedditAuthorFullname(redditApiPost.getAuthorFullname().trim());
        }
        post.setSubreddit(redditApiPost.getSubreddit());
        post.setUpvotes(redditApiPost.getUps());
        post.setCommentCount(redditApiPost.getNumComments());
        post.setStatus(RedditPostStatus.PENDING);
        post.setFetchedAt(Instant.now());
        
        if (redditApiPost.getCreatedUtc() != null) {
            post.setRedditCreatedAt(Instant.ofEpochSecond(redditApiPost.getCreatedUtc()));
        }

        postLocationEnrichmentService.enrichFromTitleAndContent(post, false);

        redditAuthorService.registerNewPost(post);

        RedditPost savedPost = redditPostRepository.save(post);
        log.info("Saved Reddit post: {} from r/{}", savedPost.getRedditPostId(), savedPost.getSubreddit());
        
        return redditPostMapper.toDTO(savedPost);
    }

    @Override
    @Transactional
    public List<RedditPostDTO> saveOrUpdatePosts(List<RedditApiPost> redditApiPosts) {
        if (redditApiPosts == null || redditApiPosts.isEmpty()) {
            return new ArrayList<>();
        }

        List<RedditPostDTO> savedPosts = new ArrayList<>();
        for (RedditApiPost apiPost : redditApiPosts) {
            try {
                RedditPostDTO savedPost = saveOrUpdatePost(apiPost);
                if (savedPost != null) {
                    savedPosts.add(savedPost);
                }
            } catch (Exception e) {
                log.error("Error saving post {}: {}", apiPost.getId(), e.getMessage(), e);
                // Continue with other posts
            }
        }

        log.info("Saved {} out of {} Reddit posts", savedPosts.size(), redditApiPosts.size());
        return savedPosts;
    }

    @Override
    @Transactional(readOnly = true)
    public RedditPostDTO findByRedditPostId(String redditPostId) {
        return redditPostRepository.findByRedditPostId(redditPostId)
                .map(redditPostMapper::toDTO)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RedditPostDTO> findPendingPosts(int limit) {
        List<RedditPost> pendingPosts = redditPostRepository.findByStatusOrderByFetchedAtAsc(RedditPostStatus.PENDING);
        
        if (limit > 0 && pendingPosts.size() > limit) {
            pendingPosts = pendingPosts.subList(0, limit);
        }
        
        return redditPostMapper.toDTOList(pendingPosts);
    }

    @Override
    @Transactional
    public RedditPostDTO analyzePost(Long postId) {
        RedditPost post = redditPostRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found with id: " + postId));

        if (post.getStatus() == RedditPostStatus.ANALYZED) {
            log.debug("Post {} already analyzed, skipping", postId);
            return redditPostMapper.toDTO(post);
        }

        try {
            postLocationEnrichmentService.enrichFromTitleAndContent(post, true);
            redditPostRepository.save(post);

            // Combine title and content for analysis
            String textToAnalyze = post.getTitle();
            if (post.getContent() != null && !post.getContent().trim().isEmpty()) {
                textToAnalyze += " " + post.getContent();
            }

            log.info("[ANALYSIS] Starting analysis for post ID: {} (Reddit ID: {})", postId, post.getRedditPostId());
            log.info("[ANALYSIS] Text to analyze (length: {}): {}", textToAnalyze.length(), 
                    textToAnalyze.length() > 200 ? textToAnalyze.substring(0, 200) + "..." : textToAnalyze);

            // Call ML service for analysis (T1 + T2)
            TextAnalysisResponse analysisResult = mlAnalysisService.analyzeText(textToAnalyze);

            log.info("[ANALYSIS] ✅ ML service analysis completed for post ID: {}", postId);
            log.info("[ANALYSIS] Result - isDisasterRelated: {}, relevanceScore: {}%", 
                    analysisResult.isDisasterRelated(), 
                    String.format("%.2f", analysisResult.getRelevanceScore() * 100));

            // Update post with analysis results (T1)
            post.setIsDisasterRelated(analysisResult.isDisasterRelated());
            post.setBaseRelevanceScore(analysisResult.getRelevanceScore());
            post.setRelevanceScore(analysisResult.getRelevanceScore());
            post.setFinalRelevanceScore(analysisResult.getRelevanceScore());
            post.setRelevanceAdjustmentDelta(0.0);
            post.setRelevanceAdjustmentReasons(null);
            post.setAnalysisMessage(analysisResult.getMessage());

            // T2: help request + humanitarian categories (if available)
            if (analysisResult.getT2() != null) {
                var t2 = analysisResult.getT2();
                post.setIsHelpRequest(t2.isHelpRequest());
                post.setHelpRequestProbability(t2.getHelpRequestProbability());
                if (t2.getHumanitarianLabels() != null && !t2.getHumanitarianLabels().isEmpty()) {
                    post.setHumanitarianCategories(String.join(",", t2.getHumanitarianLabels()));
                } else {
                    post.setHumanitarianCategories(null);
                }
            }

            // Hash (dedup) is important: compute it for any image-like mediaUrl, regardless of relevanceScore.
            if (post.getMediaUrl() != null && !post.getMediaUrl().isBlank()
                    && (post.getMediaContentHash() == null || post.getMediaContentHash().isBlank())) {
                String hash = openAiVisionMatchService.computeImageContentHash(post.getMediaUrl());
                post.setMediaContentHash(hash);
                if (hash != null && !hash.isBlank()) {
                    redditPostRepository.findFirstByMediaContentHash(hash).ifPresent(existing -> {
                        if (!existing.getId().equals(post.getId())) {
                            log.info("[DEDUP] Media hash already exists. currentPostId={}, existingPostId={}, hash={}",
                                    post.getId(), existing.getId(), hash);
                            post.setDuplicateOfPostId(existing.getId());
                        }
                    });
                }
            }

            // Run text-image match whenever media exists.
            // If mismatch is detected, confidence adjustment can strongly downgrade score.
            if (post.getMediaUrl() != null && !post.getMediaUrl().isBlank()) {
                var vision = openAiVisionMatchService.analyzeImageTextMatch(textToAnalyze, post.getMediaUrl());
                if (vision != null) {
                    post.setIsImageTextMatch(vision.getIsMatch());
                    post.setImageTextMatchScore(vision.getScore());
                    post.setImageCaption(vision.getCaption());
                    post.setImageAnalysisJson(vision.getRawJson());
                    post.setImageAnalyzedAt(Instant.now());
                }

                // Run damage analysis whenever media exists (not gated by text relevance).
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
            post.setAnalyzedAt(Instant.now());
            post.setStatus(RedditPostStatus.ANALYZED);
            postModerationStateService.applyInitialStateAfterAnalysis(post);

            RedditPost savedPost = redditPostRepository.save(post);
            redditAuthorService.onAnalysisSuccess(savedPost);
            log.info("[ANALYSIS] ✅ Post saved to database - ID: {}, Reddit ID: {}, disaster-related: {}, score: {}%", 
                    savedPost.getId(),
                    savedPost.getRedditPostId(), 
                    analysisResult.isDisasterRelated(),
                    String.format("%.2f", analysisResult.getRelevanceScore() * 100));

            return redditPostMapper.toDTO(savedPost);

        } catch (Exception e) {
            log.error("Error analyzing post {}: {}", postId, e.getMessage(), e);
            post.setStatus(RedditPostStatus.FAILED);
            redditPostRepository.save(post);
            redditAuthorService.onAnalysisFailed(post);
            throw new RuntimeException("Failed to analyze post: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public int analyzePendingPosts(int limit) {
        List<RedditPostDTO> pendingPosts = findPendingPosts(limit);
        int analyzedCount = 0;

        for (RedditPostDTO post : pendingPosts) {
            try {
                analyzePost(post.getId());
                analyzedCount++;
            } catch (Exception e) {
                log.error("Error analyzing post {}: {}", post.getId(), e.getMessage());
                // Continue with other posts
            }
        }

        log.info("Analyzed {} pending posts", analyzedCount);
        return analyzedCount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RedditPostDTO> findAnalyzedPosts(int limit) {
        List<RedditPost> analyzedPosts = redditPostRepository.findByStatus(RedditPostStatus.ANALYZED);
        
        // Sort by analyzed date (most recent first)
        analyzedPosts.sort((a, b) -> {
            if (a.getAnalyzedAt() == null && b.getAnalyzedAt() == null) return 0;
            if (a.getAnalyzedAt() == null) return 1;
            if (b.getAnalyzedAt() == null) return -1;
            return b.getAnalyzedAt().compareTo(a.getAnalyzedAt());
        });
        
        if (limit > 0 && analyzedPosts.size() > limit) {
            analyzedPosts = analyzedPosts.subList(0, limit);
        }
        
        return redditPostMapper.toDTOList(analyzedPosts);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RedditPostDTO> findAnalyzedPosts(
            PageRequest pageRequest,
            Instant reportedFrom,
            Instant reportedTo,
            PostModerationStatus moderationStatus) {
        Pageable pageable = createPageable(pageRequest);
        Page<RedditPost> page;
        if (moderationStatus != null) {
            if (!useReportedRange(reportedFrom, reportedTo)) {
                page = redditPostRepository.findByStatusAndModerationStatus(
                        RedditPostStatus.ANALYZED, moderationStatus, pageable);
            } else {
                Instant lo = rangeLo(reportedFrom, reportedTo);
                Instant hi = rangeHi(reportedFrom, reportedTo);
                page = redditPostRepository.findByStatusAndModerationStatusAndRedditCreatedAtBetween(
                        RedditPostStatus.ANALYZED, moderationStatus, lo, hi, pageable);
            }
        } else if (!useReportedRange(reportedFrom, reportedTo)) {
            page = redditPostRepository.findByStatus(RedditPostStatus.ANALYZED, pageable);
        } else {
            Instant lo = rangeLo(reportedFrom, reportedTo);
            Instant hi = rangeHi(reportedFrom, reportedTo);
            page = redditPostRepository.findByStatusAndRedditCreatedAtBetween(RedditPostStatus.ANALYZED, lo, hi, pageable);
        }

        List<RedditPostDTO> content = redditPostMapper.toDTOList(page.getContent());
        if (log.isDebugEnabled()) {
            log.debug("[PAGING] analyzed: requested page={}, size={} -> returnedElements={}, totalElements={}",
                    pageRequest.getPage(), pageRequest.getSize(),
                    content != null ? content.size() : 0,
                    page.getTotalElements());
        }
        return PageResponse.of(content, pageRequest.getPage(), pageRequest.getSize(), page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RedditPostDTO> findDisasterRelatedPosts(int limit) {
        List<RedditPost> disasterPosts = redditPostRepository.findDisasterRelatedPostsList(
                RedditPostStatus.ANALYZED, PUBLIC_MOD);
        
        if (limit > 0 && disasterPosts.size() > limit) {
            disasterPosts = disasterPosts.subList(0, limit);
        }
        
        return redditPostMapper.toDTOList(disasterPosts);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RedditPostDTO> findDisasterRelatedPosts(PageRequest pageRequest, Instant reportedFrom, Instant reportedTo) {
        Pageable pageable = createPageable(pageRequest);
        Page<RedditPost> page;
        if (!useReportedRange(reportedFrom, reportedTo)) {
            page = redditPostRepository.findDisasterRelatedPosts(RedditPostStatus.ANALYZED, PUBLIC_MOD, pageable);
        } else {
            Instant lo = rangeLo(reportedFrom, reportedTo);
            Instant hi = rangeHi(reportedFrom, reportedTo);
            page = redditPostRepository.findDisasterRelatedPostsAnalyzedBetween(
                    RedditPostStatus.ANALYZED, PUBLIC_MOD, lo, hi, pageable);
        }

        List<RedditPostDTO> content = redditPostMapper.toDTOList(page.getContent());
        if (log.isDebugEnabled()) {
            log.debug("[PAGING] disaster: requested page={}, size={} -> returnedElements={}, totalElements={}",
                    pageRequest.getPage(), pageRequest.getSize(),
                    content != null ? content.size() : 0,
                    page.getTotalElements());
        }
        return PageResponse.of(content, pageRequest.getPage(), pageRequest.getSize(), page.getTotalElements());
    }

    /**
     * Create Pageable from PageRequest
     */
    private Pageable createPageable(PageRequest pageRequest) {
        Sort.Direction direction = pageRequest.getSortDirection() == PageRequest.SortDirection.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Sort sort = RedditPostSortHelper.buildSort(pageRequest.getSortBy(), direction);
        return org.springframework.data.domain.PageRequest.of(pageRequest.getPage(), pageRequest.getSize(), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public PostStatistics getStatistics(Instant reportedFrom, Instant reportedTo) {
        long totalPosts = redditPostRepository.count();
        long mlPendingPosts = redditPostRepository.countByStatus(RedditPostStatus.PENDING);
        long failedPosts = redditPostRepository.countByStatus(RedditPostStatus.FAILED);

        long analyzedPosts;
        long approvedDisasterPosts;
        if (!useReportedRange(reportedFrom, reportedTo)) {
            analyzedPosts = redditPostRepository.countByStatus(RedditPostStatus.ANALYZED);
            approvedDisasterPosts = redditPostRepository.countDisasterRelatedByModeration(
                    RedditPostStatus.ANALYZED, PUBLIC_MOD);
        } else {
            Instant lo = rangeLo(reportedFrom, reportedTo);
            Instant hi = rangeHi(reportedFrom, reportedTo);
            analyzedPosts = redditPostRepository.countByStatusAndRedditCreatedAtBetween(RedditPostStatus.ANALYZED, lo, hi);
            approvedDisasterPosts = redditPostRepository.countDisasterRelatedAnalyzedBetween(
                    RedditPostStatus.ANALYZED, PUBLIC_MOD, lo, hi);
        }

        long pendingModerationPosts = redditPostRepository.countByStatusAndModerationStatus(
                RedditPostStatus.ANALYZED, PostModerationStatus.PENDING_REVIEW);
        long pendingPosts = mlPendingPosts + pendingModerationPosts;
        long disasterRelatedPosts = pendingModerationPosts + approvedDisasterPosts;
        long rejectedModerationPosts = redditPostRepository.countByStatusAndModerationStatus(
                RedditPostStatus.ANALYZED, PostModerationStatus.REJECTED);

        double disasterPercentage = 0.0;
        if (analyzedPosts > 0) {
            disasterPercentage = (double) disasterRelatedPosts / analyzedPosts * 100.0;
        }

        return PostStatistics.builder()
                .totalPosts(totalPosts)
                .pendingPosts(pendingPosts)
                .analyzedPosts(analyzedPosts)
                .failedPosts(failedPosts)
                .disasterRelatedPosts(disasterRelatedPosts)
                .disasterPercentage(disasterPercentage)
                .approvedDisasterPosts(approvedDisasterPosts)
                .pendingModerationPosts(pendingModerationPosts)
                .rejectedModerationPosts(rejectedModerationPosts)
                .build();
    }

    @Override
    @Transactional
    public List<MapMarkerDTO> getMapMarkers(Instant reportedFrom, Instant reportedTo) {
        List<RedditPost> candidates;
        if (!useReportedRange(reportedFrom, reportedTo)) {
            candidates = redditPostRepository.findAnalyzedMapCandidates(RedditPostStatus.ANALYZED, PUBLIC_MOD);
        } else {
            Instant lo = rangeLo(reportedFrom, reportedTo);
            Instant hi = rangeHi(reportedFrom, reportedTo);
            candidates = redditPostRepository.findAnalyzedMapCandidatesAnalyzedBetween(
                    RedditPostStatus.ANALYZED, PUBLIC_MOD, lo, hi);
        }

        for (RedditPost post : candidates) {
            if (postLocationEnrichmentService.fillMissingCoordinatesFromLocationText(post)) {
                redditPostRepository.save(post);
            }
        }

        List<RedditPost> postsWithLocation = candidates.stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .collect(Collectors.toList());

        Map<String, List<RedditPost>> grouped = new HashMap<>();
        for (RedditPost post : postsWithLocation) {
            double roundedLat = Math.round(post.getLatitude() * 100.0) / 100.0;
            double roundedLng = Math.round(post.getLongitude() * 100.0) / 100.0;
            String key = roundedLat + "," + roundedLng;
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(post);
        }

        List<MapMarkerDTO> markers = new ArrayList<>();
        for (Map.Entry<String, List<RedditPost>> e : grouped.entrySet()) {
            String[] parts = e.getKey().split(",");
            double lat = Double.parseDouble(parts[0]);
            double lng = Double.parseDouble(parts[1]);
            List<RedditPost> list = e.getValue();

            List<MapMarkerDTO.MapPostInfo> infos = list.stream()
                    .map(p -> MapMarkerDTO.MapPostInfo.builder()
                            .id(p.getId())
                            .title(p.getTitle())
                            .url(p.getUrl())
                            .contentPreview(truncateForMap(p.getContent()))
                            .locationText(p.getLocationText())
                            .locationCountry(p.getLocationCountry())
                            .locationCity(p.getLocationCity())
                            .locationRegionKey(p.getLocationRegionKey())
                            .build())
                    .collect(Collectors.toList());

            markers.add(MapMarkerDTO.builder()
                    .latitude(lat)
                    .longitude(lng)
                    .count(infos.size())
                    .posts(infos)
                    .build());
        }

        return markers;
    }

    private static String truncateForMap(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String t = content.trim().replaceAll("\\s+", " ");
        int max = 500;
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, max) + "…";
    }

    private static boolean useReportedRange(Instant from, Instant to) {
        return from != null || to != null;
    }

    private static Instant rangeLo(Instant from, Instant to) {
        Instant f = from != null ? from : Instant.EPOCH;
        Instant t = to != null ? to : Instant.now();
        return f.isAfter(t) ? t : f;
    }

    private static Instant rangeHi(Instant from, Instant to) {
        Instant f = from != null ? from : Instant.EPOCH;
        Instant t = to != null ? to : Instant.now();
        return f.isAfter(t) ? f : t;
    }
}





