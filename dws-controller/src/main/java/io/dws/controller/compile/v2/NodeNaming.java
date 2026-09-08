package io.dws.controller.compile.v2;

import io.dws.controller.compile.CompilationException;
import io.dws.controller.compile.Names;
import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/** Derives a compiled node's identifier and its sanitized DNS-1123 Dapr app ID (ADR 0001). */
@UtilityClass
class NodeNaming {

  private static final int DNS_1123_LABEL_MAX = 63;

  /**
   * The single-node definition schema's own {@code nodeId} pattern. {@link Names#kebab} splits on
   * {@link Character#isLetterOrDigit}, which admits non-ASCII letters, so a task named {@code
   * naiveStep} with a diaeresis sanitizes to an app ID Kubernetes and the schema both reject. v1
   * shares {@code Names.kebab}, so the check belongs here rather than there.
   */
  private static final Pattern DNS_1123_LABEL = Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$");

  /**
   * Rejects a task name containing {@code .} (Finding 1): {@link
   * io.dws.controller.model.CompiledNode#key()} returns a nodeId's last dotted segment, so a dotted
   * task name would collide with the dotted derived ids this class synthesizes for scopes the DSL
   * itself does not name ({@link #catchNodeId}, {@link #branchNodeId}), silently dropping a sibling
   * from the wire {@code children} map.
   */
  static void requireUndottedTaskName(String taskName) {
    if (taskName.indexOf('.') >= 0) {
      throw new CompilationException(
          List.of(
              "task '"
                  + taskName
                  + "' must not contain '.' in its name; a dotted name collides with this "
                  + "compiler's derived node ids"));
    }
  }

  static String mainNodeId(String workflow) {
    return workflow + ".main";
  }

  static String catchNodeId(String tryTaskName) {
    return tryTaskName + ".catch";
  }

  static String branchNodeId(String forkTaskName, String branchRootTaskName) {
    return forkTaskName + ".branch." + branchRootTaskName;
  }

  static String appId(String nodeId) {
    String appId = Names.kebab(nodeId);
    if (appId.isEmpty()) {
      throw new CompilationException(
          List.of(
              "node '"
                  + nodeId
                  + "' produces an empty app ID; DNS-1123 labels must contain at least one "
                  + "alphanumeric character"));
    }
    if (!DNS_1123_LABEL.matcher(appId).matches()) {
      throw new CompilationException(
          List.of(
              "node '"
                  + nodeId
                  + "' derives the app ID '"
                  + appId
                  + "', which is not a DNS-1123 label; a node's name must use only ASCII "
                  + "lowercase letters, digits, and dashes"));
    }
    if (appId.length() > DNS_1123_LABEL_MAX) {
      throw new CompilationException(
          List.of(
              "node '"
                  + nodeId
                  + "' derives the app ID '"
                  + appId
                  + "' ("
                  + appId.length()
                  + " characters), which exceeds the "
                  + DNS_1123_LABEL_MAX
                  + "-character DNS-1123 label limit"));
    }
    return appId;
  }

  static String functionAppId(String appId) {
    String functionAppId = appId + "-fn";
    if (functionAppId.length() > DNS_1123_LABEL_MAX) {
      throw new CompilationException(
          List.of(
              "app ID '"
                  + appId
                  + "' with the function suffix produces '"
                  + functionAppId
                  + "' ("
                  + functionAppId.length()
                  + " characters), which exceeds the "
                  + DNS_1123_LABEL_MAX
                  + "-character DNS-1123 label limit"));
    }
    return functionAppId;
  }
}
