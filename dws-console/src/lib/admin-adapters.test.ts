import { describe, expect, it } from "vitest";
import {
	formatAbsolute,
	formatDuration,
	formatOffset,
	formatRelative,
	normDeploymentStatus,
	normInstanceStatus,
	normTaskStatus,
	normTaskType,
	normWorkflowStatus,
	toInstanceDetail,
	toInstanceRow,
	toTaskEvents,
	toWorkflowDeployment,
	toWorkflowDetail,
	toWorkflowRow,
} from "./admin-adapters";
import type {
	DeploymentDto,
	InstanceDetailDto,
	InstanceSummaryDto,
	TaskEventDto,
	WorkflowSummaryDto,
	WorkflowVersionDto,
} from "./admin-types";
import { INSTANCE_STATUSES, statusClass } from "./mock-data";

const NOW = Date.parse("2026-08-04T12:00:00Z");

describe("formatRelative", () => {
	it("reports sub-minute ages as 'just now'", () => {
		expect(formatRelative("2026-08-04T11:59:30Z", NOW)).toBe("just now");
	});

	it("reports minutes, hours, days and weeks", () => {
		expect(formatRelative("2026-08-04T11:57:00Z", NOW)).toBe("3m ago");
		expect(formatRelative("2026-08-04T10:00:00Z", NOW)).toBe("2h ago");
		expect(formatRelative("2026-08-03T12:00:00Z", NOW)).toBe("1d ago");
		expect(formatRelative("2026-07-21T12:00:00Z", NOW)).toBe("2w ago");
	});

	it("renders a missing timestamp as an em dash", () => {
		expect(formatRelative(null, NOW)).toBe("—");
	});
});

describe("formatAbsolute", () => {
	it("formats UTC minutes by default and seconds on request", () => {
		expect(formatAbsolute("2026-08-02T14:11:09Z")).toBe("2026-08-02 14:11");
		expect(formatAbsolute("2026-08-04T09:58:12Z", true)).toBe(
			"2026-08-04 09:58:12",
		);
	});

	it("renders a missing timestamp as an em dash", () => {
		expect(formatAbsolute(null)).toBe("—");
	});
});

describe("formatDuration / formatOffset", () => {
	it("scales duration across milliseconds, seconds and minutes", () => {
		expect(formatDuration(180)).toBe("180ms");
		expect(formatDuration(2100)).toBe("2.10s");
		expect(formatDuration(162_000)).toBe("2m 42s");
	});

	it("prefixes offsets with a plus sign", () => {
		expect(formatOffset(0)).toBe("+0.00s");
		expect(formatOffset(180)).toBe("+0.18s");
		expect(formatOffset(164_000)).toBe("+2m 44s");
	});
});

describe("status normalizers", () => {
	// The vocabulary here is what dws-admin actually writes — `created`/`updated`
	// for definitions, `applied`/`drained`/`collected` for deployments,
	// `started`/`completed`/`failed` for instances and task events.
	it("accepts the statuses dws-admin stores, regardless of casing", () => {
		expect(normWorkflowStatus("created")).toBe("created");
		expect(normWorkflowStatus("UPDATED")).toBe("updated");
		expect(normInstanceStatus("Started")).toBe("started");
	});

	it("passes unknown statuses through rather than dropping them", () => {
		expect(normWorkflowStatus("mystery")).toBe("mystery");
	});

	it("maps task lifecycle phases, defaulting the unknown to pending", () => {
		expect(normTaskStatus("started")).toBe("started");
		expect(normTaskStatus("completed")).toBe("completed");
		expect(normTaskStatus("failed")).toBe("failed");
		expect(normTaskStatus("something-else")).toBe("pending");
	});

	it("lowercases task types", () => {
		expect(normTaskType("Call")).toBe("call");
	});
});

