import { describe, expect, it } from "vitest";
import {
	formatAbsolute,
	formatDuration,
	formatOffset,
	formatRelative,
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
	it("accepts known values regardless of casing", () => {
		expect(normWorkflowStatus("deployed")).toBe("DEPLOYED");
		expect(normInstanceStatus("Running")).toBe("RUNNING");
	});

	it("passes unknown statuses through uppercased rather than dropping them", () => {
		expect(normWorkflowStatus("mystery")).toBe("MYSTERY");
	});

	it("collapses task lifecycle phases onto the UI's task statuses", () => {
		expect(normTaskStatus("started")).toBe("running");
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
		status: "DEPLOYED",
		createdAt: "2026-08-04T10:00:00Z",
	};

	it("maps a workflow summary to a table row with a relative updated time", () => {
		expect(toWorkflowRow(summary, NOW)).toEqual({
			name: "order-flow",
			latestVersion: "order-flow@v3a1b2c3d",
			status: "DEPLOYED",
			updated: "2h ago",
		});
	});

	it("maps a deployment, renaming orchestratorAppId to orchestrator", () => {
		const dto: DeploymentDto = {
			version: "v3",
			status: "DRAINED",
			stepServices: ["check-inventory", "charge-card"],
			orchestratorAppId: "orch-order-flow-v3",
			drainedAt: "2026-08-02T14:11:00Z",
		};

		expect(toWorkflowDeployment(dto)).toEqual({
			version: "v3",
			status: "DRAINED",
			orchestrator: "orch-order-flow-v3",
			stepServices: ["check-inventory", "charge-card"],
			drainedAt: "2026-08-02 14:11",
		});
	});

	it("leaves drainedAt null for an active deployment", () => {
		const dto: DeploymentDto = {
			version: "v3",
			status: "ACTIVE",
			stepServices: [],
			orchestratorAppId: "orch-order-flow-v3",
			drainedAt: null,
		};

		expect(toWorkflowDeployment(dto).drainedAt).toBeNull();
	});

	const versions: WorkflowVersionDto[] = [
		{ version: "v3", status: "DEPLOYED", createdAt: "2026-08-02T14:11:00Z" },
		{ version: "v2", status: "SUPERSEDED", createdAt: "2026-07-30T09:24:00Z" },
	];
	const deployments: DeploymentDto[] = [
		{
			version: "v3",
			status: "ACTIVE",
			stepServices: [],
			orchestratorAppId: "orch-v3",
			drainedAt: null,
		},
		{
			version: "v2",
			status: "DRAINED",
			stepServices: [],
			orchestratorAppId: "orch-v2",
			drainedAt: "2026-08-02T14:11:00Z",
		},
	];

	it("assembles a detail from the versions and deployments endpoints", () => {
		const detail = toWorkflowDetail("order-flow", versions, deployments, NOW);

		expect(detail.name).toBe("order-flow");
		expect(detail.latestVersion).toBe("v3");
		expect(detail.status).toBe("DEPLOYED");
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
		status: "RUNNING",
		startedAt: "2026-08-04T11:58:00Z",
		endedAt: null,
	};

	it("maps an instance summary to a row, renaming instanceId to id", () => {
		expect(toInstanceRow(summary, NOW)).toEqual({
			id: "inst-01h9k7a2b",
			workflow: "order-flow",
			version: "v3",
			status: "RUNNING",
			started: "2m ago",
			ended: null,
		});
	});

	it("keeps ended null while an instance is still in progress", () => {
		expect(toInstanceRow(summary, NOW).ended).toBeNull();
	});

	const detailDto: InstanceDetailDto = {
		...summary,
		status: "FAILED",
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
		expect(detail.status).toBe("FAILED");
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
			status: "RUNNING",
			endedAt: null,
		};

		expect(toInstanceDetail(running, events).duration).toBe("—");
		expect(toInstanceDetail(running, events).ended).toBeNull();
	});
});
