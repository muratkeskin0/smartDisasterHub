package com.caglamurat.smartDisasterHub.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Centralized error code management for the application.
 * Each error code contains:
 * - Unique code identifier
 * - Human-readable message
 * - HTTP status code
 * - Category for grouping
 * - Severity level
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    
    // ===========================
    // USER ERRORS (USER_XXX)
    // ===========================
    USER_NOT_FOUND(
            "USER_001", 
            "User not found", 
            HttpStatus.NOT_FOUND, 
            ErrorCategory.USER,
            ErrorSeverity.MEDIUM
    ),
    USER_ALREADY_EXISTS(
            "USER_002", 
            "User already exists", 
            HttpStatus.CONFLICT, 
            ErrorCategory.USER,
            ErrorSeverity.LOW
    ),
    EMAIL_ALREADY_EXISTS(
            "USER_003", 
            "Email already exists", 
            HttpStatus.CONFLICT, 
            ErrorCategory.USER,
            ErrorSeverity.LOW
    ),
    INVALID_CREDENTIALS(
            "USER_004", 
            "Invalid credentials", 
            HttpStatus.UNAUTHORIZED, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.MEDIUM
    ),
    AUTHENTICATION_FAILED(
            "USER_004A", 
            "Authentication failed", 
            HttpStatus.UNAUTHORIZED, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.MEDIUM
    ),
    EMAIL_NOT_VERIFIED(
            "USER_005", 
            "Email not verified", 
            HttpStatus.FORBIDDEN, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.MEDIUM
    ),
    EMAIL_VERIFICATION_TOKEN_INVALID(
            "USER_005A",
            "Email verification token is invalid",
            HttpStatus.BAD_REQUEST,
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.LOW
    ),
    EMAIL_VERIFICATION_TOKEN_EXPIRED(
            "USER_005B",
            "Email verification token has expired",
            HttpStatus.BAD_REQUEST,
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.LOW
    ),
    EMAIL_DELIVERY_FAILED(
            "USER_005C",
            "Email delivery failed",
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCategory.SYSTEM,
            ErrorSeverity.HIGH
    ),
    USER_INACTIVE(
            "USER_006", 
            "User account is inactive", 
            HttpStatus.FORBIDDEN, 
            ErrorCategory.USER,
            ErrorSeverity.MEDIUM
    ),
    USER_LOCKED(
            "USER_007", 
            "User account is locked", 
            HttpStatus.FORBIDDEN, 
            ErrorCategory.USER,
            ErrorSeverity.HIGH
    ),
    USER_DELETE_SELF(
            "USER_008",
            "You cannot delete your own account",
            HttpStatus.BAD_REQUEST,
            ErrorCategory.USER,
            ErrorSeverity.MEDIUM
    ),
    USER_DELETE_PROTECTED(
            "USER_009",
            "This account cannot be deleted",
            HttpStatus.BAD_REQUEST,
            ErrorCategory.USER,
            ErrorSeverity.MEDIUM
    ),
    USER_IN_USE(
            "USER_010",
            "User has related records and cannot be deleted",
            HttpStatus.CONFLICT,
            ErrorCategory.USER,
            ErrorSeverity.MEDIUM
    ),
    USER_ALREADY_VERIFIED(
            "USER_011",
            "User email is already verified",
            HttpStatus.BAD_REQUEST,
            ErrorCategory.USER,
            ErrorSeverity.LOW
    ),
    
    // ===========================
    // ROLE ERRORS (ROLE_XXX)
    // ===========================
    ROLE_NOT_FOUND(
            "ROLE_001", 
            "Role not found", 
            HttpStatus.NOT_FOUND, 
            ErrorCategory.ROLE,
            ErrorSeverity.MEDIUM
    ),
    ROLE_ALREADY_EXISTS(
            "ROLE_002", 
            "Role already exists", 
            HttpStatus.CONFLICT, 
            ErrorCategory.ROLE,
            ErrorSeverity.LOW
    ),
    ROLE_IN_USE(
            "ROLE_003", 
            "Role is currently in use and cannot be deleted", 
            HttpStatus.CONFLICT, 
            ErrorCategory.ROLE,
            ErrorSeverity.MEDIUM
    ),
    INSUFFICIENT_PERMISSIONS(
            "ROLE_004", 
            "Insufficient permissions for this operation", 
            HttpStatus.FORBIDDEN, 
            ErrorCategory.AUTHORIZATION,
            ErrorSeverity.HIGH
    ),
    
    // ===========================
    // VALIDATION ERRORS (VAL_XXX)
    // ===========================
    VALIDATION_ERROR(
            "VAL_001", 
            "Validation error", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.VALIDATION,
            ErrorSeverity.LOW
    ),
    INVALID_INPUT(
            "VAL_002", 
            "Invalid input", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.VALIDATION,
            ErrorSeverity.LOW
    ),
    MISSING_REQUIRED_FIELD(
            "VAL_003", 
            "Missing required field", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.VALIDATION,
            ErrorSeverity.LOW
    ),
    INVALID_EMAIL_FORMAT(
            "VAL_004", 
            "Invalid email format", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.VALIDATION,
            ErrorSeverity.LOW
    ),
    PASSWORD_TOO_WEAK(
            "VAL_005", 
            "Password does not meet security requirements", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.VALIDATION,
            ErrorSeverity.MEDIUM
    ),
    INVALID_DATE_RANGE(
            "VAL_006", 
            "Invalid date range", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.VALIDATION,
            ErrorSeverity.LOW
    ),
    
    // ===========================
    // AUTHENTICATION ERRORS (AUTH_XXX)
    // ===========================
    TOKEN_EXPIRED(
            "AUTH_001", 
            "Authentication token has expired", 
            HttpStatus.UNAUTHORIZED, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.MEDIUM
    ),
    TOKEN_INVALID(
            "AUTH_002", 
            "Invalid authentication token", 
            HttpStatus.UNAUTHORIZED, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.HIGH
    ),
    SESSION_EXPIRED(
            "AUTH_003", 
            "Session has expired", 
            HttpStatus.UNAUTHORIZED, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.MEDIUM
    ),
    
    // ===========================
    // SYSTEM ERRORS (SYS_XXX)
    // ===========================
    INTERNAL_SERVER_ERROR(
            "SYS_001", 
            "Internal server error", 
            HttpStatus.INTERNAL_SERVER_ERROR, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.CRITICAL
    ),
    RESOURCE_NOT_FOUND(
            "SYS_002", 
            "Resource not found", 
            HttpStatus.NOT_FOUND, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.LOW
    ),
    BAD_REQUEST(
            "SYS_003", 
            "Bad request", 
            HttpStatus.BAD_REQUEST, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.LOW
    ),
    UNAUTHORIZED(
            "SYS_004", 
            "Unauthorized access", 
            HttpStatus.UNAUTHORIZED, 
            ErrorCategory.AUTHENTICATION,
            ErrorSeverity.HIGH
    ),
    FORBIDDEN(
            "SYS_005", 
            "Access forbidden", 
            HttpStatus.FORBIDDEN, 
            ErrorCategory.AUTHORIZATION,
            ErrorSeverity.HIGH
    ),
    SERVICE_UNAVAILABLE(
            "SYS_006", 
            "Service temporarily unavailable", 
            HttpStatus.SERVICE_UNAVAILABLE, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.CRITICAL
    ),
    DATABASE_ERROR(
            "SYS_007", 
            "Database operation failed", 
            HttpStatus.INTERNAL_SERVER_ERROR, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.CRITICAL
    ),
    EXTERNAL_SERVICE_ERROR(
            "SYS_008", 
            "External service error", 
            HttpStatus.BAD_GATEWAY, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.HIGH
    ),
    RATE_LIMIT_EXCEEDED(
            "SYS_009", 
            "Rate limit exceeded", 
            HttpStatus.TOO_MANY_REQUESTS, 
            ErrorCategory.SYSTEM,
            ErrorSeverity.MEDIUM
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
    private final ErrorCategory category;
    private final ErrorSeverity severity;
    
    /**
     * Get formatted error message with code
     */
    public String getFormattedMessage() {
        return String.format("[%s] %s", code, message);
    }
    
    /**
     * Check if error is critical
     */
    public boolean isCritical() {
        return severity == ErrorSeverity.CRITICAL;
    }
    
    /**
     * Check if error should be logged
     */
    public boolean shouldLog() {
        return severity.ordinal() >= ErrorSeverity.MEDIUM.ordinal();
    }
    
    /**
     * Get error by code
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.getCode().equals(code)) {
                return errorCode;
            }
        }
        return INTERNAL_SERVER_ERROR;
    }
}

