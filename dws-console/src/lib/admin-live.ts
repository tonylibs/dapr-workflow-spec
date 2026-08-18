/**
 * Cache patches for the live (SSE) instance updates.
 *
 * These are deliberately pure functions over the cached query data rather than
 * inline callbacks in the hooks: a pushed event has to be merged into what is
 * already cached — not trigger a refetch — so the merge rules (ordering,
 * de-duplication, "ignore what isn't loaded") are the whole substance of the
 * feature and are unit-tested directly here.
 */

import type { InfiniteData } from "@tanstack/react-query";
import type {
	InstanceDetailDto,
	InstanceStatusDeltaDto,
	InstanceStatusEventDto,
	InstanceSummaryDto,
	Page,
	TaskEventDto,
	TaskEventStreamDto,
} from "./admin-types";

/** An instance is running until one of these lands; dws-admin never moves back out of them. */
export const TERMINAL_INSTANCE_STATUSES = ["completed", "failed"] as const;

export function isTerminalInstanceStatus(status: string): boolean {
	return (TERMINAL_INSTANCE_STATUSES as readonly string[]).includes(
		status.toLowerCase(),
	);
}

export function isRunningInstanceStatus(
	status: string | undefined | null,
): boolean {
	return status != null && !isTerminalInstanceStatus(status);
}

/**
 * What `useInstanceDetail` caches: the raw DTOs, not the assembled view model.
 *
 * The view model is derived in the query's `select`, so a pushed event only has
 * to patch the DTOs and the adapter re-runs over the result. Caching the view
 * model instead would leave no source to merge an incremental event into.
 */
export interface InstanceDetailData {
	instance: InstanceDetailDto;
	tasks: TaskEventDto[];
}

/** Orders task events the way `GET /instances/:id/tasks` does — the adapter's grouping assumes it. */
function compareTaskEvents(a: TaskEventDto, b: TaskEventDto): number {
	const byTime = Date.parse(a.timestamp) - Date.parse(b.timestamp);
	return byTime !== 0 ? byTime : a.id.localeCompare(b.id);
}

/**
 * Merges a pushed instance status into the cached detail. Returns `prev`
 * unchanged (same identity, so no re-render) when there is nothing cached yet
 * or the event is for another instance.
 */
export function applyInstanceStatus(
	prev: InstanceDetailData | undefined,
	event: InstanceStatusEventDto,
): InstanceDetailData | undefined {
	if (!prev || prev.instance.instanceId !== event.instanceId) return prev;

	return {
		...prev,
		instance: {
			...prev.instance,
			status: event.status,
			startedAt: event.startedAt,
			endedAt: event.endedAt,
		},
	};
}

/**
 * Adds a pushed task event to the cached detail, keeping the list in the
 * endpoint's ascending order. A replay of an id already held is ignored, so a
 * reconnect's resync overlapping with live delivery cannot double-count a task.
 */
export function applyTaskEvent(
	prev: InstanceDetailData | undefined,
	event: TaskEventStreamDto,
): InstanceDetailData | undefined {
	if (!prev || prev.instance.instanceId !== event.instanceId) return prev;
	if (prev.tasks.some((task) => task.id === event.id)) return prev;

	const { instanceId: _instanceId, ...task } = event;
	return { ...prev, tasks: [...prev.tasks, task].sort(compareTaskEvents) };
}

/**
 * Patches a status delta into the instance list's loaded pages.
 *
 * Only rows already fetched are touched: an instance the operator has not
 * paged to yet is not inserted, which would otherwise reorder the list under
 * them or duplicate a row a later page will deliver anyway. When nothing
 * matches, `prev` is returned by identity so the list does not re-render.
 */
export function applyStatusDelta(
	prev: InfiniteData<Page<InstanceSummaryDto>> | undefined,
	delta: InstanceStatusDeltaDto,
): InfiniteData<Page<InstanceSummaryDto>> | undefined {
	if (!prev) return prev;

	let changed = false;
	const pages = prev.pages.map((page) => {
		let pageChanged = false;
		const items = page.items.map((item) => {
			if (item.instanceId !== delta.instanceId) return item;
			if (item.status === delta.status && item.endedAt === delta.endedAt) {
				return item;
			}
			pageChanged = true;
			return { ...item, status: delta.status, endedAt: delta.endedAt };
		});
		if (!pageChanged) return page;
		changed = true;
		return { ...page, items };
	});

	return changed ? { ...prev, pages } : prev;
}
