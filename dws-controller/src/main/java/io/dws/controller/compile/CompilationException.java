package io.dws.controller.compile;

import java.util.List;

/** Thrown when a definition fails to parse or validate; carries the human-readable error list. */
public class CompilationException extends RuntimeException {

    private final transient List<String> errors;

    public CompilationException(List<String> errors) {
        super("Workflow definition is invalid: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
