package io.dws.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Serverless Workflow {@code document} header. Only the fields the orchestrator
 * keys on ({@code name}, {@code version}) are modelled; the rest is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentMeta(String dsl, String namespace, String name, String version) {
}
