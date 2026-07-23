package io.dws.controller.model;

/**
 * Fully-qualified image references for the prebuilt step and orchestrator images.
 * Sourced from {@code dws.images.*} in application config; passed to the pure
 * compiler so it stays free of any framework wiring.
 */
public record ImageCatalog(String callHttp, String callOpenapi, String run, String orchestrator) {}