describe("workflow adapters", () => {
	const summary: WorkflowSummaryDto = {
		name: "order-flow",
		latestVersion: "order-flow@v3a1b2c3d",
		status: "created",
		createdAt: "2026-08-04T10:00:00Z",
	};

	it("maps a workflow summary to a table row with a relative updated time", () => {
		expect(toWorkflowRow(summary, NOW)).toEqual({
			name: "order-flow",
			latestVersion: "order-flow@v3a1b2c3d",
			status: "created",
			updated: "2h ago",
		});
	});

	it("maps a deployment, renaming orchestratorAppId to orchestrator", () => {
		const dto: DeploymentDto = {
			version: "v3",
			status: "drained",
			stepServices: ["check-inventory", "charge-card"],
			orchestratorAppId: "orch-order-flow-v3",
			drainedAt: "2026-08-02T14:11:00Z",
		};

		expect(toWorkflowDeployment(dto)).toEqual({
			version: "v3",
			status: "drained",
			orchestrator: "orch-order-flow-v3",
			stepServices: ["check-inventory", "charge-card"],
			drainedAt: "2026-08-02 14:11",
		});
	});

	it("leaves drainedAt null for an active deployment", () => {
		const dto: DeploymentDto = {
			version: "v3",
			status: "applied",
			stepServices: [],
			orchestratorAppId: "orch-order-flow-v3",
			drainedAt: null,
		};

		expect(toWorkflowDeployment(dto).drainedAt).toBeNull();
	});

	const versions: WorkflowVersionDto[] = [
		{ version: "v3", status: "updated", createdAt: "2026-08-02T14:11:00Z" },
		{ version: "v2", status: "created", createdAt: "2026-07-30T09:24:00Z" },
	];
	const deployments: DeploymentDto[] = [
		{
			version: "v3",
			status: "applied",
			stepServices: [],
			orchestratorAppId: "orch-v3",
			drainedAt: null,
		},
		{
			version: "v2",
			status: "drained",
			stepServices: [],
			orchestratorAppId: "orch-v2",
			drainedAt: "2026-08-02T14:11:00Z",
		},
	];

	it("assembles a detail from the versions and deployments endpoints", () => {
		const detail = toWorkflowDetail("order-flow", versions, deployments, NOW);

		expect(detail.name).toBe("order-flow");
		expect(detail.latestVersion).toBe("v3");
		expect(detail.status).toBe("updated");
		expect(detail.versions).toHaveLength(2);
		expect(detail.deployments).toHaveLength(2);
	});

	it("notes the newest version as current and derives a drained note for older ones", () => {
		const detail = toWorkflowDetail("order-flow", versions, deployments, NOW);

		expect(detail.versions[0].note).toBe("current");
		expect(detail.versions[0].created).toBe("2026-08-02 14:11 · 1d ago");
		expect(detail.versions[1].note).toBe("drained at 14:11");
	});

	it("survives a workflow with no stored versions", () => {
		const detail = toWorkflowDetail("empty-flow", [], [], NOW);

		expect(detail.latestVersion).toBe("—");
		expect(detail.versions).toEqual([]);
	});
});

