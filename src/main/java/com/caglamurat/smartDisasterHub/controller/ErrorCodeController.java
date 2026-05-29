package com.caglamurat.smartDisasterHub.controller;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import com.caglamurat.smartDisasterHub.exception.ErrorCategory;
import com.caglamurat.smartDisasterHub.exception.ErrorCode;
import com.caglamurat.smartDisasterHub.exception.ErrorCodeUtils;
import com.caglamurat.smartDisasterHub.exception.ErrorSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Development/Admin endpoint for error code documentation and management.
 * Should be secured or disabled in production.
 */
@RestController
@RequestMapping("/api/dev/error-codes")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class ErrorCodeController {

    /**
     * Get all error codes
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ErrorCode>>> getAllErrorCodes() {
        log.debug("REST request to get all error codes");
        List<ErrorCode> errorCodes = Arrays.asList(ErrorCode.values());
        return ResponseEntity.ok(ApiResponse.success(errorCodes));
    }

    /**
     * Get error codes by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<ApiResponse<List<ErrorCode>>> getErrorCodesByCategory(
            @PathVariable ErrorCategory category) {
        log.debug("REST request to get error codes by category: {}", category);
        List<ErrorCode> errorCodes = ErrorCodeUtils.getErrorsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(errorCodes));
    }

    /**
     * Get error codes by severity
     */
    @GetMapping("/severity/{severity}")
    public ResponseEntity<ApiResponse<List<ErrorCode>>> getErrorCodesBySeverity(
            @PathVariable ErrorSeverity severity) {
        log.debug("REST request to get error codes by severity: {}", severity);
        List<ErrorCode> errorCodes = ErrorCodeUtils.getErrorsBySeverity(severity);
        return ResponseEntity.ok(ApiResponse.success(errorCodes));
    }

    /**
     * Get critical error codes
     */
    @GetMapping("/critical")
    public ResponseEntity<ApiResponse<List<ErrorCode>>> getCriticalErrorCodes() {
        log.debug("REST request to get critical error codes");
        List<ErrorCode> errorCodes = ErrorCodeUtils.getCriticalErrors();
        return ResponseEntity.ok(ApiResponse.success(errorCodes));
    }

    /**
     * Get error code by code
     */
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<ErrorCode>> getErrorCodeByCode(@PathVariable String code) {
        log.debug("REST request to get error code: {}", code);
        ErrorCode errorCode = ErrorCode.fromCode(code);
        return ResponseEntity.ok(ApiResponse.success(errorCode));
    }

    /**
     * Get error code statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ErrorCodeUtils.ErrorStatistics>> getStatistics() {
        log.debug("REST request to get error code statistics");
        ErrorCodeUtils.ErrorStatistics stats = ErrorCodeUtils.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * Get error codes grouped by category
     */
    @GetMapping("/grouped/category")
    public ResponseEntity<ApiResponse<Map<ErrorCategory, List<ErrorCode>>>> getErrorCodesGroupedByCategory() {
        log.debug("REST request to get error codes grouped by category");
        Map<ErrorCategory, List<ErrorCode>> grouped = ErrorCodeUtils.getErrorsByCategory();
        return ResponseEntity.ok(ApiResponse.success(grouped));
    }

    /**
     * Get error codes grouped by severity
     */
    @GetMapping("/grouped/severity")
    public ResponseEntity<ApiResponse<Map<ErrorSeverity, List<ErrorCode>>>> getErrorCodesGroupedBySeverity() {
        log.debug("REST request to get error codes grouped by severity");
        Map<ErrorSeverity, List<ErrorCode>> grouped = ErrorCodeUtils.getErrorsBySeverity();
        return ResponseEntity.ok(ApiResponse.success(grouped));
    }

    /**
     * Generate Markdown documentation
     */
    @GetMapping(value = "/documentation", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getMarkdownDocumentation() {
        log.debug("REST request to generate error code documentation");
        String markdown = ErrorCodeUtils.generateMarkdownDocumentation();
        return ResponseEntity.ok(markdown);
    }

    /**
     * Validate error code
     */
    @GetMapping("/validate/{code}")
    public ResponseEntity<ApiResponse<Boolean>> validateErrorCode(@PathVariable String code) {
        log.debug("REST request to validate error code: {}", code);
        boolean isValid = ErrorCodeUtils.isValidErrorCode(code);
        String message = isValid ? "Error code is valid" : "Error code is invalid";
        return ResponseEntity.ok(ApiResponse.success(message, isValid));
    }
}


