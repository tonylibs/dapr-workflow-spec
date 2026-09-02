/**
 * Typed fetch client for the `dws-admin` read API.
 *
 * Every request in the console goes through here so the base URL, query-param
 * encoding, and error shape are defined in exactly one place. Endpoint-specific
 * calls live in `admin-hooks.ts`; DTO translation lives in `admin-adapters.ts`.
 */

import { z } from "zod";
import { getAccessToken } from "#/lib/oidc";
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

/**
 * Raised when the current OIDC access token cannot be acquired for a request.
 *
 * Distinct from `ApiError`: this never reached the network, so it is not a
 * transport or server failure. Callers (query retry policy, route banners)
 * must treat it the same way they treat a `401` — as a sign-in/session
 * outcome, never as something a retry could fix.
 */
export class AuthenticationError extends Error {
	constructor(cause: unknown) {
		super("Authentication required");
		this.name = "AuthenticationError";
		this.cause = cause;
	}
}

/**
 * Acquires the current bearer token and attaches it to every admin request.
 *
 * This is the one place in `admin-client.ts` allowed to call `getAccessToken`
 * (design D6): every JSON read, the definition write, and both SSE
 * connections route through here (or through `getJson`, which calls this),
 * so a renewed token is always the one sent and none is ever placed in a URL,
 * query key, or persisted state. Caller `Accept`/`Content-Type`, raw bodies,
 * and abort signals pass through unchanged.
 */
async function adminFetch(
	path: string,
	init: RequestInit = {},
	signal?: AbortSignal,
): Promise<Response> {
	let token: string;
	try {
		token = await getAccessToken();
	} catch (cause) {
		throw new AuthenticationError(cause);
	}

	const headers = new Headers(init.headers);
	headers.set("Authorization", `Bearer ${token}`);

	return fetch(adminUrl(path), {
		...init,
		headers,
		signal: signal ?? init.signal,
	});
}

/**
 * Controller apply outcome, returned verbatim through dws-admin's write relay.
 *
 * Parsed rather than cast: a silent shape drift in the controller would
 * otherwise reach the success banner as "Applied undefined (undefined)."
 */
const applyResultSchema = z.object({
	workflow: z.string(),
	versionId: z.string(),
	version: z.string(),
	created: z.boolean(),
});

export type ApplyResult = z.infer<typeof applyResultSchema>;

/** The controller's current flat validation response; source locations are not available yet. */
export type DefinitionSubmission =
	| { kind: "applied"; result: ApplyResult }
	| { kind: "validation-error"; errors: string[] };

/**
 * POSTs raw DSL source to dws-admin's controller relay.
 *
 * The relay preserves the bytes and forwards the current bearer token
 * (acquired here, not passed in by the caller) to the controller-side Dapr
 * middleware; the browser never calls dws-controller.
 */
export async function submitDefinition(
	definition: string,
	signal?: AbortSignal,
): Promise<DefinitionSubmission> {
	const response = await adminFetch(
		"/workflows?dryRun=false",
		{
			method: "POST",
			headers: {
				Accept: "application/json",
				"Content-Type": "application/yaml",
			},
			body: definition,
		},
		signal,
	);

	if (response.status === 400) {
		const payload = (await response.json()) as { errors?: unknown };
		if (
			Array.isArray(payload.errors) &&
			payload.errors.every((error) => typeof error === "string")
		) {
			return { kind: "validation-error", errors: payload.errors };
		}
		// Carry the body: without it an operator hitting this invariant break has
		// nothing to report but the generic message.
		throw new ApiError(
			400,
			`POST /workflows failed: invalid validation response: ${summarize(payload)}`,
		);
	}

	if (!response.ok) {
		throw new ApiError(
			response.status,
			`POST /workflows failed: ${response.status} ${response.statusText}`,
		);
	}

	const parsed = applyResultSchema.safeParse(await response.json());
	if (!parsed.success) {
		throw new ApiError(
			response.status,
			`POST /workflows failed: unexpected apply result: ${parsed.error.message}`,
		);
	}

	return { kind: "applied", result: parsed.data };
}

