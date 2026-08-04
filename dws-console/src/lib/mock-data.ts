/**
 * Static mock data mirroring the dws-admin read API shapes, populated to match
 * the Claude Design mockups. Swap these functions for TanStack Query calls
 * against GET /workflows, /instances, etc. when the console goes live — the
 * return shapes intentionally track the documented endpoints.
 */

export type WorkflowStatus =
	| "DEPLOYED"
	| "DEPLOYING"
	| "FAILED"
	| "DRAINED"
	| "SUPERSEDED";
export type DeploymentStatus = "ACTIVE" | "DRAINED";
export type InstanceStatus = "RUNNING" | "COMPLETED" | "FAILED" | "PENDING";
export type TaskStatus = "completed" | "running" | "failed" | "pending";
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

/** Maps any status enum to the shared Organic hue class. */
export function statusClass(
	status: WorkflowStatus | DeploymentStatus | InstanceStatus | TaskStatus,
): "st-ok" | "st-run" | "st-pend" | "st-fail" | "st-drain" {
	switch (status) {
		case "DEPLOYED":
		case "ACTIVE":
		case "COMPLETED":
		case "completed":
			return "st-ok";
		case "DEPLOYING":
		case "RUNNING":
		case "running":
			return "st-run";
		case "FAILED":
		case "failed":
			return "st-fail";
		case "DRAINED":
		case "SUPERSEDED":
			return "st-drain";
		default:
			return "st-pend";
	}
}

// ── Workflows ─────────────────────────────────────────────────────────────

export interface WorkflowRow {
	name: string;
	latestVersion: string;
	status: WorkflowStatus;
	updated: string;
}

export const workflows: WorkflowRow[] = [
	{
		name: "workflow-a",
		latestVersion: "v3",
		status: "DEPLOYED",
		updated: "2h ago",
	},
	{
		name: "workflow-b",
		latestVersion: "v1",
		status: "DEPLOYED",
		updated: "1d ago",
	},
	{
		name: "workflow-c",
		latestVersion: "v2",
		status: "DEPLOYING",
		updated: "3m ago",
	},
	{
		name: "workflow-d",
		latestVersion: "v5",
		status: "FAILED",
		updated: "18m ago",
	},
	{
		name: "workflow-e",
		latestVersion: "v1",
		status: "DRAINED",
		updated: "6d ago",
	},
	{
		name: "workflow-f",
		latestVersion: "v4",
		status: "DEPLOYED",
		updated: "2w ago",
	},
];

export interface WorkflowVersion {
	version: string;
	status: WorkflowStatus;
	created: string;
	note: string;
}

export interface WorkflowDeployment {
	version: string;
	status: DeploymentStatus;
	orchestrator: string;
	stepServices: string[];
	drainedAt: string | null;
}

export interface WorkflowDetail {
	name: string;
	status: WorkflowStatus;
	latestVersion: string;
	versions: WorkflowVersion[];
	deployments: WorkflowDeployment[];
}

const workflowDetails: Record<string, WorkflowDetail> = {
	"workflow-a": {
		name: "workflow-a",
		status: "DEPLOYED",
		latestVersion: "v3",
		versions: [
			{
				version: "v3",
				status: "DEPLOYED",
				created: "2026-08-02 14:11 · 2h ago",
				note: "current",
			},
			{
				version: "v2",
				status: "SUPERSEDED",
				created: "2026-07-30 09:24 · 3d ago",
				note: "drained at 14:11",
			},
			{
				version: "v1",
				status: "SUPERSEDED",
				created: "2026-07-26 22:02 · 1w ago",
				note: "drained at 09:24",
			},
		],
		deployments: [
			{
				version: "v3",
				status: "ACTIVE",
				orchestrator: "orch-wf-a-v3",
				stepServices: ["svc-1", "svc-2", "svc-3"],
				drainedAt: null,
			},
			{
				version: "v2",
				status: "DRAINED",
				orchestrator: "orch-wf-a-v2",
				stepServices: ["svc-1", "svc-2"],
				drainedAt: "2026-08-02 14:11",
			},
		],
	},
};

export function getWorkflowDetail(name: string): WorkflowDetail | undefined {
	return workflowDetails[name];
}

// ── Instances ─────────────────────────────────────────────────────────────

export interface InstanceRow {
	id: string;
	workflow: string;
	version: string;
	status: InstanceStatus;
	started: string;
	ended: string | null; // null => in progress / pending
}

