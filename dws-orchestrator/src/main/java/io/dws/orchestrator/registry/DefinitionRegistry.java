package io.dws.orchestrator.registry;

import io.dws.orchestrator.model.WorkflowDef;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds all workflow definitions loaded at startup, keyed by name then version.
 * Populated once during bootstrap and treated as immutable thereafter; the interpreter
 * reads pinned versions from it during (possibly replayed) execution.
 */
public class DefinitionRegistry {

  /** name -> (version -> def), versions kept sorted so "latest" is well-defined. */
  private final Map<String, TreeMap<String, WorkflowDef>> byNameVersion = new ConcurrentHashMap<>();

  public void register(WorkflowDef def) {
    String name = def.name();
    String version = def.version();
    if (name == null || version == null) {
      throw new IllegalArgumentException("workflow definition requires document.name and document.version");
    }
    byNameVersion
        .computeIfAbsent(name, k -> new TreeMap<>(Comparator.naturalOrder()))
        .put(version, def);
  }

  /** Exact name+version lookup used during deterministic replay. */
  public Optional<WorkflowDef> find(String name, String version) {
    return Optional.ofNullable(byNameVersion.get(name))
        .map(versions -> versions.get(version));
  }

  /** Highest version for a name; used when starting a fresh instance. */
  public Optional<WorkflowDef> findLatest(String name) {
    return Optional.ofNullable(byNameVersion.get(name))
        .filter(versions -> !versions.isEmpty())
        .map(versions -> versions.lastEntry().getValue());
  }

  public boolean contains(String name) {
    return byNameVersion.containsKey(name);
  }

  public int size() {
    return byNameVersion.values().stream().mapToInt(Map::size).sum();
  }
}
