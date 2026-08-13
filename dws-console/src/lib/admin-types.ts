/**
 * Wire types for the `dws-admin` read API, mirroring its NestJS DTOs
 * (`dws-admin/src/**\/dto/*.ts`). These are the shapes as they arrive over HTTP:
 * `date-time` fields are ISO strings, not `Date`. The adapters in
 * `admin-adapters.ts` translate them into the console view models exported
 * from `mock-data.ts`.
 */

/** Uniform page envelope returned by every list endpoint. */
export interface Page<T> {
	items: T[];
	/** Opaque cursor for the next page; null on the last page. */
	nextCursor: string | null;
}

/** One entry in `GET /workflows`. */
export interface WorkflowSummaryDto {
	name: string;
	latestVersion: string;
	status: string;
	createdAt: string;
}

/** One version in `GET /workflows/:name`. */
export interface WorkflowVersionDto {
	version: string;
	status: string;
	createdAt: string;
}

/** One deployment in `GET /workflows/:name/deployments`. */
export interface DeploymentDto {
	version: string;
	status: string;
	stepServices: string[];
	orchestratorAppId: string;
	drainedAt: string | null;
}

/** One row in `GET /instances`. Lifecycle timestamps are nullable: the read model is eventually consistent. */
export interface InstanceSummaryDto {
	instanceId: string;
	workflow: string;
	version: string;
	status: string;
	startedAt: string | null;
	endedAt: string | null;
}

/** `GET /instances/:id` — summary plus the orchestrator's Dapr app-id. */
export interface InstanceDetailDto extends InstanceSummaryDto {
	appId: string;
}

/**
 * One task event in `GET /instances/:id/tasks`. Note this is a *lifecycle
 * event*, not a task: a single task emits several (started, then completed or
 * failed). `admin-adapters.ts` groups them back into one row per task.
 */
export interface TaskEventDto {
	id: string;
	taskName: string;
	type: string;
	status: string;
	timestamp: string;
	error: string | null;
}