export const instances: InstanceRow[] = [
	{
		id: "inst-01h9k…7a2b",
		workflow: "workflow-a",
		version: "v3",
		status: "RUNNING",
		started: "2m ago",
		ended: null,
	},
	{
		id: "inst-01h9k…5c81",
		workflow: "workflow-a",
		version: "v3",
		status: "FAILED",
		started: "1h ago",
		ended: "57m ago",
	},
	{
		id: "inst-01h9j…d3fe",
		workflow: "workflow-b",
		version: "v1",
		status: "FAILED",
		started: "3h ago",
		ended: "3h ago",
	},
	{
		id: "inst-01h9j…9040",
		workflow: "workflow-a",
		version: "v3",
		status: "RUNNING",
		started: "5h ago",
		ended: null,
	},
	{
		id: "inst-01h9j…b71c",
		workflow: "workflow-a",
		version: "v2",
		status: "FAILED",
		started: "8h ago",
		ended: "8h ago",
	},
	{
		id: "inst-01h9h…62a4",
		workflow: "workflow-c",
		version: "v2",
		status: "FAILED",
		started: "1d ago",
		ended: "1d ago",
	},
];

export interface AttemptEvent {
	kind: "attempt" | "backoff";
	label: string;
	detail: string;
	time: string;
	status: TaskStatus;
}

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

const instanceDetails: Record<string, InstanceDetail> = {
	"inst-01h9k…5c81": {
		id: "inst-01h9k…5c81",
		workflow: "workflow-a",
		version: "v3",
		orchestrator: "orch-wf-a-v3",
		status: "FAILED",
		started: "2026-08-04 09:58:12",
		ended: "2026-08-04 10:00:59",
		duration: "2m 47s",
		taskCount: 5,
		failedCount: 1,
		retries: 3,
		tasks: [
			{
				name: "validate-payload",
				type: "call",
				status: "completed",
				statusLabel: "completed",
				when: "+0.00s",
				duration: "180ms",
			},
			{
				name: "enrich-order",
				type: "run",
				status: "completed",
				statusLabel: "completed",
				when: "+0.18s",
				duration: "2.10s",
			},
			{
				name: "choose-carrier",
				type: "switch",
				status: "completed",
				statusLabel: "completed",
				when: "+2.28s",
				duration: "30ms",
			},
			{
				name: "dispatch-shipment",
				type: "try",
				status: "failed",
				statusLabel: "failed — caught",
				when: "+2.31s",
				duration: "2m 42s",
				attempts: 3,
				retryPolicy: "retry policy: 3× exponential backoff",
				attemptHistory: [
					{
						kind: "attempt",
						label: "Attempt 1",
						detail: "carrier-api call — HTTP 504 gateway timeout after 30s",
						time: "+2.31s → +32.31s",
						status: "failed",
					},
					{
						kind: "backoff",
						label: "backoff",
						detail: "wait 5s",
						time: "→ +37.31s",
						status: "pending",
					},
					{
						kind: "attempt",
						label: "Attempt 2",
						detail: "carrier-api call — HTTP 500 internal error",
						time: "+37.31s → +48.02s",
						status: "failed",
					},
					{
						kind: "backoff",
						label: "backoff",
						detail: "wait 10s",
						time: "→ +58.02s",
						status: "pending",
					},
					{
						kind: "attempt",
						label: "Attempt 3",
						detail:
							"carrier-api call — HTTP 500 internal error · retries exhausted",
						time: "+58.02s → +2m 44s",
						status: "failed",
					},
				],
				caughtBy: "log-and-notify",
				caughtError:
					"CarrierUnavailable: dispatch could not be completed after 3 attempts (upstream 5xx).",
			},
			{
				name: "log-and-notify",
				type: "catch",
				status: "completed",
				statusLabel: "completed",
				when: "+2m 44s",
				duration: "3.02s",
				indent: true,
			},
			{
				name: "emit-failure-event",
				type: "emit",
				status: "completed",
				statusLabel: "completed",
				when: "+2m 47s",
				duration: "50ms",
			},
		],
	},
};

export function getInstanceDetail(id: string): InstanceDetail | undefined {
	return instanceDetails[id];
}

export const INSTANCE_STATUSES: InstanceStatus[] = [
	"RUNNING",
	"COMPLETED",
	"FAILED",
	"PENDING",
];
