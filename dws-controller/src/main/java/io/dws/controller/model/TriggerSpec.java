package io.dws.controller.model;

/**
 * A version's normalized time trigger: what the controller stamps into the version's trigger
 * ConfigMap ({@code trigger.json}) for {@code dws-schedule-time} to fire. The scheduler parses no
 * DSL, so every field here is already validated and canonical.
 *
 * @param workflow kebab-cased workflow name
 * @param version the version ID ({@code v<sha256-8>}) this trigger belongs to
 * @param appId the orchestrator's Dapr app-id the scheduler starts instances on
 * @param kind which of {@code every}, {@code cron} or {@code after} the definition set
 * @param schedule a canonical ISO-8601 duration ({@code every}/{@code after}, e.g. {@code PT1H30M})
 *     or a single-space-separated standard 5-field cron ({@code cron})
 */
public record TriggerSpec(
    String workflow, String version, String appId, TriggerKind kind, String schedule) {}
