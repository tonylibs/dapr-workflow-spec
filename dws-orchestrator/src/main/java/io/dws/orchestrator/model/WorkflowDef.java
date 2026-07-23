package io.dws.orchestrator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Parsed workflow definition. Immutable after load; interpreter replays run against
 * a version pinned in the instance input, so a definition is never mutated in place.
 *
 * @param document metadata carrying the workflow {@code name} and {@code version}
 * @param start    name of the entry task
 * @param tasks    task name to definition
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowDef(DocumentMeta document, String start, Map<String, TaskDef> tasks) {

  public String name() {
    return document == null ? null : document.name();
  }

  public String version() {
    return document == null ? null : document.version();
  }

  public TaskDef task(String taskName) {
    return tasks == null ? null : tasks.get(taskName);
  }
}
