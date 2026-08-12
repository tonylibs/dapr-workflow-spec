/**
 * Translates `dws-admin` read-API DTOs into the console's view models.
 *
 * The two sides are close but not identical: the API returns ISO timestamps and
 * primitive fields, while the UI renders pre-formatted strings and a few
 * derived values. Everything presentational lives here so routes stay
 * declarative and the mapping is unit-testable in isolation.
 *
 * Fields the read API has no source for — a task's retry/attempt history, the
 * catch that handled it, the retry policy — are deliberately left `undefined`.
 * The timeline degrades to non-expandable rows rather than showing invented
 * data.
 */

import type {
	DeploymentDto,
	InstanceDetailDto,
	InstanceSummaryDto,
	TaskEventDto,
	WorkflowSummaryDto,
	WorkflowVersionDto,
} from "./admin-types";
import type {
	InstanceDetail,
	InstanceRow,
	InstanceStatus,
	TaskEvent,
	TaskStatus,
	TaskType,
	WorkflowDeployment,
	WorkflowDetail,
	WorkflowRow,
	WorkflowStatus,
	WorkflowVersion,
} from "./mock-data";

/** Rendered in place of any timestamp or derived value the read model has not produced yet. */
const NONE = "—";

const SECOND = 1000;
const MINUTE = 60 * SECOND;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;
const WEEK = 7 * DAY;

// ── Formatting ────────────────────────────────────────────────────────────

/**
 * Renders an ISO timestamp as an age relative to `now` ("3m ago", "2w ago").
 * `now` is injectable so the output is testable.
 */
export function formatRelative(
	iso: string | null | undefined,
	now: number = Date.now(),
): string {
	if (!iso) return NONE;
	const elapsed = now - Date.parse(iso);

	if (elapsed < MINUTE) return "just now";
	if (elapsed < HOUR) return `${Math.floor(elapsed / MINUTE)}m ago`;
	if (elapsed < DAY) return `${Math.floor(elapsed / HOUR)}h ago`;
	if (elapsed < WEEK) return `${Math.floor(elapsed / DAY)}d ago`;
	return `${Math.floor(elapsed / WEEK)}w ago`;
}

/**
 * Renders an ISO timestamp as `YYYY-MM-DD HH:mm` (optionally with seconds).
 * Deliberately UTC: a console reads one cluster's timeline, and an operator
 * comparing it against pod logs or `kubectl` output needs the same clock.
 */
export function formatAbsolute(
	iso: string | null | undefined,
	withSeconds = false,
): string {
	if (!iso) return NONE;
	const date = new Date(iso);
	const pad = (n: number) => String(n).padStart(2, "0");

	const day = `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
	const time = `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
	return withSeconds
		? `${day} ${time}:${pad(date.getUTCSeconds())}`
		: `${day} ${time}`;
}

/** Renders an elapsed span, scaling the unit to its magnitude ("180ms", "2.10s", "2m 42s"). */
export function formatDuration(ms: number): string {
	if (ms < SECOND) return `${Math.round(ms)}ms`;
	if (ms < MINUTE) return `${(ms / SECOND).toFixed(2)}s`;
	return `${Math.floor(ms / MINUTE)}m ${Math.floor((ms % MINUTE) / SECOND)}s`;
}

/** Renders a task's start as an offset from the instance start ("+0.18s", "+2m 44s"). */
export function formatOffset(ms: number): string {
	return `+${formatDuration(Math.max(0, ms)).replace(/^(\d+)ms$/, (_, n) => `${(Number(n) / SECOND).toFixed(2)}s`)}`;
}

// ── Status normalization ──────────────────────────────────────────────────

/**
 * The DTOs type every status as a bare `string`. The normalizers lower-case it
 * to match the vocabulary dws-admin stores (`created`/`updated`,
 * `applied`/`failed`/`drained`/`collected`, `started`/`completed`/`failed`) and
 * pass anything unrecognized through verbatim: the badge then shows the true
 * value with `statusClass`'s neutral fallback hue, which beats silently
 * relabeling it as a status it isn't.
 */
export function normWorkflowStatus(status: string): WorkflowStatus {
	return status.toLowerCase() as WorkflowStatus;
}

export function normDeploymentStatus(
	status: string,
): WorkflowDeployment["status"] {
	return status.toLowerCase() as WorkflowDeployment["status"];
}

export function normInstanceStatus(status: string): InstanceStatus {
	return status.toLowerCase() as InstanceStatus;
}

/** A task event's lifecycle phase. Anything unrecognized renders neutral. */
export function normTaskStatus(status: string): TaskStatus {
	switch (status.toLowerCase()) {
		case "started":
			return "started";
		case "completed":
			return "completed";
		case "failed":
			return "failed";
		default:
			return "pending";
	}
}

export function normTaskType(type: string): TaskType {
	return type.toLowerCase() as TaskType;
}

// ── Workflows ─────────────────────────────────────────────────────────────

/** `GET /workflows` item → workflow list row. */
export function toWorkflowRow(
	dto: WorkflowSummaryDto,
	now?: number,
): WorkflowRow {
	return {
		name: dto.name,
		latestVersion: dto.latestVersion,
		status: normWorkflowStatus(dto.status),
		updated: formatRelative(dto.createdAt, now),
	};
}

