package com.caglamurat.smartDisasterHub.exception;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Business Exception Handler
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, WebRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        
        // Log based on severity
        if (errorCode.isCritical()) {
            log.error("CRITICAL Business exception: {} - {}", errorCode.getCode(), ex.getMessage(), ex);
        } else if (errorCode.shouldLog()) {
            log.warn("Business exception: {} - {}", errorCode.getCode(), ex.getMessage());
        } else {
            log.debug("Business exception: {} - {}", errorCode.getCode(), ex.getMessage());
        }
        
        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(errorCode.getCode())
                .details(ex.getDetails())
                .build();

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                errorDetails
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    // Resource Not Found Exception Handler
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        
        ErrorCode errorCode = ex.getErrorCode();
        log.debug("Resource not found: {} - {}", errorCode.getCode(), ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(errorCode.getCode())
                .details(ex.getDetails())
                .build();

        ApiResponse<Void> response = ApiResponse.error(
                ex.getMessage(),
                errorDetails
        );

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(response);
    }

    // Validation Exception Handler (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        
        log.error("Validation error: {}", ex.getMessage());
        
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .details("Validation failed for one or more fields")
                .validationErrors(validationErrors)
                .build();

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.getMessage(),
                errorDetails
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // Constraint Violation Exception Handler
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex) {
        
        log.error("Constraint violation: {}", ex.getMessage());
        
        Map<String, String> validationErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .details("Constraint violation")
                .validationErrors(validationErrors)
                .build();

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.VALIDATION_ERROR.getMessage(),
                errorDetails
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    // Generic Exception Handler
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {
        
        log.error("Unexpected error occurred", ex);
        
        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .details("An unexpected error occurred. Please try again later.")
                .build();

        ApiResponse<Void> response = ApiResponse.error(
                ErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
                errorDetails
        );

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    // IllegalArgumentException Handler (for backward compatibility)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        log.error("Illegal argument: {}", ex.getMessage());
        
        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .details(ex.getMessage())
                .build();

        ApiResponse<Void> response = ApiResponse.error(
                "Bad Request",
                errorDetails
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }
}

