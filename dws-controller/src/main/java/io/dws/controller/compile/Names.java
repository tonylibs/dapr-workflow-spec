package io.dws.controller.compile;

import lombok.experimental.UtilityClass;

/** Naming helpers shared by the compile and apply passes. */
@UtilityClass
public class Names {

  /** RFC-1123-ish kebab-case: camelCase boundaries and non-alphanumerics become single dashes. */
  public static String kebab(String input) {
    StringBuilder out = new StringBuilder(input.length() + 8);
    char[] chars = input.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (Character.isUpperCase(c)) {
        appendDash(out);
        out.append(Character.toLowerCase(c));
      } else if (Character.isLetterOrDigit(c)) {
        out.append(c);
      } else {
        appendDash(out);
      }
    }
    int end = out.length();
    while (end > 0 && out.charAt(end - 1) == '-') {
      end--;
    }
    return out.substring(0, end);
  }

  private static void appendDash(StringBuilder out) {
    if (out.length() > 0 && out.charAt(out.length() - 1) != '-') {
      out.append('-');
    }
  }

  public static String definitionResource(String workflow, String versionId) {
    return "dws-def-" + workflow + "-" + versionId;
  }

  public static String orchestrator(String workflow, String versionId) {
    return workflow + "-" + versionId;
  }

  /** Kubernetes name length the generated Dapr Configuration name is held to. */
  static final int MAX_CONFIGURATION_NAME = 63;

  private static final String CONFIGURATION_SUFFIX = "-cfg";
  private static final int HASH_LENGTH = 8;

  /**
   * Name of the one merged Dapr Configuration a workload's version owns: {@code
   * <workload>-<versionId>-cfg}. Deterministic in its inputs, so re-applying a version updates the
   * same object and the label-scoped GC of a drained version finds it. When the readable form would
   * exceed {@link #MAX_CONFIGURATION_NAME}, the workload part is truncated and an 8-hex hash of the
   * full readable form is appended, so two long names that share a prefix still differ.
   */
  public static String daprConfiguration(String workload, String versionId) {
    String readable = workload + "-" + versionId + CONFIGURATION_SUFFIX;
    if (readable.length() <= MAX_CONFIGURATION_NAME) {
      return readable;
    }
    String hash =
        SpecDigest.sha256Hex(readable.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            .substring(0, HASH_LENGTH);
    String tail = "-" + hash + CONFIGURATION_SUFFIX;
    String head = readable.substring(0, MAX_CONFIGURATION_NAME - tail.length());
    int end = head.length();
    while (end > 0 && head.charAt(end - 1) == '-') {
      end--;
    }
    return head.substring(0, end) + tail;
  }

  public static String nodeDefinitionResource(String workflow, String versionId, String appId) {
    return definitionResource(workflow, versionId) + "-" + appId;
  }
}
