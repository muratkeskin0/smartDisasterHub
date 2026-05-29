package com.caglamurat.smartDisasterHub.exception;

public class ResourceNotFoundException extends BusinessException {
    
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String details) {
        super(errorCode, details);
    }

    // Convenience constructors
    public static ResourceNotFoundException userNotFound(Long id) {
        return new ResourceNotFoundException(
            ErrorCode.USER_NOT_FOUND, 
            "User not found with ID: " + id
        );
    }

    public static ResourceNotFoundException userNotFoundByEmail(String email) {
        return new ResourceNotFoundException(
            ErrorCode.USER_NOT_FOUND, 
            "User not found with email: " + email
        );
    }

    public static ResourceNotFoundException roleNotFound(Long id) {
        return new ResourceNotFoundException(
            ErrorCode.ROLE_NOT_FOUND, 
            "Role not found with ID: " + id
        );
    }

    public static ResourceNotFoundException roleNotFoundByName(String name) {
        return new ResourceNotFoundException(
            ErrorCode.ROLE_NOT_FOUND, 
            "Role not found with name: " + name
        );
    }
}


