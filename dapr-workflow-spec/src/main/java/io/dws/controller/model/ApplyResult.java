package io.dws.controller.model;

/**
 * Outcome of an apply pass.
 *
 * @param created {@code false} when this exact version was already deployed (idempotent re-POST)
 */
public record ApplyResult(String workflow, String versionId, String version, boolean created) {}
