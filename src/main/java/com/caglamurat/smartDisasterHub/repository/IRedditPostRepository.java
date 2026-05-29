package com.caglamurat.smartDisasterHub.repository;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IRedditPostRepository extends JpaRepository<RedditPost, Long> {
    interface DailyHistoricalTrendRow {
        LocalDate getDay();
        Long getPostCount();
        Double getAvgBaseScore();
        Double getAvgFinalScore();
        Double getAvgDelta();
    }

    /**
     * Find post by Reddit post ID
     */
    Optional<RedditPost> findByRedditPostId(String redditPostId);

    /**
     * Find post by URL
     */
    Optional<RedditPost> findByUrl(String url);

    /**
     * Find a post that already has the same media content hash (deduplication).
     */
    Optional<RedditPost> findFirstByMediaContentHash(String mediaContentHash);

    /**
     * Check if post exists by Reddit post ID
     */
    boolean existsByRedditPostId(String redditPostId);

    /**
     * Check if post exists by URL
     */
    boolean existsByUrl(String url);

    /**
     * Find posts by status
     */
    List<RedditPost> findByStatus(RedditPostStatus status);

    /**
     * Find pending posts that need analysis
     */
    List<RedditPost> findByStatusOrderByFetchedAtAsc(RedditPostStatus status);

    /**
     * Find analyzed posts that are disaster-related (legacy method, kept for backward compatibility)
     */
    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.isDisasterRelated = true
              AND rp.moderationStatus = :mod
            ORDER BY rp.analyzedAt DESC
            """)
    List<RedditPost> findDisasterRelatedPostsList(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    Page<RedditPost> findByStatusAndModerationStatus(
            RedditPostStatus status,
            PostModerationStatus moderationStatus,
            Pageable pageable);

    long countByStatusAndModerationStatus(RedditPostStatus status, PostModerationStatus moderationStatus);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :st AND rp.isDisasterRelated = true AND rp.moderationStatus = :mod
            """)
    long countDisasterRelatedByModeration(
            @Param("st") RedditPostStatus st,
            @Param("mod") PostModerationStatus mod);

    @Query("SELECT COUNT(rp) FROM RedditPost rp WHERE LOWER(TRIM(rp.author)) = :u AND rp.moderationStatus = :mod")
    long countByAuthorNormalizedAndModerationStatus(
            @Param("u") String normalizedUsername,
            @Param("mod") PostModerationStatus mod);

    /** Afet adayı olmayan eski APPROVED satırlarını NOT_REQUIRED yapar. */
    @Modifying
    @Query("""
            UPDATE RedditPost rp SET rp.moderationStatus = :notRequired
            WHERE rp.moderationStatus = :approved
              AND (rp.isDisasterRelated = false OR rp.isDisasterRelated IS NULL)
            """)
    int reclassifyApprovedNonDisasterToNotRequired(
            @Param("notRequired") PostModerationStatus notRequired,
            @Param("approved") PostModerationStatus approved);

    /**
     * Count posts by status
     */
    long countByStatus(RedditPostStatus status);

    /**
     * Find posts fetched within a time range
     */
    List<RedditPost> findByFetchedAtBetween(Instant start, Instant end);

    /**
     * Find posts by subreddit
     */
    List<RedditPost> findBySubredditOrderByFetchedAtDesc(String subreddit);

    /**
     * Find analyzed posts with pagination and sorting
     */
    Page<RedditPost> findByStatus(RedditPostStatus status, Pageable pageable);

    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.assignedModerator IS NULL
            """)
    Page<RedditPost> findPendingUnassigned(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus moderationStatus,
            Pageable pageable);

    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.assignedModerator.id = :moderatorId
            """)
    Page<RedditPost> findPendingAssignedTo(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus moderationStatus,
            @Param("moderatorId") Long moderatorId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.assignedModerator IS NULL
            """)
    long countPendingUnassigned(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus moderationStatus);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.assignedModerator.id = :moderatorId
            """)
    long countPendingAssignedToModerator(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus moderationStatus,
            @Param("moderatorId") Long moderatorId);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.moderationReviewedBy = :email
              AND rp.moderationStatus = :mod
              AND rp.moderationReviewedAt >= :since
            """)
    long countReviewedByEmailSince(
            @Param("email") String email,
            @Param("mod") PostModerationStatus moderationStatus,
            @Param("since") Instant since);

    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.redditCreatedAt BETWEEN :start AND :end
            """)
    Page<RedditPost> findByStatusAndModerationStatusAndRedditCreatedAtBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus moderationStatus,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    Page<RedditPost> findByStatusAndRedditCreatedAtBetween(
            RedditPostStatus status, Instant start, Instant end, Pageable pageable);

    long countByStatusAndRedditCreatedAtBetween(RedditPostStatus status, Instant start, Instant end);

    /**
     * Find disaster-related analyzed posts with pagination and sorting
     */
    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.isDisasterRelated = true AND rp.moderationStatus = :mod
            """)
    Page<RedditPost> findDisasterRelatedPosts(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            Pageable pageable);

    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.isDisasterRelated = true AND rp.moderationStatus = :mod
              AND rp.redditCreatedAt BETWEEN :start AND :end
            """)
    Page<RedditPost> findDisasterRelatedPostsAnalyzedBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :st AND rp.isDisasterRelated = true AND rp.moderationStatus = :mod
              AND rp.redditCreatedAt BETWEEN :start AND :end
            """)
    long countDisasterRelatedAnalyzedBetween(
            @Param("st") RedditPostStatus st,
            @Param("mod") PostModerationStatus mod,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Find analyzed disaster-related posts with location data
     */
    @Query("SELECT rp FROM RedditPost rp WHERE rp.status = :status AND rp.isDisasterRelated = true AND rp.latitude IS NOT NULL AND rp.longitude IS NOT NULL")
    List<RedditPost> findDisasterRelatedPostsWithLocation(@Param("status") RedditPostStatus status);

    /**
     * Analyzed posts that can appear on the map: coordinates set, or non-blank {@code locationText} (geocoded on load).
     * Not limited to disaster-related so manual DB rows / edge cases still show.
     */
    @Query("""
            SELECT rp FROM RedditPost rp WHERE rp.status = :status
              AND rp.moderationStatus = :mod AND rp.isDisasterRelated = true
              AND ((rp.latitude IS NOT NULL AND rp.longitude IS NOT NULL)
                OR (rp.locationText IS NOT NULL AND TRIM(rp.locationText) <> ''))
            """)
    List<RedditPost> findAnalyzedMapCandidates(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT rp FROM RedditPost rp WHERE rp.status = :status
              AND rp.moderationStatus = :mod AND rp.isDisasterRelated = true
              AND ((rp.latitude IS NOT NULL AND rp.longitude IS NOT NULL)
                OR (rp.locationText IS NOT NULL AND TRIM(rp.locationText) <> ''))
              AND rp.redditCreatedAt BETWEEN :start AND :end
            """)
    List<RedditPost> findAnalyzedMapCandidatesAnalyzedBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("SELECT COUNT(rp) FROM RedditPost rp WHERE LOWER(TRIM(rp.author)) = :u")
    long countByAuthorNormalized(@Param("u") String normalizedUsername);

    @Query("SELECT COUNT(rp) FROM RedditPost rp WHERE LOWER(TRIM(rp.author)) = :u AND rp.status = :st")
    long countByAuthorNormalizedAndStatus(@Param("u") String normalizedUsername, @Param("st") RedditPostStatus status);

    @Query("SELECT COUNT(rp) FROM RedditPost rp WHERE LOWER(TRIM(rp.author)) = :u AND rp.status = :st AND rp.isDisasterRelated = true")
    long countDisasterRelatedByAuthorNormalized(@Param("u") String normalizedUsername, @Param("st") RedditPostStatus status);

    @Query(value = "SELECT DISTINCT LOWER(TRIM(author)) FROM reddit_posts WHERE author IS NOT NULL "
            + "AND TRIM(author) <> '' AND LOWER(TRIM(author)) NOT IN ('[deleted]','automoderator','auto moderator')",
            nativeQuery = true)
    List<String> findDistinctNormalizedAuthors();

    @Query("SELECT rp FROM RedditPost rp WHERE LOWER(TRIM(rp.author)) = :u AND rp.redditAuthorFullname IS NOT NULL "
            + "AND TRIM(rp.redditAuthorFullname) <> '' ORDER BY rp.id DESC")
    Page<RedditPost> findLatestWithAuthorFullname(@Param("u") String normalizedUsername, Pageable pageable);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.baseRelevanceScore IS NOT NULL
              AND rp.finalRelevanceScore IS NOT NULL
            """)
    long countAnalyzedWithHistoricalScores(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.baseRelevanceScore IS NOT NULL
              AND rp.finalRelevanceScore IS NOT NULL
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    long countAnalyzedWithHistoricalScoresBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(AVG(rp.baseRelevanceScore), 0) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.baseRelevanceScore IS NOT NULL
            """)
    Double averageBaseScore(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT COALESCE(AVG(rp.baseRelevanceScore), 0) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.baseRelevanceScore IS NOT NULL
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    Double averageBaseScoreBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(AVG(rp.finalRelevanceScore), 0) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.finalRelevanceScore IS NOT NULL
            """)
    Double averageFinalScore(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT COALESCE(AVG(rp.finalRelevanceScore), 0) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.finalRelevanceScore IS NOT NULL
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    Double averageFinalScoreBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta < 0
            """)
    long countPenalized(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta < 0
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    long countPenalizedBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta > 0
            """)
    long countBoosted(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta > 0
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    long countBoostedBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COALESCE(AVG(rp.relevanceAdjustmentDelta), 0) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta IS NOT NULL
            """)
    Double averageAdjustmentDelta(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod);

    @Query("""
            SELECT COALESCE(AVG(rp.relevanceAdjustmentDelta), 0) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta IS NOT NULL
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    Double averageAdjustmentDeltaBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentReasons IS NOT NULL
              AND LOWER(rp.relevanceAdjustmentReasons) LIKE %:needle%
            """)
    long countByAdjustmentReasonContains(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("needle") String needle);

    @Query("""
            SELECT COUNT(rp) FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentReasons IS NOT NULL
              AND LOWER(rp.relevanceAdjustmentReasons) LIKE CONCAT('%', LOWER(:needle), '%')
              AND rp.redditCreatedAt BETWEEN :from AND :to
            """)
    long countByAdjustmentReasonContainsBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("needle") String needle,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta IS NOT NULL
            ORDER BY ABS(rp.relevanceAdjustmentDelta) DESC, rp.analyzedAt DESC
            """)
    Page<RedditPost> findTopAdjustedPosts(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            Pageable pageable);

    @Query("""
            SELECT rp FROM RedditPost rp
            WHERE rp.status = :status AND rp.moderationStatus = :mod
              AND rp.relevanceAdjustmentDelta IS NOT NULL
              AND rp.redditCreatedAt BETWEEN :start AND :end
            ORDER BY ABS(rp.relevanceAdjustmentDelta) DESC, rp.analyzedAt DESC
            """)
    Page<RedditPost> findTopAdjustedPostsBetween(
            @Param("status") RedditPostStatus status,
            @Param("mod") PostModerationStatus mod,
            @Param("start") Instant start,
            @Param("end") Instant end,
            Pageable pageable);

    @Query(value = """
            SELECT DATE(rp.reddit_created_at) AS day,
                   COUNT(*) AS postCount,
                   AVG(rp.base_relevance_score) AS avgBaseScore,
                   AVG(rp.final_relevance_score) AS avgFinalScore,
                   AVG(rp.relevance_adjustment_delta) AS avgDelta
            FROM reddit_posts rp
            WHERE rp.status = :status
              AND rp.moderation_status = :modStatus
              AND rp.reddit_created_at IS NOT NULL
              AND rp.base_relevance_score IS NOT NULL
              AND rp.final_relevance_score IS NOT NULL
              AND rp.reddit_created_at >= :fromTs
              AND rp.reddit_created_at <= :toTs
            GROUP BY DATE(rp.reddit_created_at)
            ORDER BY DATE(rp.reddit_created_at) ASC
            """, nativeQuery = true)
    List<DailyHistoricalTrendRow> findDailyHistoricalTrend(
            @Param("status") String status,
            @Param("modStatus") String modStatus,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs);

    @Query("""
            SELECT rp.humanitarianCategories, COUNT(rp)
            FROM RedditPost rp
            WHERE rp.status = :st
              AND rp.isDisasterRelated = true
              AND rp.moderationStatus = :mod
              AND (:from IS NULL OR rp.redditCreatedAt >= :from)
              AND (:to IS NULL OR rp.redditCreatedAt <= :to)
            GROUP BY rp.humanitarianCategories
            """)
    List<Object[]> groupDisasterHumanitarianCombos(
            @Param("st") RedditPostStatus st,
            @Param("mod") PostModerationStatus mod,
            @Param("from") Instant from,
            @Param("to") Instant to);

    interface RedditDayCountRow {
        java.sql.Date getDay();

        Long getPostCount();
    }

    @Query(value = """
            SELECT DATE(r.reddit_created_at) AS day, COUNT(*) AS postCount
            FROM reddit_posts r
            WHERE r.status = :status
              AND r.moderation_status = :modStatus
              AND r.is_disaster_related = 1
              AND (:fromTs IS NULL OR r.reddit_created_at >= :fromTs)
              AND (:toTs IS NULL OR r.reddit_created_at <= :toTs)
            GROUP BY DATE(r.reddit_created_at)
            ORDER BY DATE(r.reddit_created_at) ASC
            """, nativeQuery = true)
    List<RedditDayCountRow> countDisasterPostsByRedditDay(
            @Param("status") String status,
            @Param("modStatus") String modStatus,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs);

    interface RegionCountRow {
        String getRegionKey();

        Long getPostCount();
    }

            @Query(value = """
            SELECT t.bucket AS regionKey, COUNT(*) AS postCount
            FROM (
                SELECT CASE
                    WHEN r.location_region_key IS NOT NULL AND TRIM(r.location_region_key) <> '' THEN TRIM(r.location_region_key)
                    WHEN r.latitude IS NULL OR r.longitude IS NULL THEN 'unknown'
                    WHEN r.latitude < 36 OR r.latitude > 42 OR r.longitude < 26 OR r.longitude > 45 THEN 'outside_tr'
                    WHEN r.latitude >= 40.0 AND r.latitude <= 41.6 AND r.longitude >= 26.5 AND r.longitude <= 30.2 THEN 'marmara'
                    WHEN r.latitude >= 37.5 AND r.latitude < 40.0 AND r.longitude >= 26.0 AND r.longitude < 30.5 THEN 'aegean'
                    WHEN r.latitude >= 36.0 AND r.latitude < 37.8 AND r.longitude >= 30.0 AND r.longitude < 36.5 THEN 'mediterranean'
                    WHEN r.longitude >= 39.8 THEN 'east_anatolia'
                    WHEN r.latitude >= 39.0 AND r.longitude >= 31.0 AND r.longitude < 39.8 THEN 'central'
                    WHEN r.latitude > 40.2 AND r.longitude > 30.0 THEN 'black_sea'
                    ELSE 'other_tr'
                END AS bucket
                FROM reddit_posts r
                WHERE r.status = :status
                  AND r.moderation_status = :modStatus
                  AND r.is_disaster_related = 1
                  AND (:fromTs IS NULL OR r.reddit_created_at >= :fromTs)
                  AND (:toTs IS NULL OR r.reddit_created_at <= :toTs)
            ) t
            GROUP BY t.bucket
            """, nativeQuery = true)
    List<RegionCountRow> countDisasterPostsByRegionBucket(
            @Param("status") String status,
            @Param("modStatus") String modStatus,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs);
}





