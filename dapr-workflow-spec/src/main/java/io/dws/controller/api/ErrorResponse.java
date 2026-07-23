package io.dws.controller.api;

import java.util.List;

/** Error envelope returned for rejected definitions and missing workflows. */
public record ErrorResponse(String message, List<String> errors) {

    public ErrorResponse {
        errors = List.copyOf(errors);
    }

    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, List.of());
    }
}
