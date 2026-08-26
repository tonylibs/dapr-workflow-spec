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
	InstanceStatusDeltaDto,
	InstanceStatusEventDto,
	InstanceSummaryDto,
	Page,
	TaskEventDto,
	TaskEventStreamDto,
	WorkflowSummaryDto,
	WorkflowVersionDto,
} from "./admin-types";

/**
 * Base URL default when `VITE_DWS_ADMIN_URL` is unset.
 *
 * Deliberately a prefix rather than "": dws-admin's paths (`/workflows`,
 * `/instances`) are byte-identical to the console's own routes, so calling the
 * bare origin would fetch the console's own HTML and fail in `.json()`. The
 * prefix is what `vite.config.ts` proxies in development, and what a deployment
 * must route to dws-admin.
 */
const DEFAULT_BASE_URL = "/dws-admin";

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

/** Controller apply outcome, returned verbatim through dws-admin's write relay. */
export interface ApplyResult {
	workflow: string;
	versionId: string;
	version: string;
	created: boolean;
}

/** The controller's current flat validation response; source locations are not available yet. */
export type DefinitionSubmission =
	| { kind: "applied"; result: ApplyResult }
	| { kind: "validation-error"; errors: string[] };

/**
 * POSTs raw DSL source to dws-admin's controller relay.
 *
 * The relay preserves the bytes and forwards this bearer token to the
 * controller-side Dapr middleware; the browser never calls dws-controller.
 */
export async function submitDefinition(
	definition: string,
	accessToken: string,
): Promise<DefinitionSubmission> {
	const response = await fetch(adminUrl("/workflows?dryRun=false"), {
		method: "POST",
		headers: {
			Accept: "application/json",
			Authorization: `Bearer ${accessToken}`,
			"Content-Type": "application/yaml",
		},
		body: definition,
	});

	if (response.status === 400) {
		const payload = (await response.json()) as { errors?: unknown };
		if (Array.isArray(payload.errors) && payload.errors.every((error) => typeof error === "string")) {
			return { kind: "validation-error", errors: payload.errors };
		}
		throw new ApiError(400, "POST /workflows failed: invalid validation response");
	}

	if (!response.ok) {
		throw new ApiError(
			response.status,
			`POST /workflows failed: ${response.status} ${response.statusText}`,
		);
	}

	return { kind: "applied", result: (await response.json()) as ApplyResult };
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

// ── Live streams (SSE) ────────────────────────────────────────────────────

/**
 * A live subscription's handle. `close()` is idempotent and must be called on
 * unmount — an `EventSource` left open keeps reconnecting forever.
 */
export interface LiveSubscription {
	close(): void;
}

/** Callbacks shared by both streams. `onOpen` fires on first connect *and* on every reconnect. */
interface LiveHandlers {
	onOpen?: () => void;
	/** A transport error. `EventSource` retries on its own, so this is a notice, not a failure. */
	onError?: () => void;
}

/**
 * `EventSource` exists only in the browser. The console server-renders, so a
 * subscription helper called outside an effect would throw during SSR; this
 * returns an inert handle instead of blowing up the render.
 */
const NO_SUBSCRIPTION: LiveSubscription = { close: () => {} };

function openStream(
	path: string,
	handlers: LiveHandlers,
	listeners: Record<string, (payload: unknown) => void>,
): LiveSubscription {
	if (typeof EventSource === "undefined") return NO_SUBSCRIPTION;

	const source = new EventSource(adminUrl(path));
	if (handlers.onOpen) source.addEventListener("open", handlers.onOpen);
	if (handlers.onError) source.addEventListener("error", handlers.onError);

	for (const [name, handle] of Object.entries(listeners)) {
		source.addEventListener(name, (event) => {
			// A malformed frame must not take the whole subscription down; the
			// next well-formed one still arrives.
			try {
				handle(JSON.parse((event as MessageEvent<string>).data));
			} catch {
				handlers.onError?.();
			}
		});
	}

	return { close: () => source.close() };
}

/**
 * `GET /instances/:id/events` — status changes and task events for one
 * instance. dws-admin ends this stream once the instance reaches a terminal
 * status; the caller must `close()` on seeing that status, because a browser
 * treats a server-ended stream as a disconnect and would otherwise reconnect
 * to it indefinitely.
 */
export function subscribeToInstance(
	id: string,
	handlers: LiveHandlers & {
		onInstance: (event: InstanceStatusEventDto) => void;
		onTask: (event: TaskEventStreamDto) => void;
	},
): LiveSubscription {
	return openStream(`/instances/${encodeURIComponent(id)}/events`, handlers, {
		instance: (payload) => handlers.onInstance(payload as InstanceStatusEventDto),
		task: (payload) => handlers.onTask(payload as TaskEventStreamDto),
	});
}

/** `GET /instances/events` — status deltas across every instance. Never ends on its own. */
export function subscribeToInstanceStatuses(
	handlers: LiveHandlers & {
		onStatus: (delta: InstanceStatusDeltaDto) => void;
	},
): LiveSubscription {
	return openStream("/instances/events", handlers, {
		instance: (payload) => handlers.onStatus(payload as InstanceStatusDeltaDto),
	});
}
