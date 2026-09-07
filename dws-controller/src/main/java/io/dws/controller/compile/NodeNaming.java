package io.dws.controller.compile;

import java.util.List;

/** Derives a compiled node's identifier and its sanitized DNS-1123 Dapr app ID (ADR 0001). */
final class NodeNaming {

  private static final int DNS_1123_LABEL_MAX = 63;

  private NodeNaming() {}

  static String mainNodeId(String workflow) {
    return workflow + ".main";
  }

  static String catchNodeId(String tryTaskName) {
    return tryTaskName + ".catch";
  }

  static String branchNodeId(String forkTaskName, String branchRootTaskName) {
    return forkTaskName + ".branch." + branchRootTaskName;
  }

  static String branchScopeNodeId(String branchNodeId, String kind) {
    return branchNodeId + "." + kind;
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
