import type { InfiniteData } from "@tanstack/react-query";
import { describe, expect, it } from "vitest";
import { toInstanceDetail } from "./admin-adapters";
import {
	applyInstanceStatus,
	applyStatusDelta,
	applyTaskEvent,
	type InstanceDetailData,
	isRunningInstanceStatus,
	isTerminalInstanceStatus,
} from "./admin-live";
import type {
	InstanceDetailDto,
	InstanceSummaryDto,
	Page,
	TaskEventDto,
} from "./admin-types";

const instanceDto = (over: Partial<InstanceDetailDto> = {}): InstanceDetailDto => ({
	instanceId: "inst-1",
	workflow: "order-flow",
	version: "order-flow@v1",
	appId: "order-flow",
	status: "started",
	startedAt: "2026-08-04T12:00:00.000Z",
	endedAt: null,
	...over,
});

const taskDto = (over: Partial<TaskEventDto> & { id: string }): TaskEventDto => ({
	taskName: "checkInventory",
	type: "call",
	status: "started",
	timestamp: "2026-08-04T12:00:01.000Z",
	error: null,
	...over,
});

const detail = (over: Partial<InstanceDetailData> = {}): InstanceDetailData => ({
	instance: instanceDto(),
	tasks: [],
	...over,
});

describe("status predicates", () => {
	it("treats completed and failed as terminal, case-insensitively", () => {
		expect(isTerminalInstanceStatus("completed")).toBe(true);
		expect(isTerminalInstanceStatus("FAILED")).toBe(true);
		expect(isTerminalInstanceStatus("started")).toBe(false);
	});

	it("treats a missing status as not running, so no subscription is opened before load", () => {
		expect(isRunningInstanceStatus(undefined)).toBe(false);
		expect(isRunningInstanceStatus(null)).toBe(false);
		expect(isRunningInstanceStatus("started")).toBe(true);
		expect(isRunningInstanceStatus("completed")).toBe(false);
	});
});

describe("applyInstanceStatus", () => {
	it("merges the pushed status and timestamps into the cached instance", () => {
		const next = applyInstanceStatus(detail(), {
			instanceId: "inst-1",
			status: "completed",
			startedAt: "2026-08-04T12:00:00.000Z",
			endedAt: "2026-08-04T12:05:00.000Z",
		});

		expect(next?.instance).toMatchObject({
			status: "completed",
			endedAt: "2026-08-04T12:05:00.000Z",
			// Immutable fields survive the merge.
			workflow: "order-flow",
			appId: "order-flow",
		});
	});

	it("ignores an event for another instance, preserving cache identity", () => {
		const prev = detail();
		const next = applyInstanceStatus(prev, {
			instanceId: "inst-other",
			status: "completed",
			startedAt: null,
			endedAt: "2026-08-04T12:05:00.000Z",
		});

		expect(next).toBe(prev);
	});

	it("is a no-op when nothing is cached yet", () => {
		expect(
			applyInstanceStatus(undefined, {
				instanceId: "inst-1",
				status: "completed",
				startedAt: null,
				endedAt: null,
			}),
		).toBeUndefined();
	});
});

describe("applyTaskEvent", () => {
	it("appends a pushed task event", () => {
		const next = applyTaskEvent(detail(), {
			instanceId: "inst-1",
			...taskDto({ id: "evt-1" }),
		});

		expect(next?.tasks).toHaveLength(1);
		expect(next?.tasks[0]).toEqual(taskDto({ id: "evt-1" }));
		// instanceId is a stream-only field and must not leak into the task DTO.
		expect(next?.tasks[0]).not.toHaveProperty("instanceId");
	});

	it("keeps events in ascending timestamp order regardless of arrival order", () => {
		const prev = detail({
			tasks: [taskDto({ id: "evt-3", timestamp: "2026-08-04T12:00:03.000Z" })],
		});

		const next = applyTaskEvent(prev, {
			instanceId: "inst-1",
			...taskDto({ id: "evt-2", timestamp: "2026-08-04T12:00:02.000Z" }),
		});

		expect(next?.tasks.map((t) => t.id)).toEqual(["evt-2", "evt-3"]);
	});

	it("ignores a replay of an event id already held", () => {
		const prev = detail({ tasks: [taskDto({ id: "evt-1" })] });
		const next = applyTaskEvent(prev, {
			instanceId: "inst-1",
			...taskDto({ id: "evt-1" }),
		});

		expect(next).toBe(prev);
	});

	it("ignores a task event for another instance", () => {
		const prev = detail();
		const next = applyTaskEvent(prev, {
			instanceId: "inst-other",
			...taskDto({ id: "evt-1" }),
		});

		expect(next).toBe(prev);
	});

	it("produces a view model the adapter can derive counts from", () => {
		let data: InstanceDetailData | undefined = detail();
		data = applyTaskEvent(data, {
			instanceId: "inst-1",
			...taskDto({ id: "evt-1", status: "started" }),
		});
		data = applyTaskEvent(data, {
			instanceId: "inst-1",
			...taskDto({
				id: "evt-2",
				status: "failed",
				timestamp: "2026-08-04T12:00:02.000Z",
				error: "upstream 502",
			}),
		});
		data = applyInstanceStatus(data as InstanceDetailData, {
			instanceId: "inst-1",
			status: "failed",
			startedAt: "2026-08-04T12:00:00.000Z",
			endedAt: "2026-08-04T12:00:02.000Z",
		});

		const view = toInstanceDetail(
			(data as InstanceDetailData).instance,
			(data as InstanceDetailData).tasks,
		);
		expect(view.status).toBe("failed");
		expect(view.taskCount).toBe(1);
		expect(view.failedCount).toBe(1);
	});
});

