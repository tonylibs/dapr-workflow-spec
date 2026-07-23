package io.dws.orchestrator.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dws.orchestrator.model.WorkflowDef;
import io.dws.orchestrator.registry.DefinitionRegistry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads Serverless Workflow JSON definitions from a directory into a
 * {@link DefinitionRegistry}. The directory is typically mounted from a ConfigMap.
 */
public class DefinitionLoader {

  private final ObjectMapper mapper;

  public DefinitionLoader(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  /** Parse a single definition document. */
  public WorkflowDef parse(String json) throws IOException {
    WorkflowDef def = mapper.readValue(json, WorkflowDef.class);
    validate(def);
    return def;
  }

  /**
   * Load every {@code *.json} file directly under {@code dir} into a fresh registry.
   * A missing directory yields an empty registry rather than an error, so the app can
   * boot before its ConfigMap is populated.
   */
  public DefinitionRegistry loadDirectory(Path dir) {
    DefinitionRegistry registry = new DefinitionRegistry();
    if (!Files.isDirectory(dir)) {
      return registry;
    }
    for (Path file : jsonFiles(dir)) {
      try {
        registry.register(parse(Files.readString(file)));
      } catch (IOException e) {
        throw new UncheckedIOException("failed to load workflow definition: " + file, e);
      }
    }
    return registry;
  }

  private List<Path> jsonFiles(Path dir) {
    try (Stream<Path> stream = Files.list(dir)) {
      List<Path> files = new ArrayList<>();
      stream.filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".json"))
          .sorted()
          .forEach(files::add);
      return files;
    } catch (IOException e) {
      throw new UncheckedIOException("failed to list definitions directory: " + dir, e);
    }
  }

  private void validate(WorkflowDef def) {
    if (def.name() == null || def.version() == null) {
      throw new IllegalArgumentException("definition missing document.name or document.version");
    }
    if (def.start() == null || def.start().isBlank()) {
      throw new IllegalArgumentException("definition '" + def.name() + "' missing 'start'");
    }
    if (def.tasks() == null || def.tasks().isEmpty()) {
      throw new IllegalArgumentException("definition '" + def.name() + "' has no tasks");
    }
    if (!def.tasks().containsKey(def.start())) {
      throw new IllegalArgumentException(
          "definition '" + def.name() + "' start task '" + def.start() + "' is not defined");
    }
  }
}
