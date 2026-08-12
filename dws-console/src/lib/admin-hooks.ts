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
import type { Page } from "./admin-types";
import type {
	InstanceDetail,
	InstanceRow,
	WorkflowDetail,
	WorkflowRow,
} from "./mock-data";

/** Cursor of the first page: `undefined` lets the service pick its own start. */
const FIRST_PAGE: string | undefined = undefined;

/**
 * Drains every page of a cursor-paginated endpoint.
 *
 * The detail screens need whole collections, not a first page: a truncated task
 * list silently understates the header's task/failure/retry counts, and a
 * truncated version list would mislabel which version is current. Guarded so a
 * server that keeps returning a cursor cannot spin forever.
 */
async function fetchAllPages<T>(
	fetchPage: (cursor?: string) => Promise<Page<T>>,
	maxPages = 20,
): Promise<T[]> {
	const items: T[] = [];
	let cursor: string | undefined;
	for (let i = 0; i < maxPages; i++) {
		const page = await fetchPage(cursor);
		items.push(...page.items);
		if (!page.nextCursor) break;
		cursor = page.nextCursor;
	}
	return items;
}

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
				fetchAllPages((cursor) =>
					fetchWorkflowVersions(name, { cursor }, signal),
				),
				fetchAllPages((cursor) =>
					fetchWorkflowDeployments(name, { cursor }, signal),
				),
			]);
			return toWorkflowDetail(name, versions, deployments);
		},
		retry: retryUnlessClientError,
	});
}

/**
 * Every workflow name, for the instance list's workflow filter. Drains the
 * pages: a filter that only offers the first page silently hides workflows.
 */
export function useWorkflowNames() {
	return useQuery<string[]>({
		queryKey: ["workflow-names"],
		queryFn: () =>
			fetchAllPages((cursor) => fetchWorkflows({ cursor }, undefined)).then(
				(items) => items.map((i) => i.name),
			),
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
				fetchAllPages((cursor) => fetchInstanceTasks(id, { cursor }, signal)),
			]);
			return toInstanceDetail(instance, tasks);
		},
		retry: retryUnlessClientError,
	});
}
