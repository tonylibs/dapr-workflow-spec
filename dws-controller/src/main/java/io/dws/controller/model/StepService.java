package io.dws.controller.model;

import java.util.Map;

/**
 * One I/O task rendered as a scale-to-zero Knative Service backed by a prebuilt image.
 * {@code name} is the kebab-cased task name and doubles as the Dapr {@code app-id}.
 */
public record StepService(String name, TaskKind kind, String image, Map<String, String> env) {

    public StepService {
        env = Map.copyOf(env);
    }
}
