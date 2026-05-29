package com.caglamurat.smartDisasterHub.service.location;

import com.caglamurat.smartDisasterHub.domain.RedditPost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Fills {@link RedditPost#locationText}, coordinates, and structured location fields
 * ({@code locationCountry}, {@code locationCity}, {@code locationRegionKey}) from title/body
 * patterns and optional Nominatim forward/reverse geocoding.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PostLocationEnrichmentService {

    private final NominatimGeocodingService nominatimGeocodingService;
    private final TurkishProvinceRegionResolver turkishProvinceRegionResolver;

    /**
     * @param allowGeocoding if false, only extracts {@code locationText} and numeric coordinates (no Nominatim — avoids blocking Reddit fetch).
     */
    public void enrichFromTitleAndContent(RedditPost post, boolean allowGeocoding) {
        if (post == null) {
            return;
        }
        String title = post.getTitle();
        String content = post.getContent();

        Optional<double[]> labeledCoords = PostLocationExtractor.tryParseLabeledCoordinates(title, content);
        Optional<String> phraseOpt = PostLocationExtractor.extractLocationPhrase(title, content);

        if (labeledCoords.isPresent()) {
            double[] c = labeledCoords.get();
            post.setLatitude(c[0]);
            post.setLongitude(c[1]);
            if (phraseOpt.isPresent()) {
                post.setLocationText(phraseOpt.get());
            } else {
                post.setLocationText(c[0] + ", " + c[1]);
            }
            log.debug("[LOCATION] Labeled coordinates for post {}: {}, {}", post.getRedditPostId(), c[0], c[1]);
            if (allowGeocoding) {
                nominatimGeocodingService.reverseGeocode(c[0], c[1])
                        .ifPresent(r -> applyGeocodeToPost(post, r, true));
            }
            return;
        }

        if (phraseOpt.isEmpty()) {
            return;
        }

        String phrase = phraseOpt.get();
        post.setLocationText(phrase);

        Optional<double[]> fromPhrase = PostLocationExtractor.tryParseLatLngFromPhrase(phrase);
        if (fromPhrase.isPresent()) {
            double[] c = fromPhrase.get();
            post.setLatitude(c[0]);
            post.setLongitude(c[1]);
            log.debug("[LOCATION] Parsed coordinates from phrase for post {}: {}, {}", post.getRedditPostId(), c[0], c[1]);
            if (allowGeocoding) {
                nominatimGeocodingService.reverseGeocode(c[0], c[1])
                        .ifPresent(r -> applyGeocodeToPost(post, r, true));
            }
            return;
        }

        if (allowGeocoding) {
            String normalized = PostLocationExtractor.normalizeForGeocoding(phrase);
            final String geocodeQuery = normalized.isEmpty() ? phrase : normalized;
            nominatimGeocodingService.forwardGeocode(geocodeQuery).ifPresent(r -> {
                applyGeocodeToPost(post, r, false);
                log.debug("[LOCATION] Geocoded query '{}' (from '{}') for post {} -> {}, {}",
                        geocodeQuery, phrase, post.getRedditPostId(), r.getLatitude(), r.getLongitude());
            });
        }
    }

    /**
     * When DB has {@code locationText} but no coordinates, resolve via Nominatim search.
     * When coordinates exist but structured fields are missing, reverse-geocode once to fill them.
     *
     * @return true if any field on {@code post} was updated (caller should persist).
     */
    public boolean fillMissingCoordinatesFromLocationText(RedditPost post) {
        if (post == null) {
            return false;
        }
        boolean hasCoords = post.getLatitude() != null && post.getLongitude() != null;
        boolean missingCountry = post.getLocationCountry() == null || post.getLocationCountry().isBlank();

        if (!hasCoords) {
            if (post.getLocationText() == null || post.getLocationText().isBlank()) {
                return false;
            }
            String normalized = PostLocationExtractor.normalizeForGeocoding(post.getLocationText());
            final String geocodeQuery = normalized.isEmpty() ? post.getLocationText().trim() : normalized;
            Optional<GeocodeResult> opt = nominatimGeocodingService.forwardGeocode(geocodeQuery);
            if (opt.isEmpty()) {
                return false;
            }
            applyGeocodeToPost(post, opt.get(), false);
            log.info("[LOCATION] Backfill forward geocode from locationText for post {} -> {}, {}",
                    post.getRedditPostId(), opt.get().getLatitude(), opt.get().getLongitude());
            return true;
        }

        if (missingCountry) {
            Optional<GeocodeResult> opt = nominatimGeocodingService.reverseGeocode(post.getLatitude(), post.getLongitude());
            if (opt.isEmpty()) {
                return false;
            }
            applyGeocodeToPost(post, opt.get(), true);
            log.info("[LOCATION] Backfill reverse geocode for post {} (coords already set)", post.getRedditPostId());
            return true;
        }

        return false;
    }

    /**
     * @param preserveExistingCoordinates when true, keep post lat/lon and only fill country/city/region (reverse path).
     */
    private void applyGeocodeToPost(RedditPost post, GeocodeResult r, boolean preserveExistingCoordinates) {
        if (!preserveExistingCoordinates) {
            post.setLatitude(r.getLatitude());
            post.setLongitude(r.getLongitude());
        }
        post.setLocationCountry(nullIfBlank(r.getCountry()));
        String city = r.getCity();
        if (city == null || city.isBlank()) {
            city = r.getProvinceState();
        }
        post.setLocationCity(nullIfBlank(city));

        String cc = r.getCountryCode();
        String fromIl = turkishProvinceRegionResolver.regionKeyForProvince(r.getProvinceState(), cc);
        if (fromIl != null) {
            post.setLocationRegionKey(fromIl);
        } else if (cc != null && !cc.isBlank() && !"tr".equalsIgnoreCase(cc)) {
            post.setLocationRegionKey("outside_tr");
        } else if ("tr".equalsIgnoreCase(cc != null ? cc : "")) {
            String st = r.getProvinceState();
            if (st != null && !st.isBlank()) {
                post.setLocationRegionKey("other_tr");
            } else {
                post.setLocationRegionKey(null);
            }
        } else {
            post.setLocationRegionKey(null);
        }
    }

    private static String nullIfBlank(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
