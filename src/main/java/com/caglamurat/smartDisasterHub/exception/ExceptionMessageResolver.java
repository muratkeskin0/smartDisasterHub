package com.caglamurat.smartDisasterHub.exception;

import java.util.Map;

public final class ExceptionMessageResolver {

    private ExceptionMessageResolver() {
    }

    public static String resolve(BusinessException ex) {
        if (ex.getDetails() != null && !ex.getDetails().isBlank()) {
            return ex.getDetails();
        }
        return ex.getErrorCode().getMessage();
    }

    public static String firstValidationMessage(Map<String, String> validationErrors, String fallback) {
        if (validationErrors == null || validationErrors.isEmpty()) {
            return fallback;
        }
        return validationErrors.values().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(fallback);
    }
}
