package com.caglamurat.smartDisasterHub.service.media;

import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Fetches external post images through the backend so browsers are not blocked by hotlink rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalMediaProxyService {

    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_HOSTS = Set.of(
            "i.redd.it",
            "preview.redd.it",
            "external-preview.redd.it",
            "i.imgur.com"
    );

    private final RestTemplate restTemplate;

    public ProxiedMedia fetch(String rawUrl) {
        URI uri = validateAndNormalize(rawUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.USER_AGENT, "SmartDisasterHub/1.0");
            headers.set(HttpHeaders.ACCEPT, "image/*,*/*;q=0.8");
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<byte[]> response = restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Image fetch failed");
            }

            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Image body empty");
            }
            if (body.length > MAX_BYTES) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "Image too large");
            }

            MediaType contentType = response.getHeaders().getContentType();
            if (contentType == null || !"image".equalsIgnoreCase(contentType.getType())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "URL is not an image");
            }

            return new ProxiedMedia(body, contentType);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.debug("[MEDIA-PROXY] Failed to fetch {}: {}", uri, e.getMessage());
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Image fetch failed");
        }
    }

    private URI validateAndNormalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Image URL is required");
        }

        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid image URL");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Only HTTPS image URLs are allowed");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid image host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (!ALLOWED_HOSTS.contains(normalizedHost)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Image host is not allowed");
        }

        if (uri.getUserInfo() != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid image URL");
        }

        int port = uri.getPort();
        if (port != -1 && port != 443) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Invalid image URL port");
        }

        return uri;
    }

    public record ProxiedMedia(byte[] bytes, MediaType contentType) {
    }
}
