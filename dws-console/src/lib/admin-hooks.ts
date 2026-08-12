/**
 * TanStack Query hooks over the `dws-admin` read API — the console's single
 * data-fetching entry point.
 *
 * Each hook owns one screen's data: it fetches through `admin-client.ts`,
 * translates DTOs with `admin-adapters.ts`, and hands routes view models plus
 * the query status they render loading/empty/error states from. List hooks are
 * infinite queries because the API paginates by opaque cursor, so "Load more"
 * maps directly onto `fetchNextPage`.
 */

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import {
	toInstanceDetail,
	toInstanceRow,
	toWorkflowDetail,
	toWorkflowRow,
} from "./admin-adapters";
import {
	ApiError,
	fetchInstance,
	fetchInstances,
	fetchInstanceTasks,
	fetchWorkflowDeployments,
	fetchWorkflows,
	fetchWorkflowVersions,
	type InstanceFilters,
} from "./admin-client";
import type {
	InstanceDetail,
	InstanceRow,
	WorkflowDetail,
	WorkflowRow,
} from "./mock-data";

/** Cursor of the first page: `undefined` lets the service pick its own start. */
const FIRST_PAGE: string | undefined = undefined;

/**
 * Retry transport and server failures, but never a 4xx.
 *
 * A missing workflow or instance answers `404`, and a rejected filter or page
 * size answers `400` — none of which a retry can change. Without this the
 * not-found and bad-request views sit behind three backing-off retries before
 * they appear, which reads to an operator as a hung page.
 */
function retryUnlessClientError(failureCount: number, error: Error): boolean {
	if (error instanceof ApiError && error.status >= 400 && error.status < 500) {
		return false;
	}
	return failureCount < 2;
}

/**
 * `GET /workflows` — one row per workflow name, paged by cursor.
 * Returns the accumulated rows across every fetched page.
 */
export function useWorkflows() {
	const query = useInfiniteQuery({
		queryKey: ["workflows"],
		initialPageParam: FIRST_PAGE,
		queryFn: ({ pageParam, signal }) =>
			fetchWorkflows({ cursor: pageParam }, signal),
		getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
		retry: retryUnlessClientError,
	});

	const rows: WorkflowRow[] =
		query.data?.pages.flatMap((page) =>
			page.items.map((i) => toWorkflowRow(i)),
		) ?? [];

	return { ...query, rows };
}

/**
 * `GET /workflows/:name` + `GET /workflows/:name/deployments` — the detail
 * screen needs both, so they are fetched together and assembled into one view
 * model. Either endpoint 404ing means the workflow does not exist, which the
 * route renders as its not-found state.
 */
export function useWorkflowDetail(name: string) {
	return useQuery<WorkflowDetail>({
		queryKey: ["workflow", name],
		queryFn: async ({ signal }) => {
			const [versions, deployments] = await Promise.all([
				fetchWorkflowVersions(name, {}, signal),
				fetchWorkflowDeployments(name, {}, signal),
			]);
			return toWorkflowDetail(name, versions.items, deployments.items);
		},
		retry: retryUnlessClientError,
	});
}

/**
 * `GET /instances` — filtered server-side. The filters are part of the query
 * key, so changing a chip refetches rather than filtering the current page in
 * the browser (which would only ever filter the rows already loaded).
 */
export function useInstances(filters: InstanceFilters) {
	const query = useInfiniteQuery({
		queryKey: ["instances", filters],
		initialPageParam: FIRST_PAGE,
		queryFn: ({ pageParam, signal }) =>
			fetchInstances(filters, { cursor: pageParam }, signal),
		getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
		retry: retryUnlessClientError,
	});

	const rows: InstanceRow[] =
		query.data?.pages.flatMap((page) =>
			page.items.map((i) => toInstanceRow(i)),
		) ?? [];

	return { ...query, rows };
}

/**
 * `GET /instances/:id` + `GET /instances/:id/tasks` — the header and the task
 * timeline are one screen, so they are fetched together. The task events also
 * feed the header's derived counts, which is why they cannot be split into a
 * separate query without the header lagging behind.
 */
export function useInstanceDetail(id: string) {
	return useQuery<InstanceDetail>({
		queryKey: ["instance", id],
		queryFn: async ({ signal }) => {
			const [instance, tasks] = await Promise.all([
				fetchInstance(id, signal),
				fetchInstanceTasks(id, {}, signal),
			]);
			return toInstanceDetail(instance, tasks.items);
		},
		retry: retryUnlessClientError,
	});
}
