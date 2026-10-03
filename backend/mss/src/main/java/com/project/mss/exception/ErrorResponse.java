package com.project.mss.exception;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Single error format of the API, used by the frontend alerts.
 * fields: per-field validation messages (only for validation errors).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String message, Map<String, String> fields) {
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, null);
    }
}