describe("applyStatusDelta", () => {
	const page = (items: InstanceSummaryDto[]): Page<InstanceSummaryDto> => ({
		items,
		nextCursor: null,
	});

	const summary = (
		over: Partial<InstanceSummaryDto> & { instanceId: string },
	): InstanceSummaryDto => ({
		workflow: "order-flow",
		version: "order-flow@v1",
		status: "started",
		startedAt: "2026-08-04T12:00:00.000Z",
		endedAt: null,
		...over,
	});

	const pages = (
		...items: InstanceSummaryDto[][]
	): InfiniteData<Page<InstanceSummaryDto>> => ({
		pages: items.map(page),
		pageParams: items.map(() => undefined),
	});

	it("patches a loaded row's status and end time in place", () => {
		const prev = pages([summary({ instanceId: "inst-1" })]);
		const next = applyStatusDelta(prev, {
			instanceId: "inst-1",
			status: "completed",
			endedAt: "2026-08-04T12:05:00.000Z",
		});

		expect(next?.pages[0].items[0]).toMatchObject({
			status: "completed",
			endedAt: "2026-08-04T12:05:00.000Z",
			// Untouched columns keep their values.
			workflow: "order-flow",
			startedAt: "2026-08-04T12:00:00.000Z",
		});
	});

	it("patches a row on a later loaded page without disturbing earlier pages", () => {
		const prev = pages(
			[summary({ instanceId: "inst-1" })],
			[summary({ instanceId: "inst-2" })],
		);
		const next = applyStatusDelta(prev, {
			instanceId: "inst-2",
			status: "failed",
			endedAt: "2026-08-04T12:05:00.000Z",
		});

		// First page keeps its identity, so it does not re-render.
		expect(next?.pages[0]).toBe(prev.pages[0]);
		expect(next?.pages[1].items[0].status).toBe("failed");
	});

	it("ignores an instance not present in any loaded page", () => {
		const prev = pages([summary({ instanceId: "inst-1" })]);
		const next = applyStatusDelta(prev, {
			instanceId: "inst-unloaded",
			status: "completed",
			endedAt: "2026-08-04T12:05:00.000Z",
		});

		expect(next).toBe(prev);
	});

	it("does not insert a row for an unloaded instance", () => {
		const prev = pages([summary({ instanceId: "inst-1" })]);
		const next = applyStatusDelta(prev, {
			instanceId: "inst-unloaded",
			status: "started",
			endedAt: null,
		});

		expect(next?.pages.flatMap((p) => p.items)).toHaveLength(1);
	});

	it("ignores a delta that changes nothing, preserving identity", () => {
		const prev = pages([
			summary({ instanceId: "inst-1", status: "completed", endedAt: "2026-08-04T12:05:00.000Z" }),
		]);
		const next = applyStatusDelta(prev, {
			instanceId: "inst-1",
			status: "completed",
			endedAt: "2026-08-04T12:05:00.000Z",
		});

		expect(next).toBe(prev);
	});

	it("is a no-op when nothing is cached yet", () => {
		expect(
			applyStatusDelta(undefined, {
				instanceId: "inst-1",
				status: "completed",
				endedAt: null,
			}),
		).toBeUndefined();
	});
});
