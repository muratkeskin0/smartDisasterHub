package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.dto.report.HistoricalReportSummaryDTO;
import com.caglamurat.smartDisasterHub.dto.report.HistoricalTrendPointDTO;
import com.caglamurat.smartDisasterHub.dto.report.NamedCountDto;
import com.caglamurat.smartDisasterHub.dto.report.ReportBreakdownDto;
import com.caglamurat.smartDisasterHub.enums.PostModerationStatus;
import com.caglamurat.smartDisasterHub.enums.RedditPostStatus;
import com.caglamurat.smartDisasterHub.mapper.reddit.RedditPostMapper;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistoricalReportsService {

    private static final PostModerationStatus PUBLIC_MOD = PostModerationStatus.APPROVED;
    private static final String PUBLIC_MOD_NAME = PostModerationStatus.APPROVED.name();

    private final IRedditPostRepository redditPostRepository;
    private final RedditPostMapper redditPostMapper;

    @Transactional(readOnly = true)
    public HistoricalReportSummaryDTO getSummary(Instant reportedFrom, Instant reportedTo) {
        RedditPostStatus analyzed = RedditPostStatus.ANALYZED;
        if (!useReportedRange(reportedFrom, reportedTo)) {
            return HistoricalReportSummaryDTO.builder()
                    .analyzedWithHistoricalScores(redditPostRepository.countAnalyzedWithHistoricalScores(analyzed, PUBLIC_MOD))
                    .averageBaseScore(nz(redditPostRepository.averageBaseScore(analyzed, PUBLIC_MOD)))
                    .averageFinalScore(nz(redditPostRepository.averageFinalScore(analyzed, PUBLIC_MOD)))
                    .averageAdjustmentDelta(nz(redditPostRepository.averageAdjustmentDelta(analyzed, PUBLIC_MOD)))
                    .penalizedCount(redditPostRepository.countPenalized(analyzed, PUBLIC_MOD))
                    .boostedCount(redditPostRepository.countBoosted(analyzed, PUBLIC_MOD))
                    .imageMismatchCount(redditPostRepository.countByAdjustmentReasonContains(analyzed, PUBLIC_MOD, "image_mismatch"))
                    .noImagePenaltyCount(redditPostRepository.countByAdjustmentReasonContains(analyzed, PUBLIC_MOD, "no_image"))
                    .lowTrustPenaltyCount(
                            redditPostRepository.countByAdjustmentReasonContains(analyzed, PUBLIC_MOD, "low_trust")
                                    + redditPostRepository.countByAdjustmentReasonContains(analyzed, PUBLIC_MOD, "very_low_trust")
                    )
                    .build();
        }
        Instant lo = rangeLo(reportedFrom, reportedTo);
        Instant hi = rangeHi(reportedFrom, reportedTo);
        return HistoricalReportSummaryDTO.builder()
                .analyzedWithHistoricalScores(redditPostRepository.countAnalyzedWithHistoricalScoresBetween(analyzed, PUBLIC_MOD, lo, hi))
                .averageBaseScore(nz(redditPostRepository.averageBaseScoreBetween(analyzed, PUBLIC_MOD, lo, hi)))
                .averageFinalScore(nz(redditPostRepository.averageFinalScoreBetween(analyzed, PUBLIC_MOD, lo, hi)))
                .averageAdjustmentDelta(nz(redditPostRepository.averageAdjustmentDeltaBetween(analyzed, PUBLIC_MOD, lo, hi)))
                .penalizedCount(redditPostRepository.countPenalizedBetween(analyzed, PUBLIC_MOD, lo, hi))
                .boostedCount(redditPostRepository.countBoostedBetween(analyzed, PUBLIC_MOD, lo, hi))
                .imageMismatchCount(redditPostRepository.countByAdjustmentReasonContainsBetween(analyzed, PUBLIC_MOD, "image_mismatch", lo, hi))
                .noImagePenaltyCount(redditPostRepository.countByAdjustmentReasonContainsBetween(analyzed, PUBLIC_MOD, "no_image", lo, hi))
                .lowTrustPenaltyCount(
                        redditPostRepository.countByAdjustmentReasonContainsBetween(analyzed, PUBLIC_MOD, "low_trust", lo, hi)
                                + redditPostRepository.countByAdjustmentReasonContainsBetween(analyzed, PUBLIC_MOD, "very_low_trust", lo, hi)
                )
                .build();
    }

    @Transactional(readOnly = true)
    public List<HistoricalTrendPointDTO> getDailyTrend(int days) {
        int safeDays = Math.max(1, Math.min(90, days));
        Instant to = Instant.now();
        Instant from = to.minus(safeDays, ChronoUnit.DAYS);
        return getDailyTrend(from, to);
    }

    @Transactional(readOnly = true)
    public List<HistoricalTrendPointDTO> getDailyTrend(Instant reportedFrom, Instant reportedTo) {
        Instant lo = rangeLo(reportedFrom, reportedTo);
        Instant hi = rangeHi(reportedFrom, reportedTo);
        return redditPostRepository.findDailyHistoricalTrend(RedditPostStatus.ANALYZED.name(), PUBLIC_MOD_NAME, lo, hi)
                .stream()
                .map(row -> HistoricalTrendPointDTO.builder()
                        .day(row.getDay())
                        .postCount(row.getPostCount() == null ? 0L : row.getPostCount())
                        .avgBaseScore(nz(row.getAvgBaseScore()))
                        .avgFinalScore(nz(row.getAvgFinalScore()))
                        .avgDelta(nz(row.getAvgDelta()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RedditPostDTO> getTopAdjustedPosts(int page, int size) {
        return getTopAdjustedPosts(page, size, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<RedditPostDTO> getTopAdjustedPosts(int page, int size, Instant reportedFrom, Instant reportedTo) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Page<com.caglamurat.smartDisasterHub.domain.RedditPost> result;
        if (!useReportedRange(reportedFrom, reportedTo)) {
            result = redditPostRepository.findTopAdjustedPosts(
                    RedditPostStatus.ANALYZED,
                    PUBLIC_MOD,
                    PageRequest.of(safePage, safeSize)
            );
        } else {
            Instant lo = rangeLo(reportedFrom, reportedTo);
            Instant hi = rangeHi(reportedFrom, reportedTo);
            result = redditPostRepository.findTopAdjustedPostsBetween(
                    RedditPostStatus.ANALYZED,
                    PUBLIC_MOD,
                    lo,
                    hi,
                    PageRequest.of(safePage, safeSize)
            );
        }
        return PageResponse.of(
                redditPostMapper.toDTOList(result.getContent()),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
    }

    /**
     * Pie-chart data: humanitarian label counts (from comma-separated field), posts per Reddit calendar day,
     * and coarse Turkey buckets from coordinates (same optional {@code from}/{@code to} on {@code redditCreatedAt}).
     */
    @Transactional(readOnly = true)
    public ReportBreakdownDto getBreakdown(Instant reportedFrom, Instant reportedTo) {
        RedditPostStatus st = RedditPostStatus.ANALYZED;
        Instant from = null;
        Instant to = null;
        if (useReportedRange(reportedFrom, reportedTo)) {
            from = rangeLo(reportedFrom, reportedTo);
            to = rangeHi(reportedFrom, reportedTo);
        }
        List<NamedCountDto> disasterTypes = mergeHumanitarianCombos(
                redditPostRepository.groupDisasterHumanitarianCombos(st, PUBLIC_MOD, from, to)
        );
        List<NamedCountDto> byDay = buildDaySlices(
                redditPostRepository.countDisasterPostsByRedditDay(st.name(), PUBLIC_MOD_NAME, from, to)
        );
        List<NamedCountDto> byRegion = redditPostRepository.countDisasterPostsByRegionBucket(
                st.name(), PUBLIC_MOD_NAME, from, to).stream()
                .map(row -> new NamedCountDto(
                        row.getRegionKey(),
                        row.getPostCount() == null ? 0L : row.getPostCount()
                ))
                .sorted(Comparator.comparingLong(NamedCountDto::getCount).reversed())
                .collect(Collectors.toList());
        return ReportBreakdownDto.builder()
                .disasterTypes(disasterTypes)
                .postsByRedditDay(byDay)
                .postsByRegion(byRegion)
                .build();
    }

    private static List<NamedCountDto> mergeHumanitarianCombos(List<Object[]> rows) {
        Map<String, Long> acc = new HashMap<>();
        for (Object[] row : rows) {
            String combo = (String) row[0];
            long c = row[1] == null ? 0L : ((Number) row[1]).longValue();
            if (combo == null || combo.isBlank()) {
                acc.merge("uncategorized", c, Long::sum);
                continue;
            }
            for (String part : combo.split(",")) {
                String k = part.strip().toLowerCase(Locale.ROOT);
                if (k.isEmpty()) {
                    continue;
                }
                acc.merge(k, c, Long::sum);
            }
        }
        return acc.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new NamedCountDto(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static List<NamedCountDto> buildDaySlices(List<IRedditPostRepository.RedditDayCountRow> rows) {
        List<NamedCountDto> mapped = new ArrayList<>();
        for (IRedditPostRepository.RedditDayCountRow r : rows) {
            if (r.getDay() == null) {
                continue;
            }
            String key = r.getDay().toLocalDate().toString();
            long c = r.getPostCount() == null ? 0L : r.getPostCount();
            mapped.add(new NamedCountDto(key, c));
        }
        // Keep chronological order so frontend can render daily bars + cumulative meaningfully.
        mapped.sort(Comparator.comparing(NamedCountDto::getKey));
        return mapped;
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

    private static double nz(Double value) {
        return value == null ? 0.0 : value;
    }
}
