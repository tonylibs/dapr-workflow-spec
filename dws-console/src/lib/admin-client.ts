/**
 * Typed fetch client for the `dws-admin` read API.
 *
 * Every request in the console goes through here so the base URL, query-param
 * encoding, and error shape are defined in exactly one place. Endpoint-specific
 * calls live in `admin-hooks.ts`; DTO translation lives in `admin-adapters.ts`.
 */

import type {
	DeploymentDto,
	InstanceDetailDto,
	InstanceSummaryDto,
	Page,
	TaskEventDto,
	WorkflowSummaryDto,
	WorkflowVersionDto,
} from "./admin-types";

/** Base URL default when `VITE_DWS_ADMIN_URL` is unset: same-origin (a dev proxy or an ingress in front of both). */
const DEFAULT_BASE_URL = "";

/** Mirrors `dws-admin`'s `MAX_LIMIT` — the service rejects anything larger with 400. */
export const MAX_LIMIT = 100;

/** Page size for the paged list routes. */
export const LIST_LIMIT = 20;

/**
 * Builds an absolute (or same-origin) URL for a `dws-admin` path.
 * Never hardcode a host anywhere else — configure `VITE_DWS_ADMIN_URL` instead.
 */
export function adminUrl(path: string): string {
	const base = import.meta.env.VITE_DWS_ADMIN_URL ?? DEFAULT_BASE_URL;
	return `${base.replace(/\/$/, "")}${path}`;
}

/** A non-2xx response from `dws-admin`, carrying the status so callers can special-case 404/400. */
export class ApiError extends Error {
	readonly status: number;

	constructor(status: number, message: string) {
		super(message);
		this.name = "ApiError";
		this.status = status;
	}
}

/** Query-string values a read endpoint accepts. `undefined` entries are omitted. */
type QueryParams = Record<string, string | number | undefined>;

function withParams(path: string, params?: QueryParams): string {
	if (!params) return path;
	const search = new URLSearchParams();
	for (const [key, value] of Object.entries(params)) {
		if (value !== undefined) search.set(key, String(value));
	}
	const qs = search.toString();
	return qs ? `${path}?${qs}` : path;
}

/** GETs a JSON document, throwing `ApiError` (with the HTTP status) on any non-2xx response. */
export async function getJson<T>(
	path: string,
	params?: QueryParams,
	signal?: AbortSignal,
): Promise<T> {
	const url = adminUrl(withParams(path, params));
	const response = await fetch(url, {
		headers: { Accept: "application/json" },
		signal,
	});

	if (!response.ok) {
		throw new ApiError(
			response.status,
			`GET ${path} failed: ${response.status} ${response.statusText}`,
		);
	}

	return (await response.json()) as T;
}

// ── Endpoint calls ────────────────────────────────────────────────────────

/** Shared cursor-pagination arguments for the list endpoints. */
export interface PageParams {
	limit?: number;
	cursor?: string;
}

export function fetchWorkflows(
	{ limit = LIST_LIMIT, cursor }: PageParams = {},
	signal?: AbortSignal,
): Promise<Page<WorkflowSummaryDto>> {
	return getJson("/workflows", { limit, cursor }, signal);
}

export function fetchWorkflowVersions(
	name: string,
	{ limit = MAX_LIMIT, cursor }: PageParams = {},
	signal?: AbortSignal,
): Promise<Page<WorkflowVersionDto>> {
	return getJson(
		`/workflows/${encodeURIComponent(name)}`,
		{ limit, cursor },
		signal,
	);
}

export function fetchWorkflowDeployments(
	name: string,
	{ limit = MAX_LIMIT, cursor }: PageParams = {},
	signal?: AbortSignal,
): Promise<Page<DeploymentDto>> {
	return getJson(
		`/workflows/${encodeURIComponent(name)}/deployments`,
		{ limit, cursor },
		signal,
	);
}

/** Optional, combinable server-side filters on `GET /instances`. */
export interface InstanceFilters {
	workflow?: string;
	status?: string;
}

export function fetchInstances(
	filters: InstanceFilters,
	{ limit = LIST_LIMIT, cursor }: PageParams = {},
	signal?: AbortSignal,
): Promise<Page<InstanceSummaryDto>> {
	return getJson("/instances", { ...filters, limit, cursor }, signal);
}

export function fetchInstance(
	id: string,
	signal?: AbortSignal,
): Promise<InstanceDetailDto> {
	return getJson(`/instances/${encodeURIComponent(id)}`, undefined, signal);
}

export function fetchInstanceTasks(
	id: string,
	{ limit = MAX_LIMIT, cursor }: PageParams = {},
	signal?: AbortSignal,
): Promise<Page<TaskEventDto>> {
	return getJson(
		`/instances/${encodeURIComponent(id)}/tasks`,
		{ limit, cursor },
		signal,
	);
}