describe("instance adapters", () => {
	const summary: InstanceSummaryDto = {
		instanceId: "inst-01h9k7a2b",
		workflow: "order-flow",
		version: "v3",
		status: "started",
		startedAt: "2026-08-04T11:58:00Z",
		endedAt: null,
	};

	it("maps an instance summary to a row, renaming instanceId to id", () => {
		expect(toInstanceRow(summary, NOW)).toEqual({
			id: "inst-01h9k7a2b",
			workflow: "order-flow",
			version: "v3",
			status: "started",
			started: "2m ago",
			ended: null,
		});
	});

	it("keeps ended null while an instance is still in progress", () => {
		expect(toInstanceRow(summary, NOW).ended).toBeNull();
	});

	const detailDto: InstanceDetailDto = {
		...summary,
		status: "failed",
		startedAt: "2026-08-04T09:58:12Z",
		endedAt: "2026-08-04T10:00:59Z",
		appId: "orch-order-flow-v3",
	};

	const events: TaskEventDto[] = [
		{
			id: "1",
			taskName: "validate-payload",
			type: "call",
			status: "started",
			timestamp: "2026-08-04T09:58:12.000Z",
			error: null,
		},
		{
			id: "2",
			taskName: "validate-payload",
			type: "call",
			status: "completed",
			timestamp: "2026-08-04T09:58:12.180Z",
			error: null,
		},
		{
			id: "3",
			taskName: "dispatch-shipment",
			type: "call",
			status: "started",
			timestamp: "2026-08-04T09:58:14.000Z",
			error: null,
		},
		{
			id: "4",
			taskName: "dispatch-shipment",
			type: "call",
			status: "started",
			timestamp: "2026-08-04T09:58:44.000Z",
			error: null,
		},
		{
			id: "5",
			taskName: "dispatch-shipment",
			type: "call",
			status: "failed",
			timestamp: "2026-08-04T10:00:56.000Z",
			error: "CarrierUnavailable",
		},
	];

	it("groups task events into one row per task, in first-seen order", () => {
		const tasks = toTaskEvents(events, detailDto.startedAt);

		expect(tasks.map((t) => t.name)).toEqual([
			"validate-payload",
			"dispatch-shipment",
		]);
	});

	it("takes each row's status from its terminal event", () => {
		const tasks = toTaskEvents(events, detailDto.startedAt);

		expect(tasks[0].status).toBe("completed");
		expect(tasks[1].status).toBe("failed");
	});

	it("derives offset and duration from the group's first and last event", () => {
		const tasks = toTaskEvents(events, detailDto.startedAt);

		expect(tasks[0].when).toBe("+0.00s");
		expect(tasks[0].duration).toBe("180ms");
		expect(tasks[1].when).toBe("+2.00s");
		expect(tasks[1].duration).toBe("2m 42s");
	});

	it("leaves retry and catch fields unset — the read API has no source for them", () => {
		const task = toTaskEvents(events, detailDto.startedAt)[1];

		expect(task.attempts).toBeUndefined();
		expect(task.attemptHistory).toBeUndefined();
		expect(task.retryPolicy).toBeUndefined();
		expect(task.caughtBy).toBeUndefined();
		expect(task.caughtError).toBeUndefined();
		expect(task.indent).toBeUndefined();
	});

	it("returns no rows when the read model has not reported task events yet", () => {
		expect(toTaskEvents([], detailDto.startedAt)).toEqual([]);
	});

	it("assembles an instance detail, renaming appId to orchestrator", () => {
		const detail = toInstanceDetail(detailDto, events);

		expect(detail.id).toBe("inst-01h9k7a2b");
		expect(detail.orchestrator).toBe("orch-order-flow-v3");
		expect(detail.status).toBe("failed");
		expect(detail.started).toBe("2026-08-04 09:58:12");
		expect(detail.ended).toBe("2026-08-04 10:00:59");
	});

	it("derives duration and task counts from the returned events", () => {
		const detail = toInstanceDetail(detailDto, events);

		expect(detail.duration).toBe("2m 47s");
		expect(detail.taskCount).toBe(2);
		expect(detail.failedCount).toBe(1);
	});

	it("counts a repeated start on the same task as a retry", () => {
		expect(toInstanceDetail(detailDto, events).retries).toBe(1);
	});

	it("reports an unfinished instance's duration as in progress", () => {
		const running: InstanceDetailDto = {
			...detailDto,
			status: "started",
			endedAt: null,
		};

		expect(toInstanceDetail(running, events).duration).toBe("—");
		expect(toInstanceDetail(running, events).ended).toBeNull();
	});
});

/**
 * Guards the mistake this suite originally encoded: the adapters were written
 * against the mockups' vocabulary (`DEPLOYED`, `ACTIVE`, `RUNNING`) rather than
 * the values dws-admin writes. Everything else passed, because the fixtures
 * carried the same wrong assumption as the code.
 *
 * The literals below are asserted against
 * `dws-admin/src/events/controller-events.handler.ts` and
 * `orchestrator-events.handler.ts`. If dws-admin's vocabulary changes, these
 * fail rather than the console silently rendering neutral pills and filters
 * that match nothing.
 */
describe("status vocabulary matches what dws-admin stores", () => {
	const STORED = {
		definition: ["created", "updated"],
		deployment: ["applied", "failed", "drained", "collected"],
		instance: ["started", "completed", "failed"],
		task: ["started", "completed", "failed"],
	};

	it("gives every stored status a meaningful hue, never the neutral fallback", () => {
		for (const status of STORED.definition) {
			expect(statusClass(normWorkflowStatus(status))).not.toBe("st-pend");
		}
		for (const status of STORED.deployment) {
			expect(statusClass(normDeploymentStatus(status))).not.toBe("st-pend");
		}
		for (const status of STORED.instance) {
			expect(statusClass(normInstanceStatus(status))).not.toBe("st-pend");
		}
		for (const status of STORED.task) {
			expect(statusClass(normTaskStatus(status))).not.toBe("st-pend");
		}
	});

	it("offers instance filter chips that dws-admin can actually match", () => {
		// The chips are sent verbatim as ?status=…; dws-admin compares them
		// case-sensitively against the stored value.
		expect([...INSTANCE_STATUSES]).toEqual(STORED.instance);
	});
});
