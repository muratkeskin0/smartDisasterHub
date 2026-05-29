package com.caglamurat.smartDisasterHub.service.ml;

import com.caglamurat.smartDisasterHub.dto.analysis.ImageTextMatchResult;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.util.*;

/**
 * OpenAI Vision matcher:
 * - downloads an image (via URL) and hashes it (SHA-256 hex) for deduplication
 * - optionally calls OpenAI Responses API to judge text-image semantic consistency
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAiVisionMatchService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.openai.enabled:false}")
    private boolean openAiEnabled;

    @Value("${app.openai.api-key:}")
    private String openAiApiKey;

    @Value("${app.openai.responses-url:https://api.openai.com/v1/responses}")
    private String responsesUrl;

    @Value("${app.openai.vision-model:gpt-4.1-mini}")
    private String visionModel;

    @Value("${app.openai.vision-timeout-ms:20000}")
    private int visionTimeoutMs;

    private boolean looksLikeImageUrl(String url) {
        if (url == null) return false;
        String u = url.toLowerCase(Locale.ROOT);
        boolean isHttp = u.startsWith("http://") || u.startsWith("https://");
        if (!isHttp) return false;
        return u.contains("i.redd.it") || u.contains("preview.redd.it")
                || u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".png") || u.endsWith(".webp") || u.endsWith(".gif");
    }

    /**
     * Compute SHA-256 hash of the image bytes downloaded from URL.
     * Returns null if URL is blank or download fails.
     */
    public String computeImageContentHash(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        if (!looksLikeImageUrl(imageUrl)) {
            return null;
        }
        try {
            byte[] bytes = downloadBytes(imageUrl);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return sha256Hex(bytes);
        } catch (Exception e) {
            log.debug("[VISION] Failed to compute image hash for url={}: {}", imageUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Judge whether the image content matches the post text.
     * Returns null if OpenAI is disabled or anything fails.
     */
    public ImageTextMatchResult analyzeImageTextMatch(String postText, String imageUrl) {
        if (!openAiEnabled) {
            return null;
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        if (!looksLikeImageUrl(imageUrl)) {
            return null;
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "OpenAI API key is missing");
        }

        try {
            Map<String, Object> payload = buildResponsesPayload(postText, imageUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.exchange(responsesUrl, HttpMethod.POST, entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isBlank()) {
                log.warn("[VISION] OpenAI returned non-2xx or empty body: {}", resp.getStatusCode());
                return null;
            }

            String outputText = extractOutputText(resp.getBody());
            if (outputText == null || outputText.isBlank()) {
                log.warn("[VISION] OpenAI response had no output text");
                return null;
            }

            // outputText is expected to be strict JSON per our prompt
            ImageTextMatchResult result = parseMatchResult(outputText);
            if (result != null) {
                result.setRawJson(outputText);
                result.setModelUsed(visionModel);
            }
            return result;

        } catch (RestClientException e) {
            log.warn("[VISION] OpenAI call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[VISION] Unexpected vision analysis error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * T3: estimate visible damage severity from image only.
     * Returns null if OpenAI is disabled or analysis fails.
     */
    public Map<String, Object> analyzeImageDamage(String imageUrl) {
        if (!openAiEnabled) {
            return null;
        }
        if (imageUrl == null || imageUrl.isBlank() || !looksLikeImageUrl(imageUrl)) {
            return null;
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "OpenAI API key is missing");
        }

        try {
            Map<String, Object> payload = buildDamagePayload(imageUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> resp = restTemplate.exchange(responsesUrl, HttpMethod.POST, entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null || resp.getBody().isBlank()) {
                log.warn("[VISION] T3 damage analysis returned non-2xx or empty body: {}", resp.getStatusCode());
                return null;
            }

            String outputText = extractOutputText(resp.getBody());
            if (outputText == null || outputText.isBlank()) {
                log.warn("[VISION] T3 damage analysis response had no output text");
                return null;
            }

            Map<String, Object> result = parseDamageResult(outputText);
            if (result != null) {
                result.put("rawJson", outputText);
                result.put("modelUsed", visionModel);
            }
            return result;
        } catch (RestClientException e) {
            log.warn("[VISION] T3 damage analysis failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("[VISION] Unexpected T3 damage analysis error: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildResponsesPayload(String postText, String imageUrl) {
        String safeText = postText == null ? "" : postText;

        String instruction = """
You are an API that checks whether a Reddit post text is semantically consistent with its image.
Return STRICTLY a single JSON object and nothing else.

Rules:
- If the image is unrelated or contradicts the text, is_match=false.
- If the image is broadly consistent with the text, is_match=true.
- score: number between 0 and 1 (semantic match strength)
- confidence: number between 0 and 1

JSON shape:
{
  "is_match": boolean,
  "score": number,
  "caption": string,
  "reasons": string[],
  "contradictions": string[],
  "confidence": number
}

POST TEXT:
""" + safeText;

        Map<String, Object> inputText = new HashMap<>();
        inputText.put("type", "input_text");
        inputText.put("text", instruction);

        Map<String, Object> inputImage = new HashMap<>();
        inputImage.put("type", "input_image");
        inputImage.put("image_url", imageUrl);

        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", List.of(inputText, inputImage));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", visionModel);
        payload.put("input", List.of(msg));
        payload.put("temperature", 0.0);
        return payload;
    }

    private Map<String, Object> buildDamagePayload(String imageUrl) {
        String instruction = """
You are an API that performs disaster impact / damage assessment from a single image.
Return STRICTLY a single JSON object and nothing else.

Rules:
- has_damage: true if the image clearly shows disaster-related harm or impact. This is NOT limited to cracked buildings.
  Count as damage/impact when you see, for example: widespread flood water submerging streets or vehicles,
  people evacuating through deep water, storm surge, collapsed or heavily damaged structures, fire/smoke damage,
  major debris fields, landslides, or other obvious hazard consequences. Calm water with no hazard context → false.
- damage_severity: one of [none, minor, moderate, severe, unknown] — use "none" only when the scene is clearly
  benign (no disaster cues). Flooding that covers roads or traps vehicles should usually be at least "moderate"
  unless the image is ambiguous.
- damage_score: number between 0 and 1 (higher = clearer / more severe visible impact). Align with has_damage and severity.
- confidence: number between 0 and 1 (how sure you are given image quality and visibility).

JSON shape:
{
  "has_damage": boolean,
  "damage_severity": "none|minor|moderate|severe|unknown",
  "damage_score": number,
  "indicators": string[],
  "confidence": number
}
""";

        Map<String, Object> inputText = new HashMap<>();
        inputText.put("type", "input_text");
        inputText.put("text", instruction);

        Map<String, Object> inputImage = new HashMap<>();
        inputImage.put("type", "input_image");
        inputImage.put("image_url", imageUrl);

        Map<String, Object> msg = new HashMap<>();
        msg.put("role", "user");
        msg.put("content", List.of(inputText, inputImage));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", visionModel);
        payload.put("input", List.of(msg));
        payload.put("temperature", 0.0);
        return payload;
    }

    private ImageTextMatchResult parseMatchResult(String jsonText) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonText);
        if (node == null || !node.isObject()) {
            return null;
        }

        Boolean isMatch = node.has("is_match") && node.get("is_match").isBoolean() ? node.get("is_match").asBoolean() : null;
        Double score = node.has("score") && node.get("score").isNumber() ? node.get("score").asDouble() : null;
        String caption = node.has("caption") ? node.get("caption").asText(null) : null;
        Double confidence = node.has("confidence") && node.get("confidence").isNumber() ? node.get("confidence").asDouble() : null;

        List<String> reasons = readStringArray(node.get("reasons"));
        List<String> contradictions = readStringArray(node.get("contradictions"));

        return ImageTextMatchResult.builder()
                .isMatch(isMatch)
                .score(score)
                .caption(caption)
                .reasons(reasons)
                .contradictions(contradictions)
                .confidence(confidence)
                .build();
    }

    private Map<String, Object> parseDamageResult(String jsonText) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonText);
        if (node == null || !node.isObject()) {
            return null;
        }

        Boolean hasDamage = readBooleanField(node, "has_damage", "hasDamage");
        String severity = readTextField(node, "damage_severity", "damageSeverity", "unknown");
        Double score = readNumberField(node, "damage_score", "damageScore");
        Double confidence = node.has("confidence") && node.get("confidence").isNumber()
                ? node.get("confidence").asDouble() : null;
        List<String> indicators = readStringArray(node.get("indicators"));

        Map<String, Object> out = new HashMap<>();
        out.put("hasDamage", hasDamage);
        out.put("damageSeverity", severity);
        out.put("damageScore", score);
        out.put("indicators", indicators);
        out.put("confidence", confidence);
        return out;
    }

    private static Boolean readBooleanField(JsonNode node, String snakeKey, String camelKey) {
        if (node.has(snakeKey) && node.get(snakeKey).isBoolean()) {
            return node.get(snakeKey).asBoolean();
        }
        if (node.has(camelKey) && node.get(camelKey).isBoolean()) {
            return node.get(camelKey).asBoolean();
        }
        return null;
    }

    private static String readTextField(JsonNode node, String snakeKey, String camelKey, String defaultVal) {
        if (node.has(snakeKey) && node.get(snakeKey).isTextual()) {
            return node.get(snakeKey).asText(defaultVal);
        }
        if (node.has(camelKey) && node.get(camelKey).isTextual()) {
            return node.get(camelKey).asText(defaultVal);
        }
        return defaultVal;
    }

    private static Double readNumberField(JsonNode node, String snakeKey, String camelKey) {
        if (node.has(snakeKey) && node.get(snakeKey).isNumber()) {
            return node.get(snakeKey).asDouble();
        }
        if (node.has(camelKey) && node.get(camelKey).isNumber()) {
            return node.get(camelKey).asDouble();
        }
        return null;
    }

    private List<String> readStringArray(JsonNode arr) {
        if (arr == null || !arr.isArray()) return null;
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) {
            if (n != null && n.isTextual()) out.add(n.asText());
        }
        return out;
    }

    /**
     * Extract model output text from a Responses API JSON response.
     * Tries multiple known shapes to be resilient.
     */
    private String extractOutputText(String responsesJson) {
        try {
            JsonNode root = objectMapper.readTree(responsesJson);
            if (root == null) return null;

            JsonNode direct = root.get("output_text");
            if (direct != null && direct.isTextual()) {
                return direct.asText();
            }

            // Fallback: walk output[].content[].text
            JsonNode output = root.get("output");
            if (output != null && output.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : output) {
                    JsonNode content = item.get("content");
                    if (content != null && content.isArray()) {
                        for (JsonNode part : content) {
                            JsonNode text = part.get("text");
                            if (text != null && text.isTextual()) {
                                if (!sb.isEmpty()) sb.append("\n");
                                sb.append(text.asText());
                            }
                        }
                    }
                }
                String s = sb.toString().trim();
                return s.isEmpty() ? null : s;
            }
        } catch (Exception e) {
            log.debug("[VISION] Failed to parse Responses API JSON: {}", e.getMessage());
        }
        return null;
    }

    private byte[] downloadBytes(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "SmartDisasterHub/1.0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> resp = restTemplate.exchange(url, HttpMethod.GET, entity, byte[].class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                return null;
            }
            MediaType ct = resp.getHeaders().getContentType();
            if (ct != null && !"image".equalsIgnoreCase(ct.getType())) {
                // Not an image - skip hashing (and avoid downloading huge videos/html)
                return null;
            }
            return resp.getBody();
        } catch (Exception e) {
            return null;
        }
    }

    private String sha256Hex(byte[] bytes) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(bytes);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

