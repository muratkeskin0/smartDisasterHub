package com.caglamurat.smartDisasterHub.service.location;

import lombok.Builder;
import lombok.Value;

/**
 * Forward or reverse Nominatim result with structured address fields.
 */
@Value
@Builder
public class GeocodeResult {
    double latitude;
    double longitude;
    /** Display country name (e.g. Türkiye) */
    String country;
    /** ISO country code lower-case (e.g. tr) */
    String countryCode;
    /**
     * City / town / district level for UI (Nominatim: city, town, village, or county).
     */
    String city;
    /**
     * Admin level typically matching Turkish il in OSM ({@code address.state}).
     */
    String provinceState;
}
