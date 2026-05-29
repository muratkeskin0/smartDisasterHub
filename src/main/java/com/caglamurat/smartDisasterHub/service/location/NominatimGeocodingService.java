package com.caglamurat.smartDisasterHub.service.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Forward and reverse geocoding via OpenStreetMap Nominatim (usage policy: max ~1 req/s, valid User-Agent).
 */
@Service
@Slf4j
public class NominatimGeocodingService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.geocoding.enabled:true}")
    private boolean geocodingEnabled;

    @Value("${app.geocoding.nominatim-base-url:https://nominatim.openstreetmap.org/search}")
    private String nominatimSearchUrl;

    @Value("${app.geocoding.nominatim-reverse-url:https://nominatim.openstreetmap.org/reverse}")
    private String nominatimReverseUrl;

    @Value("${app.geocoding.user-agent:SmartDisasterHub/1.0 (graduation project)}")
    private String userAgent;

    private final Object rateLock = new Object();
    private long lastRequestAtMs = 0L;

    public NominatimGeocodingService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @return [lat, lon] in WGS84 if found (legacy callers).
     */
    public Optional<double[]> geocode(String query) {
        return forwardGeocode(query).map(r -> new double[]{r.getLatitude(), r.getLongitude()});
    }

    public Optional<GeocodeResult> forwardGeocode(String query) {
        if (!geocodingEnabled || query == null) {
            return Optional.empty();
        }
        String q = query.trim();
        if (q.length() > 500) {
            q = q.substring(0, 500);
        }
        if (q.isEmpty()) {
            return Optional.empty();
        }

        synchronized (rateLock) {
            if (!throttle()) {
                return Optional.empty();
            }
            try {
                URI uri = UriComponentsBuilder
                        .fromHttpUrl(nominatimSearchUrl)
                        .queryParam("q", q)
                        .queryParam("format", "json")
                        .queryParam("addressdetails", 1)
                        .queryParam("limit", 1)
                        .encode(StandardCharsets.UTF_8)
                        .build()
                        .toUri();

                Optional<GeocodeResult> out = executeGet(uri);
                lastRequestAtMs = System.currentTimeMillis();
                return out;
            } catch (Exception e) {
                log.warn("[GEOCODING] Nominatim search failed for query '{}': {}", q, e.getMessage());
                lastRequestAtMs = System.currentTimeMillis();
                return Optional.empty();
            }
        }
    }

    public Optional<GeocodeResult> reverseGeocode(double latitude, double longitude) {
        if (!geocodingEnabled) {
            return Optional.empty();
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            return Optional.empty();
        }

        synchronized (rateLock) {
            if (!throttle()) {
                return Optional.empty();
            }
            try {
                URI uri = UriComponentsBuilder
                        .fromHttpUrl(nominatimReverseUrl)
                        .queryParam("lat", latitude)
                        .queryParam("lon", longitude)
                        .queryParam("format", "json")
                        .queryParam("addressdetails", 1)
                        .encode(StandardCharsets.UTF_8)
                        .build()
                        .toUri();

                Optional<GeocodeResult> out = executeGet(uri);
                lastRequestAtMs = System.currentTimeMillis();
                return out;
            } catch (Exception e) {
                log.warn("[GEOCODING] Nominatim reverse failed for {}, {}: {}", latitude, longitude, e.getMessage());
                lastRequestAtMs = System.currentTimeMillis();
                return Optional.empty();
            }
        }
    }

    /** @return false if interrupted while waiting (caller must not hit the API). */
    private boolean throttle() {
        long now = System.currentTimeMillis();
        long wait = 1100L - (now - lastRequestAtMs);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    private Optional<GeocodeResult> executeGet(URI uri) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, String.class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return Optional.empty();
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        JsonNode first;
        if (root.isArray()) {
            if (root.isEmpty()) {
                return Optional.empty();
            }
            first = root.get(0);
        } else {
            first = root;
        }
        return parsePlaceNode(first);
    }

    private Optional<GeocodeResult> parsePlaceNode(JsonNode first) {
        if (first == null || first.isMissingNode()) {
            return Optional.empty();
        }
        String latStr = first.path("lat").asText(null);
        String lonStr = first.path("lon").asText(null);
        if (latStr == null || lonStr == null) {
            return Optional.empty();
        }
        double lat = Double.parseDouble(latStr);
        double lon = Double.parseDouble(lonStr);

        JsonNode addr = first.path("address");
        String country = textOrNull(addr, "country");
        String countryCode = textOrNull(addr, "country_code");
        if (countryCode != null) {
            countryCode = countryCode.toLowerCase();
        }
        String state = textOrNull(addr, "state");
        String city = firstNonBlank(
                textOrNull(addr, "city"),
                textOrNull(addr, "town"),
                textOrNull(addr, "village"),
                textOrNull(addr, "municipality"),
                textOrNull(addr, "county"),
                textOrNull(addr, "city_district")
        );

        return Optional.of(GeocodeResult.builder()
                .latitude(lat)
                .longitude(lon)
                .country(country)
                .countryCode(countryCode)
                .city(city)
                .provinceState(state)
                .build());
    }

    private static String textOrNull(JsonNode parent, String field) {
        if (parent == null || parent.isMissingNode()) {
            return null;
        }
        String t = parent.path(field).asText(null);
        if (t == null || t.isBlank()) {
            return null;
        }
        return t.trim();
    }

    @SafeVarargs
    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
