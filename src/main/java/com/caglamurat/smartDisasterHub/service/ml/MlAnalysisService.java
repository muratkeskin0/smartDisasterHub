package com.caglamurat.smartDisasterHub.service.ml;

import com.caglamurat.smartDisasterHub.dto.analysis.TextAnalysisResponse;
import com.caglamurat.smartDisasterHub.exception.BusinessException;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ML Analysis Service Implementation
 * Communicates with ML service for disaster relevance classification
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MlAnalysisService implements IMlAnalysisService {
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${app.ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;
    
    @Value("${app.ml.service.enabled:true}")
    private boolean mlServiceEnabled;

    @Value("${app.ml.external.enabled:false}")
    private boolean externalMlEnabled;

    /**
     * External T1+T2 API URL (must return same JSON as /analyze endpoint in ml-service).
     * Example: https://external-ml.example.com/analyze
     */
    @Value("${app.ml.external.url:}")
    private String externalMlUrl;

    /**
     * External API key (e.g. the gsk_... key you shared).
     * Configure in application.properties or as an environment variable; don't hardcode in code.
     */
    @Value("${app.ml.external.api-key:}")
    private String externalMlApiKey;

    /**
     * OpenAI API key (reused as fallback for externalMlApiKey).
     */
    @Value("${app.openai.api-key:}")
    private String openAiApiKey;

    /**
     * External chat model name for Groq/OpenAI-compatible API.
     * Örnek: llama-3.1-8b-instant, gpt-4o-mini, vb.
     */
    @Value("${app.ml.external.model:llama-3.1-8b-instant}")
    private String externalMlModel;

    /** Broad disaster vocabulary that can appear literally or figuratively. */
    private static final Set<String> DISASTER_TERMS = new HashSet<>(Arrays.asList(
            "deprem", "sel", "yangın", "yangin", "fırtına", "firtina", "kasırga", "kasirga",
            "tayfun", "tsunami", "heyelan", "çığ", "cig", "patlama", "afet"
    ));

    /** Generic figurative / idiomatic cues across Turkish social/news language. */
    private static final Set<String> FIGURATIVE_CUES = new HashSet<>(Arrays.asList(
            "etkisi yarattı", "etkisi yaratti", "etkisi oldu", "gibi", "sanki", "resmen", "adeta", "adeta bir",
            "mecazi", "metafor", "benzetme", "şok etkisi", "sarsıntı yarattı", "sarsinti yaratti",
            "fırtına estirdi", "firtina estirdi", "siyaset depremi", "piyasa depremi", "transfer depremi",
            "gözyaşları sel", "gozyaslari sel", "gözyaşı sel", "gozyasi sel", "göz yaşları sel"
    ));

    /** Context domains that strongly indicate non-literal usage. */
    private static final Set<String> NON_LITERAL_DOMAINS = new HashSet<>(Arrays.asList(
            "piyasa", "borsa", "hisse", "kripto", "finans",
            "siyaset", "seçim", "secim", "meclis", "parti",
            "spor", "futbol", "basketbol", "transfer", "maç", "mac",
            "magazin", "dizi", "film", "şarkı", "sarki", "albüm", "album",
            "oyun", "teknoloji", "tanıtım", "tanitim", "kampanya", "indirim"
    ));

    /** Literal disaster cues that should preserve disaster interpretation. */
    private static final Set<String> LITERAL_DISASTER_CUES = new HashSet<>(Arrays.asList(
            "artçı", "artci", "enkaz", "hasar", "yaralı", "yarali", "can kaybı", "can kaybi",
            "acil yardım", "acil yardim", "arama kurtarma", "afad", "itfaiye", "valilik",
            "tahliye", "depremzede", "büyüklüğünde", "buyuklugunde", "richter",
            "km derinlik", "merkez üssü", "merkez ussu", "ilçe", "ilce", "mahalle", "sokak"
    ));
    
    @Override
    public TextAnalysisResponse analyzeText(String text) {
        if (!mlServiceEnabled) {
            log.error("ML service is disabled");
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "ML service is disabled. Please enable it in configuration."
            );
        }
        
        try {
            // 1) Try external ML API first (T1+T2). If it fails, fall back to internal Python model.
            if (externalMlEnabled && externalMlUrl != null && !externalMlUrl.isBlank()) {
                TextAnalysisResponse external = callExternalMlService(text);
                if (external != null) {
                    log.info("[ML SERVICE] ✅ External ML service used (T1+T2)");
                    return external;
                }
                log.warn("[ML SERVICE] ⚠ External ML service failed or returned empty body, falling back to internal ML");
            }

            // 2) Fallback: use internal Python ML service (/analyze endpoint: T1+T2)
            String url = mlServiceUrl + "/analyze";
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            // Always prefer RoBERTa + multi-task model when available
            requestBody.put("use_roberta", Boolean.TRUE);
            requestBody.put("include_t2", Boolean.TRUE);
            
            // Log request details
            String textPreview = text.length() > 100 ? text.substring(0, 100) + "..." : text;
            log.info("[ML SERVICE] Calling ML service for text analysis");
            log.info("[ML SERVICE] URL: {}", url);
            log.info("[ML SERVICE] Text preview: {}", textPreview);
            log.info("[ML SERVICE] Text length: {} characters", text.length());
            
            long startTime = System.currentTimeMillis();
            ResponseEntity<TextAnalysisResponse> response =
                    restTemplate.postForEntity(url, requestBody, TextAnalysisResponse.class);
            long duration = System.currentTimeMillis() - startTime;
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TextAnalysisResponse result = response.getBody();
                log.info("[ML SERVICE] ✅ Successfully received response from ML service");
                log.info("[ML SERVICE] Response time: {} ms", duration);
                log.info("[ML SERVICE] Is Disaster Related: {}", result.isDisasterRelated());
                log.info("[ML SERVICE] Relevance Score: {}%", String.format("%.2f", result.getRelevanceScore() * 100));
                log.info("[ML SERVICE] Message: {}", result.getMessage());
                log.info("[ML SERVICE] Full response: isDisasterRelated={}, relevanceScore={}, message={}", 
                        result.isDisasterRelated(), result.getRelevanceScore(), result.getMessage());
                applyFigurativeLanguageGuard(text, result);
                return result;
            } else {
                log.error("[ML SERVICE] ❌ ML service returned unsuccessful response: {}", response.getStatusCode());
                throw new BusinessException(
                        ErrorCode.EXTERNAL_SERVICE_ERROR,
                        "ML service returned unsuccessful response: " + response.getStatusCode()
                );
            }
            
        } catch (HttpClientErrorException e) {
            log.error("[ML SERVICE] ❌ HTTP client error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "ML service client error: " + e.getStatusCode() + " - " + e.getMessage()
            );
        } catch (HttpServerErrorException e) {
            log.error("[ML SERVICE] ❌ HTTP server error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "ML service server error: " + e.getStatusCode() + " - " + e.getMessage()
            );
        } catch (RestClientException e) {
            log.error("[ML SERVICE] ❌ Connection error: {}", e.getMessage(), e);
            log.error("[ML SERVICE] ❌ Cannot connect to ML service at: {}", mlServiceUrl);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Cannot connect to ML service at " + mlServiceUrl + ". Please ensure the service is running."
            );
        } catch (BusinessException e) {
            // Re-throw business exceptions
            log.error("[ML SERVICE] ❌ Business exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[ML SERVICE] ❌ Unexpected error: {}", e.getMessage(), e);
            throw new BusinessException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    "Unexpected error while calling ML service: " + e.getMessage()
            );
        }
    }

    /**
     * Call external ML API for unified T1+T2 analysis.
     * Returns null if anything goes wrong so that caller can safely fall back to internal model.
     */
    private TextAnalysisResponse callExternalMlService(String text) {
        try {
            String url = externalMlUrl;

            // Groq (OpenAI-compatible) chat completions request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", externalMlModel);

            List<Map<String, Object>> messages = new ArrayList<>();

            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content",
                    "You are an API that performs disaster relevance analysis (T1) and humanitarian analysis (T2) "
                            + "for Reddit posts.\n"
                            + "IMPORTANT CLASSIFICATION RULES:\n"
                            + "- Mark is_disaster_related=true ONLY for literal, real-world disaster context "
                            + "(earthquake/flood/fire/landslide/storm/tsunami etc.).\n"
                            + "- Distinguish explicit REAL INCIDENT REPORT vs figurative/metaphorical language.\n"
                            + "- If disaster words are metaphorical (e.g. deprem/sel/yangin used idiomatically), classify as NOT disaster-related.\n"
                            + "- If there is no concrete real-world impact context (casualties, damage, rescue, emergency, location/event details), keep relevance_score low.\n"
                            + "- Input: a single post text.\n"
                            + "- Output: STRICTLY a single JSON object with the following shape and no extra text:\n"
                            + "{\n"
                            + "  \"is_disaster_related\": boolean,\n"
                            + "  \"relevance_score\": number between 0 and 1,\n"
                            + "  \"is_literal_disaster\": boolean,\n"
                            + "  \"is_mecaz\": boolean,\n"
                            + "  \"literal_confidence\": number between 0 and 1,\n"
                            + "  \"is_real_world_incident_report\": boolean,\n"
                            + "  \"incident_report_confidence\": number between 0 and 1,\n"
                            + "  \"message\": string,\n"
                            + "  \"model_used\": string,\n"
                            + "  \"t2\": {\n"
                            + "    \"is_help_request\": boolean,\n"
                            + "    \"help_request_probability\": number between 0 and 1,\n"
                            + "    \"humanitarian_labels\": array of strings (subset of [\"urgent_needs\",\"infrastructure_damage\",\"donations_volunteering\",\"other\"]),\n"
                            + "    \"category_probabilities\": object mapping each of the four labels to a probability between 0 and 1\n"
                            + "  }\n"
                            + "}\n"
                            + "Do not include any explanations, only JSON.");
            messages.add(systemMsg);

            Map<String, Object> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", "Analyze this Reddit post text:\n\n" + text);
            messages.add(userMsg);

            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.0);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String keyToUse = (externalMlApiKey != null && !externalMlApiKey.isBlank()) ? externalMlApiKey : openAiApiKey;
            if (keyToUse != null && !keyToUse.isBlank()) {
                headers.setBearerAuth(keyToUse);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<Map> response =
                    restTemplate.postForEntity(url, entity, Map.class);
            long duration = System.currentTimeMillis() - startTime;

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                Object choicesObj = body.get("choices");
                if (choicesObj instanceof List<?> choices && !choices.isEmpty()) {
                    Object first = choices.get(0);
                    if (first instanceof Map<?, ?> choice) {
                        Object msgObj = choice.get("message");
                        if (msgObj instanceof Map<?, ?> msg) {
                            Object contentObj = msg.get("content");
                            if (contentObj instanceof String content && !content.isBlank()) {
                                // content should be pure JSON string according to our system prompt
                                Map<String, Object> json = objectMapper.readValue(
                                        content,
                                        new TypeReference<Map<String, Object>>() {}
                                );
                                TextAnalysisResponse result = objectMapper.convertValue(json, TextAnalysisResponse.class);
                                applyLiteralMecazDecision(result);
                                applyFigurativeLanguageGuard(text, result);
                                log.info("[ML SERVICE] External ML response time: {} ms", duration);
                                return result;
                            }
                        }
                    }
                }

                log.warn("[ML SERVICE] External ML response did not contain a valid choices[0].message.content JSON payload");
            }

            log.warn("[ML SERVICE] External ML returned non-2xx: {}", response.getStatusCode());
        } catch (Exception e) {
            log.warn("[ML SERVICE] External ML call failed: {}", e.getMessage());
        }
        return null;
    }

    /**
     * If external model provides literal/mecaz signals, make final decision from those signals.
     * This avoids endless handcrafted phrase lists.
     */
    private void applyLiteralMecazDecision(TextAnalysisResponse result) {
        if (result == null) {
            return;
        }
        Boolean literal = result.getIsLiteralDisaster();
        Boolean mecaz = result.getIsMecaz();
        Double literalConfidence = result.getLiteralConfidence();
        Boolean realIncident = result.getIsRealWorldIncidentReport();
        Double incidentConfidence = result.getIncidentReportConfidence();
        if (literal == null && mecaz == null && literalConfidence == null
                && realIncident == null && incidentConfidence == null) {
            return; // external payload does not support these fields yet
        }

        final double literalConf = literalConfidence == null ? 0.0 : Math.max(0.0, Math.min(1.0, literalConfidence));
        final double incidentConf = incidentConfidence == null ? 0.0 : Math.max(0.0, Math.min(1.0, incidentConfidence));
        boolean isMecaz = Boolean.TRUE.equals(mecaz);
        boolean isLiteral = Boolean.TRUE.equals(literal);
        boolean isRealIncident = Boolean.TRUE.equals(realIncident);

        if (isMecaz) {
            result.setDisasterRelated(false);
            result.setRelevanceScore(Math.min(result.getRelevanceScore(), 0.30d));
            appendMessage(result, "Literal/mecaz guard: classified as figurative usage.");
            return;
        }

        // Require both literal interpretation and real-incident semantics.
        if (!isLiteral || literalConf < 0.55d || !isRealIncident || incidentConf < 0.50d) {
            result.setDisasterRelated(false);
            double combined = Math.min(literalConf, incidentConf);
            result.setRelevanceScore(Math.min(result.getRelevanceScore(), 0.45d * combined));
            appendMessage(result, String.format(Locale.ROOT,
                    "Literal/incident guard: literal=%.2f incident=%.2f.", literalConf, incidentConf));
            return;
        }

        // Literal + real incident + high confidence: keep disaster true and ensure score is not inconsistent.
        result.setDisasterRelated(true);
        result.setRelevanceScore(Math.max(result.getRelevanceScore(), Math.min(literalConf, incidentConf)));
        appendMessage(result, String.format(Locale.ROOT,
                "Literal/incident guard accepted: literal=%.2f incident=%.2f.", literalConf, incidentConf));
    }

    /**
     * Broad figurative-language safeguard for disaster keywords.
     * Applies conservative downscoring when disaster terms appear in likely idiomatic/metaphorical context.
     */
    private void applyFigurativeLanguageGuard(String text, TextAnalysisResponse result) {
        if (text == null || text.isBlank() || result == null) {
            return;
        }
        String t = text.toLowerCase(Locale.ROOT);
        boolean hasDisasterTerm = containsAny(t, DISASTER_TERMS);
        if (!hasDisasterTerm) {
            return;
        }

        boolean hasFigurativeCue = containsAny(t, FIGURATIVE_CUES);
        boolean hasNonLiteralDomain = containsAny(t, NON_LITERAL_DOMAINS);
        boolean hasLiteralDisasterContext = containsAny(t, LITERAL_DISASTER_CUES);
        boolean likelyFigurative = (hasFigurativeCue || hasNonLiteralDomain) && !hasLiteralDisasterContext;

        if (likelyFigurative) {
            result.setDisasterRelated(false);
            result.setRelevanceScore(Math.min(result.getRelevanceScore(), 0.35d));
            appendMessage(result, "Figurative-language guard applied: likely non-literal disaster wording.");
            log.info("[ML SERVICE] Figurative guard lowered external result for text: {}",
                    text.length() > 160 ? text.substring(0, 160) + "..." : text);
        }
    }

    private static boolean containsAny(String text, Set<String> keys) {
        if (text == null || text.isBlank() || keys == null || keys.isEmpty()) {
            return false;
        }
        for (String key : keys) {
            if (key != null && !key.isBlank() && text.contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static void appendMessage(TextAnalysisResponse result, String suffix) {
        if (result == null || suffix == null || suffix.isBlank()) {
            return;
        }
        String msg = result.getMessage() == null ? "" : result.getMessage().trim();
        if (msg.isEmpty()) {
            result.setMessage(suffix);
        } else if (!msg.contains(suffix)) {
            result.setMessage(msg + " | " + suffix);
        }
    }
    
    @Override
    public boolean checkHealth() {
        if (!mlServiceEnabled) {
            return false;
        }
        
        try {
            String url = mlServiceUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("ML service health check failed: {}", e.getMessage());
            return false;
        }
    }
}

