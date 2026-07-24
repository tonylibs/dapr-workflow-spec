package io.dws.controller.model;

/**
 * A pub/sub topic binding recorded for an {@code emit} or {@code listen} task.
 * These deploy no service; the orchestrator wires them at runtime.
 */
public record TopicBinding(String task, Direction direction, String topic) {

    public enum Direction {
        EMIT,
        LISTEN
    }
}
