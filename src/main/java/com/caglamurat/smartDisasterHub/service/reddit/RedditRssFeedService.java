package com.caglamurat.smartDisasterHub.service.reddit;

import com.caglamurat.smartDisasterHub.dto.reddit.RedditApiPost;
import com.caglamurat.smartDisasterHub.service.integration.RedditIntegrationSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches subreddit posts from Reddit's public Atom RSS feed.
 * Used as a fallback when OAuth/JSON API requests fail (e.g. HTTP 403).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedditRssFeedService {

    private static final String REDDIT_BASE_URL = "https://www.reddit.com";
    private static final String ATOM_NS = "http://www.w3.org/2005/Atom";
    private static final String MRSS_NS = "http://search.yahoo.com/mrss/";
    private static final Pattern MARKDOWN_BODY = Pattern.compile(
            "<div class=\"md\">(.*?)</div>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern IMG_SRC = Pattern.compile(
            "<img[^>]+src=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern HREF_URL = Pattern.compile(
            "href=[\"'](https?://[^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private final RestTemplate restTemplate;
    private final RedditIntegrationSettingsService integrationSettingsService;

    public List<RedditApiPost> fetchPosts(String subreddit, int limit) {
        String normalizedSubreddit = subreddit == null ? "" : subreddit.trim();
        if (normalizedSubreddit.isEmpty()) {
            return List.of();
        }

        int actualLimit = Math.min(Math.max(1, limit), 100);
        String url = String.format("%s/r/%s/new.rss?limit=%d", REDDIT_BASE_URL, normalizedSubreddit, actualLimit);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, integrationSettingsService.getUserAgent());
            headers.set(HttpHeaders.ACCEPT, "application/atom+xml, application/xml, text/xml, */*");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null || response.getBody().isBlank()) {
                log.warn("Reddit RSS feed returned non-2xx or empty body for r/{}: {}", normalizedSubreddit, response.getStatusCode());
                return List.of();
            }

            List<RedditApiPost> posts = parseFeed(response.getBody(), normalizedSubreddit, actualLimit);
            log.info("RSS fallback fetched {} posts from r/{}", posts.size(), normalizedSubreddit);
            return posts;
        } catch (RestClientException e) {
            log.error("Reddit RSS fallback failed for r/{}: {}", normalizedSubreddit, e.getMessage());
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error parsing Reddit RSS for r/{}: {}", normalizedSubreddit, e.getMessage(), e);
            return List.of();
        }
    }

    private List<RedditApiPost> parseFeed(String xml, String subreddit, int limit) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);

        Document document = factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        NodeList entries = document.getElementsByTagNameNS(ATOM_NS, "entry");
        List<RedditApiPost> posts = new ArrayList<>();

        for (int i = 0; i < entries.getLength() && posts.size() < limit; i++) {
            Element entry = (Element) entries.item(i);
            RedditApiPost post = convertEntry(entry, subreddit);
            if (post != null) {
                posts.add(post);
            }
        }

        return posts;
    }

    private RedditApiPost convertEntry(Element entry, String subreddit) {
        try {
            String rawId = firstText(entry, "id");
            String postId = normalizePostId(rawId);
            if (postId == null || postId.isBlank()) {
                return null;
            }

            String title = firstText(entry, "title");
            String contentHtml = firstText(entry, "content");
            String selftext = extractSelftext(contentHtml);
            String link = firstLinkHref(entry);
            if (link == null || link.isBlank()) {
                return null;
            }

            URI uri = URI.create(link);
            String permalink = uri.getRawPath();
            String author = normalizeAuthor(firstNestedText(entry, "author", "name"));
            Long createdUtc = parseInstant(firstText(entry, "published"));
            if (createdUtc == null) {
                createdUtc = parseInstant(firstText(entry, "updated"));
            }

            List<String> mediaUrls = extractImageUrls(entry, contentHtml);
            String primaryMediaUrl = mediaUrls.isEmpty() ? link : mediaUrls.get(0);

            return RedditApiPost.builder()
                    .id(postId)
                    .title(title)
                    .selftext(selftext)
                    .url(link)
                    .mediaUrl(primaryMediaUrl)
                    .mediaUrls(mediaUrls)
                    .author(author)
                    .authorFullname(null)
                    .subreddit(subreddit)
                    .ups(0)
                    .numComments(0)
                    .createdUtc(createdUtc)
                    .permalink(permalink)
                    .build();
        } catch (Exception e) {
            log.debug("Skipping malformed RSS entry: {}", e.getMessage());
            return null;
        }
    }

    private String firstText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(ATOM_NS, localName);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private String firstNestedText(Element parent, String containerName, String childName) {
        NodeList containers = parent.getElementsByTagNameNS(ATOM_NS, containerName);
        if (containers.getLength() == 0) {
            return null;
        }
        Element container = (Element) containers.item(0);
        NodeList children = container.getElementsByTagNameNS(ATOM_NS, childName);
        if (children.getLength() == 0) {
            return null;
        }
        String text = children.item(0).getTextContent();
        return text == null ? null : text.trim();
    }

    private String firstLinkHref(Element entry) {
        NodeList links = entry.getElementsByTagNameNS(ATOM_NS, "link");
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            if (href != null && !href.isBlank() && href.contains("/comments/")) {
                return href;
            }
        }
        for (int i = 0; i < links.getLength(); i++) {
            Element link = (Element) links.item(i);
            String href = link.getAttribute("href");
            if (href != null && !href.isBlank()) {
                return href;
            }
        }
        return null;
    }

    private String normalizePostId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        int slash = rawId.lastIndexOf('_');
        if (slash >= 0 && slash + 1 < rawId.length()) {
            return rawId.substring(slash + 1);
        }
        return rawId.startsWith("t3_") ? rawId.substring(3) : rawId;
    }

    private String normalizeAuthor(String rawAuthor) {
        if (rawAuthor == null || rawAuthor.isBlank()) {
            return null;
        }
        return rawAuthor.startsWith("/u/") ? rawAuthor.substring(3) : rawAuthor;
    }

    private Long parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value).getEpochSecond();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractSelftext(String contentHtml) {
        if (contentHtml == null || contentHtml.isBlank()) {
            return "";
        }

        Matcher markdown = MARKDOWN_BODY.matcher(contentHtml);
        if (markdown.find()) {
            return decodeHtml(stripTags(markdown.group(1))).trim();
        }

        String stripped = decodeHtml(stripTags(contentHtml)).trim();
        if (isRssBoilerplate(stripped)) {
            return "";
        }
        return stripped;
    }

    /** RSS image/link posts often have no body — only "submitted by … [link] [comments]". */
    private boolean isRssBoilerplate(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.matches(".*submitted by.*/u/.*\\[link\\].*\\[comments\\].*")
                || (normalized.contains("[link]") && normalized.contains("[comments]") && normalized.length() < 120);
    }

    private List<String> extractImageUrls(Element entry, String contentHtml) {
        List<String> urls = new ArrayList<>();

        NodeList thumbnails = entry.getElementsByTagNameNS(MRSS_NS, "thumbnail");
        for (int i = 0; i < thumbnails.getLength(); i++) {
            Element thumbnail = (Element) thumbnails.item(i);
            addImageUrl(urls, thumbnail.getAttribute("url"));
        }

        NodeList mediaContents = entry.getElementsByTagNameNS(MRSS_NS, "content");
        for (int i = 0; i < mediaContents.getLength(); i++) {
            Element mediaContent = (Element) mediaContents.item(i);
            addImageUrl(urls, mediaContent.getAttribute("url"));
        }

        if (contentHtml != null && !contentHtml.isBlank()) {
            Matcher imgMatcher = IMG_SRC.matcher(contentHtml);
            while (imgMatcher.find()) {
                addImageUrl(urls, imgMatcher.group(1));
            }

            Matcher hrefMatcher = HREF_URL.matcher(contentHtml);
            while (hrefMatcher.find()) {
                addImageUrl(urls, hrefMatcher.group(1));
            }
        }

        urls.sort(this::compareImageUrlPriority);
        return urls;
    }

    private int compareImageUrlPriority(String a, String b) {
        int scoreA = imageUrlPriority(a);
        int scoreB = imageUrlPriority(b);
        return Integer.compare(scoreB, scoreA);
    }

    private int imageUrlPriority(String url) {
        if (url == null) {
            return 0;
        }
        String lower = url.toLowerCase();
        if (lower.contains("i.redd.it")) {
            return 3;
        }
        if (lower.contains("preview.redd.it") || lower.contains("external-preview.redd.it")) {
            return 2;
        }
        if (lower.contains("i.imgur.com")) {
            return 2;
        }
        return 1;
    }

    private void addImageUrl(List<String> urls, String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        String decoded = decodeHtml(rawUrl.trim());
        if (!looksLikeImageUrl(decoded)) {
            return;
        }
        if (!urls.contains(decoded)) {
            urls.add(decoded);
        }
    }

    private boolean looksLikeImageUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        return lower.contains("i.redd.it")
                || lower.contains("preview.redd.it")
                || lower.contains("external-preview.redd.it")
                || lower.contains("i.imgur.com")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif");
    }

    private String stripTags(String html) {
        return HTML_TAGS.matcher(html).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private String decodeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#32;", " ")
                .replace("&nbsp;", " ");
    }
}
