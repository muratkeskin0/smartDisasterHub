package com.caglamurat.smartDisasterHub.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Severity levels for error codes.
 * Used for error monitoring, alerting, and logging decisions.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorSeverity {
    /**
     * Low severity - Expected errors, no immediate action required
     * Examples: Validation errors, not found errors
     */
    LOW("Low", 1),
    
    /**
     * Medium severity - Should be monitored but not critical
     * Examples: Authentication failures, duplicate entries
     */
    MEDIUM("Medium", 2),
    
    /**
     * High severity - Requires attention, may affect user experience
     * Examples: Authorization failures, invalid tokens
     */
    HIGH("High", 3),
    
    /**
     * Critical severity - Requires immediate attention
     * Examples: Database errors, service unavailable
     */
    CRITICAL("Critical", 4);
    
    private final String displayName;
    private final int level;
    
    /**
     * Check if this severity is higher than another
     */
    public boolean isHigherThan(ErrorSeverity other) {
        return this.level > other.level;
    }
    
    /**
     * Check if this severity requires immediate alert
     */
    public boolean requiresAlert() {
        return this == HIGH || this == CRITICAL;
    }
}


