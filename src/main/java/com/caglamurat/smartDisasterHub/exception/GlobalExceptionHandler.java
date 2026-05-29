package com.caglamurat.smartDisasterHub.exception;

import com.caglamurat.smartDisasterHub.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, WebRequest request) {

        ErrorCode errorCode = ex.getErrorCode();
        String userMessage = ExceptionMessageResolver.resolve(ex);

        if (errorCode.isCritical()) {
            log.error("CRITICAL Business exception: {} - {}", errorCode.getCode(), userMessage, ex);
        } else if (errorCode.shouldLog()) {
            log.warn("Business exception: {} - {}", errorCode.getCode(), userMessage);
        } else {
            log.debug("Business exception: {} - {}", errorCode.getCode(), userMessage);
        }

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(errorCode.getCode())
                .details(userMessage)
                .build();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        ErrorCode errorCode = ex.getErrorCode();
        String userMessage = ExceptionMessageResolver.resolve(ex);
        log.debug("Resource not found: {} - {}", errorCode.getCode(), userMessage);

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(errorCode.getCode())
                .details(userMessage)
                .build();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {

        log.debug("Validation error: {}", ex.getMessage());

        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        String userMessage = ExceptionMessageResolver.firstValidationMessage(
                validationErrors,
                "Please check the highlighted fields and try again."
        );

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .details(userMessage)
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
            ConstraintViolationException ex) {

        log.debug("Constraint violation: {}", ex.getMessage());

        Map<String, String> validationErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String errorMessage = violation.getMessage();
            validationErrors.put(fieldName, errorMessage);
        });

        String userMessage = ExceptionMessageResolver.firstValidationMessage(
                validationErrors,
                "Please check the highlighted fields and try again."
        );

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.VALIDATION_ERROR.getCode())
                .details(userMessage)
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex) {

        log.debug("Malformed request body: {}", ex.getMessage());

        String userMessage = "The request could not be read. Please check your input and try again.";
        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .details(userMessage)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        log.debug("Illegal argument: {}", ex.getMessage());

        String userMessage = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : ErrorCode.BAD_REQUEST.getMessage();

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.BAD_REQUEST.getCode())
                .details(userMessage)
                .build();

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        String userMessage = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : ErrorCode.SERVICE_UNAVAILABLE.getMessage();

        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.SERVICE_UNAVAILABLE.getCode())
                .details(userMessage)
                .build();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(userMessage, errorDetails));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(
            Exception ex, WebRequest request) {

        log.error("Unexpected error occurred", ex);

        String userMessage = "Something went wrong on our side. Please try again in a moment.";
        ErrorDetails errorDetails = ErrorDetails.builder()
                .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .details(userMessage)
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(userMessage, errorDetails));
    }
}
