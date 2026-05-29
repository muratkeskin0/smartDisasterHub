package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.dto.reddit.RedditApiPost;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for fetching posts from Reddit API.
 * Uses OAuth when configured; falls back to public JSON (often blocked with HTTP 403).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedditApiService implements IRedditApiService {

    private final RestTemplate restTemplate;
    private final RedditOAuthTokenProvider oauthTokenProvider;
    private final com.caglamurat.smartDisasterHub.service.integration.RedditIntegrationSettingsService integrationSettingsService;

    private static final String REDDIT_BASE_URL = "https://www.reddit.com";
    private static final String REDDIT_OAUTH_BASE_URL = "https://oauth.reddit.com";
    private static final int MAX_LIMIT = 100;

    @Override
    public List<RedditApiPost> fetchPosts(String subreddit, int limit) {
        if (subreddit == null || subreddit.trim().isEmpty()) {
            log.warn("Subreddit name is empty, returning empty list");
            return new ArrayList<>();
        }

        int actualLimit = Math.min(Math.max(1, limit), MAX_LIMIT);
        boolean useOAuth = oauthTokenProvider.isConfigured();
        String url = useOAuth
                ? String.format("%s/r/%s/new?limit=%d&raw_json=1", REDDIT_OAUTH_BASE_URL, subreddit, actualLimit)
                : String.format("%s/r/%s/new.json?limit=%d", REDDIT_BASE_URL, subreddit, actualLimit);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, integrationSettingsService.getUserAgent());
            if (useOAuth) {
                headers.setBearerAuth(oauthTokenProvider.getAccessToken());
            } else {
                log.warn(
                        "Reddit OAuth is not configured (app.reddit.client-id/secret/username/password). "
                                + "Public Reddit JSON endpoints are often blocked with HTTP 403."
                );
            }
            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.debug("Fetching posts from Reddit: {}", url);
            ResponseEntity<RedditResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    RedditResponse.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<RedditApiPost> posts = parseRedditResponse(response.getBody());
                log.info("Successfully fetched {} posts from r/{}", posts.size(), subreddit);
                return posts;
            } else {
                log.warn("Reddit API returned non-2xx status: {}", response.getStatusCode());
                return new ArrayList<>();
            }

        } catch (RestClientException e) {
            if (!useOAuth) {
                log.error(
                        "Error fetching posts from Reddit subreddit r/{} (public API blocked?): {}. "
                                + "Configure Reddit OAuth credentials in application-secrets-local.properties.",
                        subreddit,
                        e.getMessage()
                );
            } else {
                log.error("Error fetching posts from Reddit subreddit r/{}: {}", subreddit, e.getMessage(), e);
            }
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Unexpected error fetching posts from Reddit: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<RedditApiPost> fetchPostsFromMultipleSubreddits(List<String> subreddits, int limitPerSubreddit) {
        List<RedditApiPost> allPosts = new ArrayList<>();
        
        for (String subreddit : subreddits) {
            try {
                List<RedditApiPost> posts = fetchPosts(subreddit, limitPerSubreddit);
                allPosts.addAll(posts);
            } catch (Exception e) {
                log.error("Error fetching posts from subreddit {}: {}", subreddit, e.getMessage());
                // Continue with other subreddits even if one fails
            }
        }
        
        return allPosts;
    }

    /**
     * Parse Reddit API JSON response to RedditApiPost objects
     */
    private List<RedditApiPost> parseRedditResponse(RedditResponse response) {
        List<RedditApiPost> posts = new ArrayList<>();

        if (response == null || response.getData() == null || response.getData().getChildren() == null) {
            return posts;
        }

        for (RedditResponse.Child child : response.getData().getChildren()) {
            if (child.getData() != null) {
                RedditApiPost post = convertToRedditApiPost(child.getData());
                if (post != null) {
                    posts.add(post);
                }
            }
        }

        return posts;
    }

    /**
     * Convert Reddit post data to RedditApiPost DTO
     */
    private RedditApiPost convertToRedditApiPost(RedditResponse.PostData data) {
        try {
            // Use permalink to construct full Reddit post URL (this is the actual Reddit post URL)
            String permalink = data.getPermalink() != null ? data.getPermalink() : "";
            String fullRedditUrl = REDDIT_BASE_URL + permalink;
            
            // Store the original URL (could be external link for link posts, or Reddit post URL for self posts)
            String originalUrl = data.getUrl() != null ? data.getUrl() : fullRedditUrl;

            // Extract images (preview + gallery)
            List<String> mediaUrls = extractImageUrls(data);
            String primaryMediaUrl = (mediaUrls != null && !mediaUrls.isEmpty()) ? mediaUrls.get(0) : originalUrl;

            return RedditApiPost.builder()
                    .id(data.getId())
                    .title(data.getTitle())
                    .selftext(data.getSelftext())
                    .url(fullRedditUrl) // Always use Reddit permalink as the URL for consistency
                    .mediaUrl(primaryMediaUrl)
                    .mediaUrls(mediaUrls)
                    .author(data.getAuthor())
                    .authorFullname(data.getAuthorFullname())
                    .subreddit(data.getSubreddit())
                    .ups(data.getUps())
                    .numComments(data.getNumComments())
                    .createdUtc(data.getCreatedUtc())
                    .permalink(permalink)
                    .build();
        } catch (Exception e) {
            log.error("Error converting Reddit post data: {}", e.getMessage(), e);
            return null;
        }
    }

    private List<String> extractImageUrls(RedditResponse.PostData data) {
        List<String> urls = new ArrayList<>();

        // 1) Gallery posts: media_metadata
        if (Boolean.TRUE.equals(data.getIsGallery()) && data.getMediaMetadata() != null) {
            for (var entry : data.getMediaMetadata().entrySet()) {
                RedditResponse.MediaMetadataItem item = entry.getValue();
                if (item == null || item.getS() == null) continue;
                String u = htmlDecode(item.getS().getU());
                if (u != null && !u.isBlank()) urls.add(u);
            }
        }

        // 2) Preview images: preview.images[].source.url
        if (data.getPreview() != null && data.getPreview().getImages() != null) {
            for (RedditResponse.PreviewImage img : data.getPreview().getImages()) {
                if (img == null || img.getSource() == null) continue;
                String u = htmlDecode(img.getSource().getUrl());
                if (u != null && !u.isBlank()) urls.add(u);
            }
        }

        // 3) Fallback: raw url if it looks like an image
        String raw = data.getUrl();
        if (raw != null && looksLikeImageUrl(raw)) {
            urls.add(raw);
        }

        // Deduplicate while preserving order
        return urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private boolean looksLikeImageUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase();
        return (u.startsWith("http://") || u.startsWith("https://"))
                && (u.contains("i.redd.it") || u.contains("preview.redd.it")
                || u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".webp") || u.endsWith(".gif"));
    }

    private String htmlDecode(String s) {
        if (s == null) return null;
        // Reddit sometimes HTML-encodes '&' as '&amp;' inside URLs
        return s.replace("&amp;", "&");
    }

    /**
     * Inner classes for parsing Reddit JSON response
     */
    @Data
    static class RedditResponse {
        private ResponseData data;

        @Data
        static class ResponseData {
            private List<Child> children;
        }

        @Data
        static class Child {
            private String kind;
            private PostData data;
        }

        @Data
        static class PostData {
            private String id;
            private String title;
            @JsonProperty("selftext")
            private String selftext;
            private String url;
            private String author;
            /** Reddit stable id, e.g. {@code t2_wnf9qoikfg} */
            @JsonProperty("author_fullname")
            private String authorFullname;
            private String subreddit;
            private Integer ups;
            @JsonProperty("num_comments")
            private Integer numComments;
            @JsonProperty("created_utc")
            private Long createdUtc;
            private String permalink;

            // media-related fields
            @JsonProperty("is_gallery")
            private Boolean isGallery;

            private Preview preview;

            @JsonProperty("media_metadata")
            private Map<String, MediaMetadataItem> mediaMetadata;
        }

        @Data
        static class Preview {
            private List<PreviewImage> images;
        }

        @Data
        static class PreviewImage {
            private PreviewImageSource source;
        }

        @Data
        static class PreviewImageSource {
            @JsonProperty("url")
            private String url;
        }

        @Data
        static class MediaMetadataItem {
            @JsonProperty("s")
            private MediaMetadataSource s;
        }

        @Data
        static class MediaMetadataSource {
            @JsonProperty("u")
            private String u;
        }
    }
}





