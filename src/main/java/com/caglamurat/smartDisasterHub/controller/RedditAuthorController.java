package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.domain.RedditAuthor;
import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.PageResponse;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditAuthorDTO;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditAuthorInsightsDTO;
import com.caglamurat.smartDisasterHub.repository.IRedditAuthorRepository;
import com.caglamurat.smartDisasterHub.service.reddit.RedditAuthorService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reddit-authors")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class RedditAuthorController {

    private final IRedditAuthorRepository redditAuthorRepository;
    private final RedditAuthorService redditAuthorService;

    /**
     * Paginated list: default sort trustScore DESC. Optional username substring filter.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RedditAuthorDTO>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "trustScore") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {
        int pageSize = Math.min(Math.max(size, 1), 200);
        Sort sort = resolveSort(sortBy, sortDirection);
        Pageable pageable = PageRequest.of(page, pageSize, sort);

        Page<RedditAuthor> result;
        if (search != null && !search.isBlank()) {
            result = redditAuthorRepository.findByRedditUsernameContainingIgnoreCase(search.trim(), pageable);
        } else {
            result = redditAuthorRepository.findAll(pageable);
        }

        List<RedditAuthorDTO> content = result.getContent().stream().map(this::toDto).collect(Collectors.toList());
        PageResponse<RedditAuthorDTO> body = PageResponse.of(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements()
        );
        body.setFirst(result.isFirst());
        body.setLast(result.isLast());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    /**
     * Summary for charts: averages + top slices (small fixed limits).
     */
    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<RedditAuthorInsightsDTO>> insights() {
        long total = redditAuthorRepository.count();
        Double avg = redditAuthorRepository.averageTrustScore();

        Page<RedditAuthor> topTrust = redditAuthorRepository.findAll(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "trustScore")));
        Page<RedditAuthor> topVol = redditAuthorRepository.findAll(
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "totalPosts")));

        RedditAuthorInsightsDTO dto = RedditAuthorInsightsDTO.builder()
                .totalAuthors(total)
                .averageTrust(avg != null ? round3(avg) : null)
                .topByTrust(topTrust.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .topByPostVolume(topVol.getContent().stream().map(this::toDto).collect(Collectors.toList()))
                .build();
        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static Sort resolveSort(String sortBy, String sortDirection) {
        String field = switch (sortBy == null ? "" : sortBy.toLowerCase(Locale.ROOT)) {
            case "username", "redditusername" -> "redditUsername";
            case "totalposts", "posts" -> "totalPosts";
            case "analyzedposts", "analyzed" -> "analyzedPosts";
            case "disasterposts", "disaster" -> "disasterRelatedPosts";
            case "failed", "failedposts" -> "failedAnalysisPosts";
            case "lastpost", "lastpostat" -> "lastPostAt";
            case "firstseen", "firstseenat" -> "firstSeenAt";
            default -> "trustScore";
        };
        Sort.Direction dir = "ASC".equalsIgnoreCase(sortDirection) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
    }

    /**
     * Recompute all rows from {@code reddit_posts} (repair / migration).
     */
    @PostMapping("/rebuild")
    public ResponseEntity<ApiResponse<Integer>> rebuild() {
        int n = redditAuthorService.rebuildAllFromPosts();
        return ResponseEntity.ok(ApiResponse.success("Rebuilt author stats", n));
    }

    private RedditAuthorDTO toDto(RedditAuthor a) {
        return RedditAuthorDTO.builder()
                .id(a.getId())
                .redditUsername(a.getRedditUsername())
                .redditUserId(a.getRedditUserId())
                .totalPosts(a.getTotalPosts())
                .analyzedPosts(a.getAnalyzedPosts())
                .disasterRelatedPosts(a.getDisasterRelatedPosts())
                .failedAnalysisPosts(a.getFailedAnalysisPosts())
                .trustScore(a.getTrustScore())
                .firstSeenAt(a.getFirstSeenAt())
                .lastPostAt(a.getLastPostAt())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
