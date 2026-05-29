package com.caglamurat.smartDisasterHub.mapper.reddit;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import com.caglamurat.smartDisasterHub.dto.reddit.RedditPostDTO;
import com.caglamurat.smartDisasterHub.repository.IRedditPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RedditPostMapper {

    private final IRedditPostRepository redditPostRepository;

    public RedditPostDTO toDTO(RedditPost post) {
        if (post == null) {
            return null;
        }

        List<String> mediaUrls = null;
        if (post.getMediaUrls() != null && !post.getMediaUrls().isBlank()) {
            mediaUrls = Arrays.stream(post.getMediaUrls().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        String duplicateOfRedditPostId = null;
        Long duplicateOfId = post.getDuplicateOfPostId();
        if (duplicateOfId != null && (post.getId() == null || !duplicateOfId.equals(post.getId()))) {
            duplicateOfRedditPostId = redditPostRepository.findById(duplicateOfId)
                    .map(RedditPost::getRedditPostId)
                    .orElse(null);
        }

        return RedditPostDTO.builder()
                .id(post.getId())
                .redditPostId(post.getRedditPostId())
                .title(post.getTitle())
                .content(post.getContent())
                .url(post.getUrl())
                .mediaUrl(post.getMediaUrl())
                .mediaUrls(mediaUrls)
                .mediaContentHash(post.getMediaContentHash())
                .duplicateOfPostId(post.getDuplicateOfPostId())
                .duplicateOfRedditPostId(duplicateOfRedditPostId)
                .redditAuthorId(post.getRedditAuthorId())
                .redditAuthorFullname(post.getRedditAuthorFullname())
                .author(post.getAuthor())
                .subreddit(post.getSubreddit())
                .upvotes(post.getUpvotes())
                .commentCount(post.getCommentCount())
                .redditCreatedAt(post.getRedditCreatedAt())
                .fetchedAt(post.getFetchedAt())
                .isDisasterRelated(post.getIsDisasterRelated())
                .relevanceScore(post.getRelevanceScore())
                .baseRelevanceScore(post.getBaseRelevanceScore())
                .finalRelevanceScore(post.getFinalRelevanceScore())
                .relevanceAdjustmentDelta(post.getRelevanceAdjustmentDelta())
                .relevanceAdjustmentReasons(post.getRelevanceAdjustmentReasons())
                .appliedAuthorTrustScore(post.getAppliedAuthorTrustScore())
                .analysisMessage(post.getAnalysisMessage())
                .isHelpRequest(post.getIsHelpRequest())
                .helpRequestProbability(post.getHelpRequestProbability())
                .humanitarianCategories(post.getHumanitarianCategories())
                .isImageTextMatch(post.getIsImageTextMatch())
                .imageTextMatchScore(post.getImageTextMatchScore())
                .imageCaption(post.getImageCaption())
                .hasImageDamage(post.getHasImageDamage())
                .imageDamageSeverity(post.getImageDamageSeverity())
                .imageDamageScore(post.getImageDamageScore())
                .imageAnalyzedAt(post.getImageAnalyzedAt())
                .analyzedAt(post.getAnalyzedAt())
                .status(post.getStatus())
                .moderationStatus(post.getModerationStatus())
                .moderationReviewedAt(post.getModerationReviewedAt())
                .moderationReviewedBy(post.getModerationReviewedBy())
                .moderationNotes(post.getModerationNotes())
                .assignedModeratorId(post.getAssignedModerator() != null ? post.getAssignedModerator().getId() : null)
                .assignedModeratorEmail(post.getAssignedModerator() != null ? post.getAssignedModerator().getEmail() : null)
                .assignedModeratorName(post.getAssignedModerator() != null
                        ? post.getAssignedModerator().getFirstName() + " " + post.getAssignedModerator().getLastName()
                        : null)
                .assignedAt(post.getAssignedAt())
                .locationText(post.getLocationText())
                .latitude(post.getLatitude())
                .longitude(post.getLongitude())
                .locationCountry(post.getLocationCountry())
                .locationCity(post.getLocationCity())
                .locationRegionKey(post.getLocationRegionKey())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    public List<RedditPostDTO> toDTOList(List<RedditPost> posts) {
        if (posts == null) {
            return null;
        }

        return posts.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}





