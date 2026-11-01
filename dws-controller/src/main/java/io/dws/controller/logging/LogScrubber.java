package io.dws.controller.logging;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Defence-in-depth redaction for JSON-mode log text ({@code docs/log-format.md}, "Scrubber"): the
 * two contract expressions, applied in order, case-insensitively. It over-redacts on purpose and
 * cannot recognise an unlabelled secret; the guarantee for anything it does not name is the
 * call-site rule that no log call is ever given a secret.
 */
@UtilityClass
public class LogScrubber {

  static final String PLACEHOLDER = "[REDACTED]";

  /** Credential-named key/value: {@code key=value}, {@code key: value}, {@code "key":"value"}. */
  private static final Pattern CREDENTIAL_KEY_VALUE =
      Pattern.compile(
          "([\\w.-]*(?:authorization|password|passwd|secret|token|api[-_]?key)[\\w.-]*)"
              + "(\"?\\s*[:=]\\s*\"?)(?:(?:bearer|basic)\\s+)?[^\\s\"',;&}]+",
          Pattern.CASE_INSENSITIVE);

  /** A bare {@code Bearer}/{@code Basic} credential with no key in front of it. */
  private static final Pattern BARE_CREDENTIAL =
      Pattern.compile("\\b(bearer|basic)\\s+[A-Za-z0-9._~+/-]{8,}=*", Pattern.CASE_INSENSITIVE);

  public static String scrub(String text) {
    String keyed = CREDENTIAL_KEY_VALUE.matcher(text).replaceAll("$1$2" + PLACEHOLDER);
    return BARE_CREDENTIAL.matcher(keyed).replaceAll("$1 " + PLACEHOLDER);
  }
}