/** Longest server payload echoed into an `ApiError` message. */
const MAX_PAYLOAD_CHARS = 200;

/** Serializes an unexpected payload for an error message, truncated so a huge body stays readable. */
function summarize(payload: unknown): string {
	const text = JSON.stringify(payload) ?? String(payload);
	return text.length > MAX_PAYLOAD_CHARS
		? `${text.slice(0, MAX_PAYLOAD_CHARS)}…`
		: text;
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
	const url = withParams(path, params);
	const response = await adminFetch(
		url,
		{ headers: { Accept: "application/json" } },
		signal,
	);

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

// ── Live streams (fetch/ReadableStream SSE) ───────────────────────────────
//
// Native `EventSource` cannot set request headers, so it cannot carry the
// bearer token the gated admin streams now require (design D6, roadmap
// Phase 5). This transport reopens the connection as a plain authenticated
// `fetch` and parses the `text/event-stream` body by hand: a line-buffer that
// accumulates `event:`/`data:` fields until a blank line, then dispatches the
// parsed JSON to the matching listener — the same named-event shape the
// `EventSource`-based version used, so `admin-hooks.ts` did not have to change.

/**
 * A live subscription's handle. `close()` is idempotent and must be called on
 * unmount — an open connection left running keeps reconnecting forever.
 */
export interface LiveSubscription {
	close(): void;
}

/** Callbacks shared by both streams. `onOpen` fires on first connect *and* on every reconnect. */
interface LiveHandlers {
	onOpen?: () => void;
	/**
	 * A terminal transport outcome: token acquisition failed, or the stream
	 * responded 401. Unlike the old `EventSource` version, this callback means
	 * the subscription has stopped and will not retry anonymously — it is an
	 * authentication outcome, not a routine notice.
	 */
	onError?: () => void;
}

/**
 * `fetch`/`ReadableStream` exist only in the browser and Node, not during a
 * server render with no such globals. This returns an inert handle instead of
 * throwing during render.
 */
const NO_SUBSCRIPTION: LiveSubscription = { close: () => {} };

/** Backoff schedule for a dropped or ended stream; capped so a long outage still retries periodically. */
const RECONNECT_BASE_DELAY_MS = 1_000;
const RECONNECT_MAX_DELAY_MS = 30_000;

function isAbortError(error: unknown): boolean {
	return error instanceof Error && error.name === "AbortError";
}

/** Reads a decoded SSE byte stream when `TextDecoderStream` is unavailable. */
function decodeChunks(body: ReadableStream<Uint8Array>): AsyncIterable<string> {
	const reader = body.getReader();
	const decoder = new TextDecoder();
	return {
		[Symbol.asyncIterator]() {
			return {
				async next(): Promise<IteratorResult<string>> {
					const { done, value } = await reader.read();
					if (done) return { done: true, value: undefined };
					return {
						done: false,
						value: decoder.decode(value, { stream: true }),
					};
				},
			};
		},
	};
}

/**
 * Parses one connection's `text/event-stream` body, dispatching each
 * complete `event:`/`data:` frame to the matching listener as it arrives.
 * Resolves when the server ends the stream; the caller decides whether that
 * means reconnect (a drop) or stop (a terminal instance closed it).
 */
async function readFrames(
	body: ReadableStream<Uint8Array>,
	listeners: Record<string, (payload: unknown) => void>,
	onFrameError: () => void,
): Promise<void> {
	// `TextDecoderStream`'s DOM typings declare a `BufferSource` writable side,
	// which is wider than `ReadableStream<Uint8Array>`'s `pipeThrough` accepts;
	// the runtime contract is exactly a byte stream in, string stream out.
	const chunks: AsyncIterable<string> =
		typeof TextDecoderStream === "undefined"
			? decodeChunks(body)
			: (body.pipeThrough(
					new TextDecoderStream() as unknown as ReadableWritablePair<
						string,
						Uint8Array
					>,
				) as ReadableStream<string>);

	let buffer = "";
	let eventName: string | undefined;
	let dataLines: string[] = [];

	const dispatch = () => {
		if (eventName !== undefined && dataLines.length > 0) {
			const handle = listeners[eventName];
			if (handle) {
				// A malformed frame must not take the whole subscription down; the
				// next well-formed one still arrives.
				try {
					handle(JSON.parse(dataLines.join("\n")));
				} catch {
					onFrameError();
				}
			}
		}
		eventName = undefined;
		dataLines = [];
	};

	for await (const chunk of chunks) {
		buffer += chunk;
		let newlineAt = buffer.indexOf("\n");
		while (newlineAt !== -1) {
			const line = buffer.slice(0, newlineAt).replace(/\r$/, "");
			buffer = buffer.slice(newlineAt + 1);
			if (line === "") {
				dispatch();
			} else if (line.startsWith("event:")) {
				eventName = line.slice("event:".length).trim();
			} else if (line.startsWith("data:")) {
				dataLines.push(line.slice("data:".length).trimStart());
			}
			// Other fields (`id:`, `retry:`, `:`-comments) are not part of this
			// contract and are ignored rather than rejected.
			newlineAt = buffer.indexOf("\n");
		}
	}
}

/**
 * Opens (and, on drop, reopens) an authenticated `text/event-stream`
 * connection to a `dws-admin` path.
 *
 * Each attempt reacquires the current access token, so a renewal between
 * connections is what the reconnect sends — never a stale copy. A `401`
 * response is treated as terminal (no anonymous retry); any other failure to
 * connect or a stream that simply ends (the server dropped it) schedules a
 * backed-off reconnect. `onOpen` fires after every successful response,
 * before frames are read, which is what lets `admin-hooks.ts` tell a first
 * connect from a reconnect and resync on the latter.
 */
function openStream(
	path: string,
	handlers: LiveHandlers,
	listeners: Record<string, (payload: unknown) => void>,
): LiveSubscription {
	if (typeof fetch === "undefined") return NO_SUBSCRIPTION;

	const controller = new AbortController();
	let closed = false;
	let attempt = 0;

	function close(): void {
		if (closed) return;
		closed = true;
		controller.abort();
	}

	function scheduleReconnect(): void {
		if (closed) return;
		const delay = Math.min(
			RECONNECT_BASE_DELAY_MS * 2 ** attempt,
			RECONNECT_MAX_DELAY_MS,
		);
		attempt += 1;
		setTimeout(() => {
			if (!closed) void connect();
		}, delay);
	}

	async function connect(): Promise<void> {
		if (closed) return;

		let token: string;
		try {
			token = await getAccessToken();
		} catch {
			// Token acquisition failed (signed out / session expired): this is an
			// authentication outcome, not a transport failure, so stop rather than
			// retrying anonymously.
			handlers.onError?.();
			close();
			return;
		}

		let response: Response;
		try {
			response = await fetch(adminUrl(path), {
				headers: {
					Accept: "text/event-stream",
					Authorization: `Bearer ${token}`,
				},
				signal: controller.signal,
			});
		} catch (error) {
			if (closed || isAbortError(error)) return;
			scheduleReconnect();
			return;
		}

		if (response.status === 401) {
			handlers.onError?.();
			close();
			return;
		}

		if (!response.ok || !response.body) {
			if (closed) return;
			scheduleReconnect();
			return;
		}

		attempt = 0;
		handlers.onOpen?.();

		try {
			await readFrames(response.body, listeners, () => handlers.onError?.());
		} catch (error) {
			if (closed || isAbortError(error)) return;
			scheduleReconnect();
			return;
		}

		// The server ended the stream (a drop, not a `close()` call) — reconnect.
		if (!closed) scheduleReconnect();
	}

	void connect();

	return { close };
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
		instance: (payload) =>
			handlers.onInstance(payload as InstanceStatusEventDto),
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
