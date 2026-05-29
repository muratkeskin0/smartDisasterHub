package com.caglamurat.smartDisasterHub.exception;

/**
 * Categories for grouping error codes.
 * Used for error analytics and reporting.
 */
public enum ErrorCategory {
    /**
     * User-related errors (profile, account management)
     */
    USER,
    
    /**
     * Role and permission errors
     */
    ROLE,
    
    /**
     * Authentication errors (login, token, session)
     */
    AUTHENTICATION,
    
    /**
     * Authorization errors (permissions, access control)
     */
    AUTHORIZATION,
    
    /**
     * Input validation errors
     */
    VALIDATION,
    
    /**
     * System and infrastructure errors
     */
    SYSTEM,
    
    /**
     * Business logic errors
     */
    BUSINESS,
    
    /**
     * External service integration errors
     */
    EXTERNAL_SERVICE
}