/** `GET /workflows/:name/deployments` item → deployment card. */
export function toWorkflowDeployment(dto: DeploymentDto): WorkflowDeployment {
	return {
		version: dto.version,
		status: normDeploymentStatus(dto.status),
		orchestrator: dto.orchestratorAppId,
		stepServices: dto.stepServices,
		drainedAt: dto.drainedAt ? formatAbsolute(dto.drainedAt) : null,
	};
}

/**
 * Assembles the workflow detail view from the two endpoints that feed it.
 * The version list arrives newest-first, so its head is the latest version and
 * carries the workflow's current status. A version's "note" is derived, not
 * fetched: the newest is `current`, and an older one is annotated with the
 * drain time of its matching deployment when there is one.
 */
export function toWorkflowDetail(
	name: string,
	versionDtos: WorkflowVersionDto[],
	deploymentDtos: DeploymentDto[],
	now?: number,
): WorkflowDetail {
	const drainedAtByVersion = new Map(
		deploymentDtos
			.filter((d) => d.drainedAt)
			.map((d) => [d.version, d.drainedAt as string]),
	);

	const versions: WorkflowVersion[] = versionDtos.map((dto, index) => {
		const drainedAt = drainedAtByVersion.get(dto.version);
		return {
			version: dto.version,
			status: normWorkflowStatus(dto.status),
			created: `${formatAbsolute(dto.createdAt)} · ${formatRelative(dto.createdAt, now)}`,
			note:
				index === 0
					? "current"
					: drainedAt
						? `drained at ${formatAbsolute(drainedAt).slice(-5)}`
						: "",
		};
	});

	const latest = versions[0];

	return {
		name,
		status: latest?.status ?? normWorkflowStatus("unknown"),
		latestVersion: latest?.version ?? NONE,
		versions,
		deployments: deploymentDtos.map(toWorkflowDeployment),
	};
}

// ── Instances ─────────────────────────────────────────────────────────────

/** `GET /instances` item → instance list row. A null `ended` renders as "in progress". */
export function toInstanceRow(
	dto: InstanceSummaryDto,
	now?: number,
): InstanceRow {
	return {
		id: dto.instanceId,
		workflow: dto.workflow,
		version: dto.version,
		status: normInstanceStatus(dto.status),
		started: formatRelative(dto.startedAt, now),
		ended: dto.endedAt ? formatRelative(dto.endedAt, now) : null,
	};
}

/**
 * Folds the flat `task_events` stream into one timeline row per task.
 *
 * The API records a *lifecycle event* per phase, but the timeline renders a
 * *task* per row: events are grouped by task name (first-seen order, which is
 * chronological because the endpoint sorts ascending), the row takes its status
 * from the group's terminal event, and its offset and duration are measured
 * from the group's first and last event.
 */
export function toTaskEvents(
	events: TaskEventDto[],
	instanceStartedAt: string | null,
): TaskEvent[] {
	const groups = new Map<string, TaskEventDto[]>();
	for (const event of events) {
		const group = groups.get(event.taskName);
		if (group) group.push(event);
		else groups.set(event.taskName, [event]);
	}

	const origin = instanceStartedAt
		? Date.parse(instanceStartedAt)
		: events.length > 0
			? Date.parse(events[0].timestamp)
			: 0;

	return Array.from(groups.values(), (group) => {
		const first = group[0];
		const last = group[group.length - 1];
		const status = normTaskStatus(last.status);

		return {
			name: first.taskName,
			type: normTaskType(first.type),
			status,
			statusLabel: last.error ? `${status} — ${last.error}` : status,
			when: formatOffset(Date.parse(first.timestamp) - origin),
			duration: formatDuration(
				Date.parse(last.timestamp) - Date.parse(first.timestamp),
			),
		};
	});
}

/**
 * Assembles the instance detail header from the summary endpoint plus the task
 * events. `duration`, `taskCount`, `failedCount` and `retries` have no DTO of
 * their own — they are derived here and are therefore presentational, not
 * authoritative. A retry is counted as a repeated `started` event on a task
 * that had already started.
 */
export function toInstanceDetail(
	dto: InstanceDetailDto,
	events: TaskEventDto[],
): InstanceDetail {
	const tasks = toTaskEvents(events, dto.startedAt);

	const startsPerTask = new Map<string, number>();
	for (const event of events) {
		if (normTaskStatus(event.status) !== "started") continue;
		startsPerTask.set(
			event.taskName,
			(startsPerTask.get(event.taskName) ?? 0) + 1,
		);
	}
	const retries = Array.from(startsPerTask.values()).reduce(
		(total, starts) => total + Math.max(0, starts - 1),
		0,
	);

	return {
		id: dto.instanceId,
		workflow: dto.workflow,
		version: dto.version,
		orchestrator: dto.appId,
		status: normInstanceStatus(dto.status),
		started: dto.startedAt ? formatAbsolute(dto.startedAt, true) : null,
		ended: dto.endedAt ? formatAbsolute(dto.endedAt, true) : null,
		duration:
			dto.startedAt && dto.endedAt
				? formatDuration(Date.parse(dto.endedAt) - Date.parse(dto.startedAt))
				: NONE,
		taskCount: tasks.length,
		failedCount: tasks.filter((t) => t.status === "failed").length,
		retries,
		tasks,
	};
}
