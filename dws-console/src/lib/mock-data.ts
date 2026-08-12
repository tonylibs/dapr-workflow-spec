/**
 * View models for the console's screens, mirroring the `dws-admin` read API.
 *
 * These were the mock fixtures the UI was prototyped against; the data now
 * comes from the live API through `admin-hooks.ts`, which adapts `dws-admin`'s
 * DTOs (`admin-types.ts`) into the types declared here. The types stayed put so
 * every screen and shared component keeps one vocabulary for a workflow, an
 * instance, and a task.
 */

/**
 * The status vocabularies below are the ones `dws-admin` actually stores — see
 * `dws-admin/src/events/controller-events.handler.ts` and
 * `orchestrator-events.handler.ts`, which write these literals. They are not
 * the labels the Phase 1–2 mockups used (`DEPLOYED`, `ACTIVE`, `RUNNING`);
 * those never existed in the read model.
 */
export type WorkflowStatus = "created" | "updated";
export type DeploymentStatus = "applied" | "failed" | "drained" | "collected";
export type InstanceStatus = "started" | "completed" | "failed";
/** `pending` covers a backoff gap in an attempt history; the API emits only the other three. */
export type TaskStatus = "started" | "completed" | "failed" | "pending";
export type TaskType =
	| "call"
	| "run"
	| "switch"
	| "set"
	| "wait"
	| "listen"
	| "emit"
	| "try"
	| "catch";

/**
 * Maps any status enum to the shared Organic hue class. The fallback matters
 * beyond the listed members: statuses arrive from the API as bare strings, so
 * an unrecognized one lands here and renders neutral rather than mis-coloured.
 */
export function statusClass(
	status: WorkflowStatus | DeploymentStatus | InstanceStatus | TaskStatus,
): "st-ok" | "st-run" | "st-pend" | "st-fail" | "st-drain" {
	switch (status) {
		case "completed":
		case "applied":
			return "st-ok";
		case "started":
		case "created":
		case "updated":
			return "st-run";
		case "failed":
			return "st-fail";
		case "drained":
		case "collected":
			return "st-drain";
		default:
			return "st-pend";
	}
}

// ── Workflows ─────────────────────────────────────────────────────────────

/** One row of the workflow list — a name with its latest version's state. */
export interface WorkflowRow {
	name: string;
	latestVersion: string;
	status: WorkflowStatus;
	updated: string;
}

/** One entry in a workflow's version history. */
export interface WorkflowVersion {
	version: string;
	status: WorkflowStatus;
	created: string;
	note: string;
}

/** One deployment card: the resources a version is (or was) running on. */
export interface WorkflowDeployment {
	version: string;
	status: DeploymentStatus;
	orchestrator: string;
	stepServices: string[];
	drainedAt: string | null;
}

/** The workflow detail screen — version history and deployments for one name. */
export interface WorkflowDetail {
	name: string;
	status: WorkflowStatus;
	latestVersion: string;
	versions: WorkflowVersion[];
	deployments: WorkflowDeployment[];
}

// ── Instances ─────────────────────────────────────────────────────────────

/** One row of the instance list. */
export interface InstanceRow {
	id: string;
	workflow: string;
	version: string;
	status: InstanceStatus;
	started: string;
	ended: string | null; // null => in progress / pending
}

/**
 * One attempt or backoff inside a task's retry history.
 *
 * Not populated from the read API: `task_events` records a lifecycle phase per
 * event, with no attempt or backoff detail. The type is kept because the
 * timeline renders it when the data exists — see `TaskEvent.attemptHistory`.
 */
export interface AttemptEvent {
	kind: "attempt" | "backoff";
	label: string;
	detail: string;
	time: string;
	status: TaskStatus;
}

/**
 * One row of the task timeline: a task, not a raw event. The adapter folds a
 * task's lifecycle events into one of these.
 *
 * The optional fields below describe retry and catch behavior that the read API
 * has no source for, so they are left unset and the row degrades to a
 * non-expandable one. They stay declared so the timeline needs no change once
 * `dws-admin` emits richer task events.
 */
export interface TaskEvent {
	name: string;
	type: TaskType;
	status: TaskStatus;
	statusLabel: string;
	when: string;
	duration: string;
	indent?: boolean; // catch-branch sibling
	attempts?: number;
	attemptHistory?: AttemptEvent[];
	caughtBy?: string;
	caughtError?: string;
	retryPolicy?: string;
}

/**
 * The instance detail screen. `duration`, `taskCount`, `failedCount` and
 * `retries` have no endpoint of their own — the adapter derives them from the
 * instance's timestamps and task events, so treat them as presentational.
 */
export interface InstanceDetail {
	id: string;
	workflow: string;
	version: string;
	orchestrator: string;
	status: InstanceStatus;
	started: string | null;
	ended: string | null;
	duration: string;
	taskCount: number;
	failedCount: number;
	retries: number;
	tasks: TaskEvent[];
}

/** The status filter chips offered on the instance list. */
export const INSTANCE_STATUSES: InstanceStatus[] = [
	"started",
	"completed",
	"failed",
];
