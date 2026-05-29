package com.caglamurat.smartDisasterHub.service.location;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts a location phrase from post title/body after common multilingual labels
 * (e.g. Location:, Location=, Konum:, Ubicación:, Standort:, 位置:).
 * Slashes in the value (istanbul/maltepe) are normalized for geocoding.
 */
public final class PostLocationExtractor {

    /** After label: colon, fullwidth colon, or equals (Reddit-style "Location= ...") */
    private static final String LABEL_VALUE_SEP = "[:：=]";

    private PostLocationExtractor() {
    }

    /**
     * Keywords after which we capture the rest of the line (before newline).
     * Covers EN, TR, DE, FR, ES, IT, PT, NL, RU, ID, MS, ZH, JA variants.
     */
    private static final String LABEL = String.join("|",
            "location",
            "konum",
            "konum\\s+bilgisi",
            "yer",
            "adres",
            "coordinates?",
            "co[oö]rdinates?",
            "coordenadas",
            "koordinat",
            "standort",
            "lage",
            "ort",
            "lieu",
            "emplacement",
            "localisation",
            "position",
            "ubicaci[oó]n",
            "localizaci[oó]n",
            "posici[oó]n",
            "posizione",
            "ubicazione",
            "localit[aà]",
            "indirizzo",
            "lokasyon",
            "lokasi",
            "alamat",
            "tempat",
            "位置",
            "地点",
            "地點",
            "место",
            "местоположение",
            "localiza[cç][aã]o",
            "posi[cç][aã]o",
            "plaats"
    );

    private static final Pattern LINE_PATTERN = Pattern.compile(
            "(?is)(?:^|[^\\p{L}\\p{N}_])(?:" + LABEL + ")\\s*" + LABEL_VALUE_SEP + "\\s*([^\\r\\n]+)"
    );

    /** Explicit decimal degrees on the same line as the label */
    private static final Pattern LABELED_COORD_PATTERN = Pattern.compile(
            "(?is)(?:^|[^\\p{L}\\p{N}_])(?:" + LABEL + ")\\s*" + LABEL_VALUE_SEP
                    + "\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*[,;]\\s*(-?\\d{1,3}(?:\\.\\d+)?)"
    );

    /** Phrase that is only "lat, lon" */
    private static final Pattern PHRASE_COORD_ONLY = Pattern.compile(
            "^\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*[,;]\\s*(-?\\d{1,3}(?:\\.\\d+)?)\\s*$"
    );

    public static Optional<String> extractLocationPhrase(String title, String content) {
        String full = combine(title, content);
        if (full.isBlank()) {
            return Optional.empty();
        }
        Matcher lineM = LINE_PATTERN.matcher(full);
        if (lineM.find()) {
            String raw = lineM.group(1).trim();
            if (!raw.isEmpty()) {
                return Optional.of(raw);
            }
        }
        return Optional.empty();
    }

    /**
     * Turns values like {@code istanbul/maltepe} into {@code istanbul, maltepe} for Nominatim.
     * Original phrase is still stored in DB; use this only for the search query.
     */
    public static String normalizeForGeocoding(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim();
        s = s.replace("/", ", ");
        s = s.replaceAll("\\s*,\\s*", ", ");
        s = s.replaceAll("\\s+", " ");
        return s.trim();
    }

    public static Optional<double[]> tryParseLatLngFromPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }
        Matcher m = PHRASE_COORD_ONLY.matcher(phrase.trim());
        if (m.matches()) {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            if (isValidLatLng(lat, lon)) {
                return Optional.of(new double[]{lat, lon});
            }
        }
        return Optional.empty();
    }

    /**
     * If the full text contains a labeled coordinate line, return [lat, lng].
     */
    public static Optional<double[]> tryParseLabeledCoordinates(String title, String content) {
        String full = combine(title, content);
        Matcher m = LABELED_COORD_PATTERN.matcher(full);
        if (m.find()) {
            double lat = Double.parseDouble(m.group(1));
            double lon = Double.parseDouble(m.group(2));
            if (isValidLatLng(lat, lon)) {
                return Optional.of(new double[]{lat, lon});
            }
        }
        return Optional.empty();
    }

    private static boolean isValidLatLng(double lat, double lon) {
        return lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
    }

    private static String combine(String title, String content) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append(title.trim());
        }
        if (content != null && !content.isBlank()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(content.trim());
        }
        return sb.toString();
    }
}
