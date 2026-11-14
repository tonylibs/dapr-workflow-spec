package io.dws.controller.compile;

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import com.fasterxml.jackson.databind.JsonNode;
import io.dws.controller.model.TriggerKind;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A definition's validated, normalized time trigger (scheduling Phase 6a): exactly one of {@code
 * every}, {@code cron} or {@code after}, with its value in canonical form (an ISO-8601 duration
 * string or a single-space-separated 5-field cron).
 *
 * <p>Reads the <em>raw</em> definition tree, not the SDK model: the SDK's {@code Schedule} has no
 * {@code read} field and rejects it with a generic "Unrecognized field" parse error, and the
 * operator-facing rules here need to name the offending {@code schedule} field. Reading and
 * validating are one call, so no unvalidated {@code TimeSchedule} can exist.
 *
 * @param kind which time trigger the definition set
 * @param schedule the canonical schedule value stamped into {@code trigger.json}
 */
record TimeSchedule(TriggerKind kind, String schedule) {

  static final Duration MINIMUM_INTERVAL = Duration.ofSeconds(1);

  private static final int CRON_FIELDS = 5;
  private static final Pattern RUNTIME_EXPRESSION = Pattern.compile("^\\s*\\$\\{.+}\\s*$");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final CronParser UNIX_CRON =
      new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));

  /** Duration-object fields (DSL 1.0 {@code duration} inline form), smallest last. */
  private static final Map<String, Function<Long, Duration>> DURATION_FIELDS =
      Map.of(
          "days", Duration::ofDays,
          "hours", Duration::ofHours,
          "minutes", Duration::ofMinutes,
          "seconds", Duration::ofSeconds,
          "milliseconds", Duration::ofMillis);

  private static final Set<String> TIME_FIELDS = Set.of("every", "cron", "after");

  /**
   * The definition's time trigger, or empty when it has no {@code schedule}.
   *
   * @param root the definition's raw tree
   * @throws CompilationException listing every rule the {@code schedule} block breaks
   */
  static Optional<TimeSchedule> read(JsonNode root) {
    JsonNode schedule = root.path("schedule");
    if (schedule.isMissingNode() || schedule.isNull()) {
      return Optional.empty();
    }
    if (!schedule.isObject()) {
      throw new CompilationException("'schedule' must be an object");
    }

    List<String> errors = new ArrayList<>();
    boolean hasOn = schedule.has("on");
    if (hasOn) {
      errors.add("'schedule.on' (event-triggered scheduling) is not yet supported");
    } else if (schedule.has("read")) {
      errors.add("'schedule.read' is valid only with 'schedule.on'");
    }

    List<String> present = new ArrayList<>();
    for (Iterator<String> names = schedule.fieldNames(); names.hasNext(); ) {
      String name = names.next();
      if (TIME_FIELDS.contains(name)) {
        present.add(name);
      }
    }

    TimeSchedule result = null;
    if (present.size() > 1) {
      errors.add(
          "'schedule' allows exactly one of 'every', 'cron' or 'after'; found '"
              + String.join("', '", present)
              + "'");
    } else if (present.isEmpty() && !hasOn) {
      errors.add("'schedule': one of 'every', 'cron', 'after' or 'on' is required");
    } else if (present.size() == 1) {
      String field = present.get(0);
      JsonNode value = schedule.get(field);
      result =
          switch (field) {
            case "every" -> duration(TriggerKind.EVERY, value, errors);
            case "after" -> duration(TriggerKind.AFTER, value, errors);
            default -> cron(value, errors);
          };
    }

    if (!errors.isEmpty()) {
      throw new CompilationException(errors);
    }
    return Optional.ofNullable(result);
  }

  private static TimeSchedule duration(TriggerKind kind, JsonNode value, List<String> errors) {
    String field = "schedule." + kind.wireName();
    Duration duration;
    if (value.isTextual()) {
      duration = isoDuration(field, value.asText(), errors);
    } else if (value.isObject()) {
      duration = durationObject(field, value, errors);
    } else {
      errors.add("'" + field + "' must be an ISO-8601 duration string or a duration object");
      return null;
    }
    if (duration == null) {
      return null;
    }
    if (duration.compareTo(MINIMUM_INTERVAL) < 0) {
      errors.add("'" + field + "' must be at least 1 second; got " + duration);
      return null;
    }
    return new TimeSchedule(kind, duration.toString());
  }

  private static Duration isoDuration(String field, String text, List<String> errors) {
    if (RUNTIME_EXPRESSION.matcher(text).matches()) {
      errors.add(
          "'"
              + field
              + "': runtime-expression durations are not supported in a schedule (no workflow"
              + " instance exists to evaluate them against)");
      return null;
    }
    try {
      return Duration.parse(text.strip());
    } catch (DateTimeParseException e) {
      errors.add(
          "'"
              + field
              + "' value '"
              + text
              + "' is not an ISO-8601 duration of days to fractional seconds (e.g. PT1H30M)");
      return null;
    }
  }

  private static Duration durationObject(String field, JsonNode value, List<String> errors) {
    Duration total = Duration.ZERO;
    boolean valid = true;
    for (Iterator<Map.Entry<String, JsonNode>> it = value.fields(); it.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = it.next();
      Function<Long, Duration> unit = DURATION_FIELDS.get(entry.getKey());
      if (unit == null) {
        errors.add("'" + field + "' has unknown duration field '" + entry.getKey() + "'");
        valid = false;
      } else if (!entry.getValue().canConvertToLong()
          || !entry.getValue().isIntegralNumber()
          || entry.getValue().asLong() < 0) {
        errors.add("'" + field + "." + entry.getKey() + "' must be a non-negative integer");
        valid = false;
      } else {
        total = total.plus(unit.apply(entry.getValue().asLong()));
      }
    }
    return valid ? total : null;
  }

  private static TimeSchedule cron(JsonNode value, List<String> errors) {
    if (!value.isTextual()) {
      errors.add("'schedule.cron' must be a string");
      return null;
    }
    String text = value.asText().strip();
    String[] fields = text.isEmpty() ? new String[0] : WHITESPACE.split(text);
    if (fields.length != CRON_FIELDS) {
      errors.add(
          "'schedule.cron': a standard 5-field cron expression is required (minute hour"
              + " day-of-month month day-of-week); '"
              + text
              + "' has "
              + fields.length
              + " field"
              + (fields.length == 1 ? "" : "s"));
      return null;
    }
    String normalized = String.join(" ", fields);
    try {
      UNIX_CRON.parse(normalized).validate();
    } catch (IllegalArgumentException e) {
      errors.add("'schedule.cron' '" + normalized + "' is not a valid cron expression");
      return null;
    }
    return new TimeSchedule(TriggerKind.CRON, normalized);
  }
}
