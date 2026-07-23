package io.dws.controller.model;

/** List projection: name, current version, status. */
public record WorkflowSummary(String name, String version, StackStatus status) {}
