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

import {
	type InfiniteData,
	useInfiniteQuery,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { useEffect } from "react";
import { useOidc } from "#/lib/oidc";
import {
	toInstanceDetail,
	toInstanceRow,
	toWorkflowDetail,
	toWorkflowRow,
} from "./admin-adapters";
import {
	ApiError,
	AuthenticationError,
	fetchInstance,
	fetchInstances,
	fetchInstanceTasks,
	fetchWorkflowDeployments,
	fetchWorkflows,
	fetchWorkflowVersions,
	type InstanceFilters,
	subscribeToInstance,
	subscribeToInstanceStatuses,
} from "./admin-client";
import {
	applyInstanceStatus,
	applyStatusDelta,
	applyTaskEvent,
	type InstanceDetailData,
	isTerminalInstanceStatus,
} from "./admin-live";
import type { InstanceSummaryDto, Page } from "./admin-types";
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
 * Retry transport and server failures, but never a 4xx or an authentication
 * outcome.
 *
 * A missing workflow or instance answers `404`, and a rejected filter or page
 * size answers `400` — none of which a retry can change. Without this the
 * not-found and bad-request views sit behind three backing-off retries before
 * they appear, which reads to an operator as a hung page. A `401` (also a
 * 4xx, so already covered) and a failure to acquire a token at all
 * (`AuthenticationError`, which never reached the network) are both
 * sign-in/session outcomes, not transport failures a retry could fix.
 */
function retryUnlessClientError(failureCount: number, error: Error): boolean {
	if (error instanceof AuthenticationError) return false;
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
	const oidc = useOidc();
	const query = useInfiniteQuery({
		queryKey: ["workflows"],
		initialPageParam: FIRST_PAGE,
		queryFn: ({ pageParam, signal }) =>
			fetchWorkflows({ cursor: pageParam }, signal),
		getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
		retry: retryUnlessClientError,
		enabled: oidc.isUserLoggedIn === true,
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
	const oidc = useOidc();
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
		enabled: oidc.isUserLoggedIn === true,
	});
}

/**
 * Every workflow name, for the instance list's workflow filter. Drains the
 * pages: a filter that only offers the first page silently hides workflows.
 */
export function useWorkflowNames() {
	const oidc = useOidc();
	return useQuery<string[]>({
		queryKey: ["workflow-names"],
		queryFn: () =>
			fetchAllPages((cursor) => fetchWorkflows({ cursor }, undefined)).then(
				(items) => items.map((i) => i.name),
			),
		retry: retryUnlessClientError,
		enabled: oidc.isUserLoggedIn === true,
	});
}

/** Query keys shared with the live-update hooks, which patch these caches directly. */
export const instancesKey = (filters: InstanceFilters) =>
	["instances", filters] as const;
export const instanceKey = (id: string) => ["instance", id] as const;

/**
 * `GET /instances` — filtered server-side. The filters are part of the query
 * key, so changing a chip refetches rather than filtering the current page in
 * the browser (which would only ever filter the rows already loaded).
 */
export function useInstances(filters: InstanceFilters) {
	const oidc = useOidc();
	const query = useInfiniteQuery({
		queryKey: instancesKey(filters),
		initialPageParam: FIRST_PAGE,
		queryFn: ({ pageParam, signal }) =>
			fetchInstances(filters, { cursor: pageParam }, signal),
		getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
		retry: retryUnlessClientError,
		enabled: oidc.isUserLoggedIn === true,
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
	const oidc = useOidc();
	return useQuery<InstanceDetailData, Error, InstanceDetail>({
		queryKey: instanceKey(id),
		queryFn: async ({ signal }) => {
			const [instance, tasks] = await Promise.all([
				fetchInstance(id, signal),
				fetchAllPages((cursor) => fetchInstanceTasks(id, { cursor }, signal)),
			]);
			return { instance, tasks };
		},
		// The cache holds the raw DTOs and the view model is derived here, so a
		// pushed event can be merged into the DTOs and re-adapted. Caching the
		// assembled view model would leave nothing to merge into.
		select: ({ instance, tasks }) => toInstanceDetail(instance, tasks),
		retry: retryUnlessClientError,
		enabled: oidc.isUserLoggedIn === true,
	});
}

// ── Live updates ──────────────────────────────────────────────────────────

/**
 * Subscribes the open instance to `GET /instances/:id/events` and merges what
 * arrives into the detail cache, so a running instance's header and timeline
 * update without the operator hitting "Refresh".
 *
 * Only subscribes while `isRunning` — a terminal instance can produce nothing
 * further — and closes as soon as a pushed status is terminal. That close is
 * required, not just tidy: dws-admin ends the stream at the same moment, and a
 * browser treats a server-ended stream as a dropped connection and would
 * reconnect to it forever.
 *
 * Events are merged into the cache rather than triggering `invalidateQueries`,
 * which would refetch the whole instance per event — the polling this replaces.
 */
export function useInstanceLiveUpdates(id: string, isRunning: boolean) {
	const queryClient = useQueryClient();
	const oidc = useOidc();
	const isSignedIn = oidc.isUserLoggedIn === true;

	useEffect(() => {
		if (!isRunning || !isSignedIn) return;

		let closed = false;
		// The first connect needs no resync — the query's own fetch is that GET.
		// Every later `open` is a reconnect, and the stream carries no history,
		// so whatever happened while disconnected is only recoverable by refetching.
		let connected = false;
		const subscription = subscribeToInstance(id, {
			onOpen: () => {
				if (connected) {
					queryClient.invalidateQueries({ queryKey: instanceKey(id) });
				}
				connected = true;
			},
			onInstance: (event) => {
				queryClient.setQueryData<InstanceDetailData>(instanceKey(id), (prev) =>
					applyInstanceStatus(prev, event),
				);
				if (isTerminalInstanceStatus(event.status) && !closed) {
					closed = true;
					subscription.close();
				}
			},
			onTask: (event) => {
				queryClient.setQueryData<InstanceDetailData>(instanceKey(id), (prev) =>
					applyTaskEvent(prev, event),
				);
			},
			// A dropped stream leaves the last fetched data on screen and the
			// manual "Refresh" working; EventSource retries on its own.
			onError: () => {},
		});

		return () => {
			closed = true;
			subscription.close();
		};
	}, [id, isRunning, isSignedIn, queryClient]);
}

/**
 * Subscribes the instance list to `GET /instances/events` and patches the
 * status and end time of rows already loaded. Instances not on the page are
 * ignored: inserting them would reorder the list under the operator, and the
 * page they belong to delivers them anyway.
 */
export function useInstanceListLiveUpdates(filters: InstanceFilters) {
	const queryClient = useQueryClient();
	const oidc = useOidc();
	const isSignedIn = oidc.isUserLoggedIn === true;
	// The key is an object literal, so depend on its content rather than its
	// identity — otherwise every render resubscribes.
	const filtersKey = JSON.stringify(filters);

	useEffect(() => {
		if (!isSignedIn) return;
		const key = instancesKey(JSON.parse(filtersKey) as InstanceFilters);

		// As above: resync on reconnect, not on the first connect.
		let connected = false;
		const subscription = subscribeToInstanceStatuses({
			onOpen: () => {
				if (connected) queryClient.invalidateQueries({ queryKey: key });
				connected = true;
			},
			onStatus: (delta) => {
				queryClient.setQueryData<InfiniteData<Page<InstanceSummaryDto>>>(
					key,
					(prev) => applyStatusDelta(prev, delta),
				);
			},
			onError: () => {},
		});

		return () => subscription.close();
	}, [filtersKey, isSignedIn, queryClient]);
}
