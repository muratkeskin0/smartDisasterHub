package com.caglamurat.smartDisasterHub.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for ErrorCode operations and analytics.
 * Provides helper methods for error code management and reporting.
 */
@Slf4j
public class ErrorCodeUtils {

    private ErrorCodeUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Get all error codes by category
     */
    public static Map<ErrorCategory, List<ErrorCode>> getErrorsByCategory() {
        Map<ErrorCategory, List<ErrorCode>> errorsByCategory = new EnumMap<>(ErrorCategory.class);
        
        for (ErrorCode errorCode : ErrorCode.values()) {
            errorsByCategory
                .computeIfAbsent(errorCode.getCategory(), k -> new ArrayList<>())
                .add(errorCode);
        }
        
        return errorsByCategory;
    }

    /**
     * Get all error codes by severity
     */
    public static Map<ErrorSeverity, List<ErrorCode>> getErrorsBySeverity() {
        Map<ErrorSeverity, List<ErrorCode>> errorsBySeverity = new EnumMap<>(ErrorSeverity.class);
        
        for (ErrorCode errorCode : ErrorCode.values()) {
            errorsBySeverity
                .computeIfAbsent(errorCode.getSeverity(), k -> new ArrayList<>())
                .add(errorCode);
        }
        
        return errorsBySeverity;
    }

    /**
     * Get all error codes by HTTP status
     */
    public static Map<HttpStatus, List<ErrorCode>> getErrorsByHttpStatus() {
        Map<HttpStatus, List<ErrorCode>> errorsByStatus = new HashMap<>();
        
        for (ErrorCode errorCode : ErrorCode.values()) {
            errorsByStatus
                .computeIfAbsent(errorCode.getHttpStatus(), k -> new ArrayList<>())
                .add(errorCode);
        }
        
        return errorsByStatus;
    }

    /**
     * Get all critical error codes
     */
    public static List<ErrorCode> getCriticalErrors() {
        return Arrays.stream(ErrorCode.values())
                .filter(ErrorCode::isCritical)
                .collect(Collectors.toList());
    }

    /**
     * Get all error codes that should be logged
     */
    public static List<ErrorCode> getLoggableErrors() {
        return Arrays.stream(ErrorCode.values())
                .filter(ErrorCode::shouldLog)
                .collect(Collectors.toList());
    }

    /**
     * Validate if error code exists
     */
    public static boolean isValidErrorCode(String code) {
        return Arrays.stream(ErrorCode.values())
                .anyMatch(ec -> ec.getCode().equals(code));
    }

    /**
     * Get error codes by category
     */
    public static List<ErrorCode> getErrorsByCategory(ErrorCategory category) {
        return Arrays.stream(ErrorCode.values())
                .filter(ec -> ec.getCategory() == category)
                .collect(Collectors.toList());
    }

    /**
     * Get error codes by severity
     */
    public static List<ErrorCode> getErrorsBySeverity(ErrorSeverity severity) {
        return Arrays.stream(ErrorCode.values())
                .filter(ec -> ec.getSeverity() == severity)
                .collect(Collectors.toList());
    }

    /**
     * Print error code summary (for debugging/documentation)
     */
    public static void printErrorCodeSummary() {
        log.info("===== ERROR CODE SUMMARY =====");
        log.info("Total Error Codes: {}", ErrorCode.values().length);
        
        // By Category
        log.info("\n--- By Category ---");
        getErrorsByCategory().forEach((category, errors) -> 
            log.info("{}: {} errors", category, errors.size())
        );
        
        // By Severity
        log.info("\n--- By Severity ---");
        getErrorsBySeverity().forEach((severity, errors) -> 
            log.info("{}: {} errors", severity, errors.size())
        );
        
        // Critical Errors
        log.info("\n--- Critical Errors ---");
        getCriticalErrors().forEach(error -> 
            log.info("{}: {}", error.getCode(), error.getMessage())
        );
        
        log.info("===============================");
    }

    /**
     * Generate error code documentation in Markdown format
     */
    public static String generateMarkdownDocumentation() {
        StringBuilder doc = new StringBuilder();
        doc.append("# Error Code Documentation\n\n");
        doc.append("## Overview\n\n");
        doc.append("Total Error Codes: ").append(ErrorCode.values().length).append("\n\n");
        
        // By Category
        Map<ErrorCategory, List<ErrorCode>> byCategory = getErrorsByCategory();
        doc.append("## Error Codes by Category\n\n");
        
        for (Map.Entry<ErrorCategory, List<ErrorCode>> entry : byCategory.entrySet()) {
            ErrorCategory category = entry.getKey();
            List<ErrorCode> errors = entry.getValue();
            
            doc.append("### ").append(category.name()).append("\n\n");
            doc.append("| Code | Message | HTTP Status | Severity |\n");
            doc.append("|------|---------|-------------|----------|\n");
            
            for (ErrorCode error : errors) {
                doc.append("| `").append(error.getCode()).append("` | ")
                   .append(error.getMessage()).append(" | ")
                   .append(error.getHttpStatus().value()).append(" ")
                   .append(error.getHttpStatus().getReasonPhrase()).append(" | ")
                   .append(error.getSeverity().getDisplayName()).append(" |\n");
            }
            doc.append("\n");
        }
        
        return doc.toString();
    }

    /**
     * Get error statistics
     */
    public static ErrorStatistics getStatistics() {
        int totalErrors = ErrorCode.values().length;
        
        Map<ErrorCategory, Long> categoryCount = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getCategory, Collectors.counting()));
        
        Map<ErrorSeverity, Long> severityCount = Arrays.stream(ErrorCode.values())
                .collect(Collectors.groupingBy(ErrorCode::getSeverity, Collectors.counting()));
        
        long criticalCount = getCriticalErrors().size();
        long loggableCount = getLoggableErrors().size();
        
        return new ErrorStatistics(
                totalErrors,
                categoryCount,
                severityCount,
                criticalCount,
                loggableCount
        );
    }

    /**
     * Statistics data class
     */
    public static class ErrorStatistics {
        public final int totalErrors;
        public final Map<ErrorCategory, Long> errorsByCategory;
        public final Map<ErrorSeverity, Long> errorsBySeverity;
        public final long criticalErrors;
        public final long loggableErrors;

        public ErrorStatistics(
                int totalErrors,
                Map<ErrorCategory, Long> errorsByCategory,
                Map<ErrorSeverity, Long> errorsBySeverity,
                long criticalErrors,
                long loggableErrors) {
            this.totalErrors = totalErrors;
            this.errorsByCategory = errorsByCategory;
            this.errorsBySeverity = errorsBySeverity;
            this.criticalErrors = criticalErrors;
            this.loggableErrors = loggableErrors;
        }

        @Override
        public String toString() {
            return String.format(
                "ErrorStatistics{total=%d, critical=%d, loggable=%d, categories=%s, severities=%s}",
                totalErrors, criticalErrors, loggableErrors, errorsByCategory, errorsBySeverity
            );
        }
    }
}


