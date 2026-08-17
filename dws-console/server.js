/**
 * Production server entry.
 *
 * `vite build` emits two halves and no way to run them: `dist/server/server.js`
 * exports a web `fetch` handler (it binds no port), and `dist/client/` holds the
 * static assets. This module is the Node host that joins them — assets first,
 * everything else server-rendered. TanStack Start ships no node-server target in
 * this version, so a deployment has to supply this itself.
 *
 * Used by the container image (see Dockerfile). `vite dev`/`vite preview` do
 * their own serving and never load this file.
 */

import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import { createServer } from "node:http";
import { join, normalize, sep } from "node:path";
import { Readable } from "node:stream";
import { fileURLToPath } from "node:url";

import handler from "./dist/server/server.js";

// No trailing separator: the containment check below appends one itself.
const CLIENT_DIR = fileURLToPath(new URL("./dist/client", import.meta.url));
const PORT = Number(process.env.PORT ?? 3000);
const HOST = process.env.HOST ?? "0.0.0.0";

const MIME_TYPES = new Map(
	Object.entries({
		".css": "text/css; charset=utf-8",
		".gif": "image/gif",
		".html": "text/html; charset=utf-8",
		".ico": "image/x-icon",
		".jpeg": "image/jpeg",
		".jpg": "image/jpeg",
		".js": "text/javascript; charset=utf-8",
		".json": "application/json; charset=utf-8",
		".map": "application/json; charset=utf-8",
		".mjs": "text/javascript; charset=utf-8",
		".png": "image/png",
		".svg": "image/svg+xml",
		".txt": "text/plain; charset=utf-8",
		".webp": "image/webp",
		".woff": "font/woff",
		".woff2": "font/woff2",
	}),
);

function contentType(path) {
	const dot = path.lastIndexOf(".");
	return (
		MIME_TYPES.get(dot === -1 ? "" : path.slice(dot).toLowerCase()) ??
		"application/octet-stream"
	);
}

/**
 * Resolves a URL path inside `dist/client`, or null if it escapes the directory.
 * Vite emits hashed filenames under `/assets/`, so those are safe to cache
 * forever; anything else at the root (favicon, robots.txt) is revalidated.
 */
function resolveAsset(pathname) {
	let decoded;
	try {
		decoded = decodeURIComponent(pathname);
	} catch {
		return null; // Malformed percent-encoding.
	}
	if (decoded.includes("\0")) return null;

	const file = join(CLIENT_DIR, normalize(decoded));
	// normalize() collapses `..`, so a path that climbs out lands outside the
	// prefix and is rejected here rather than reaching the filesystem.
	return file.startsWith(CLIENT_DIR + sep) || file === CLIENT_DIR ? file : null;
}

async function serveAsset(request, response) {
	const { pathname } = new URL(request.url ?? "/", "http://localhost");
	const file = resolveAsset(pathname);
	if (!file) return false;

	let stats;
	try {
		stats = await stat(file);
	} catch {
		return false;
	}
	if (!stats.isFile()) return false;

	response.writeHead(200, {
		"Content-Type": contentType(file),
		"Content-Length": stats.size,
		"Cache-Control": pathname.startsWith("/assets/")
			? "public, max-age=31536000, immutable"
			: "public, max-age=0, must-revalidate",
	});

	if (request.method === "HEAD") {
		response.end();
		return true;
	}

	createReadStream(file).pipe(response);
	return true;
}

function toWebRequest(request) {
	const url = new URL(
		request.url ?? "/",
		`http://${request.headers.host ?? "localhost"}`,
	);

	const headers = new Headers();
	for (const [name, value] of Object.entries(request.headers)) {
		if (value === undefined) continue;
		for (const entry of Array.isArray(value) ? value : [value]) {
			headers.append(name, entry);
		}
	}

	const hasBody = request.method !== "GET" && request.method !== "HEAD";
	return new Request(url, {
		method: request.method,
		headers,
		body: hasBody ? Readable.toWeb(request) : undefined,
		// Required by undici whenever a stream body is supplied.
		duplex: hasBody ? "half" : undefined,
	});
}

async function writeWebResponse(webResponse, response) {
	const headers = Object.fromEntries(webResponse.headers);
	// Set-Cookie is the one header that legitimately repeats, and folding it
	// into a single comma-joined value corrupts it.
	delete headers["set-cookie"];
	response.writeHead(webResponse.status, headers);
	for (const cookie of webResponse.headers.getSetCookie?.() ?? []) {
		response.appendHeader("Set-Cookie", cookie);
	}

	if (!webResponse.body) {
		response.end();
		return;
	}
	await new Promise((resolve, reject) => {
		Readable.fromWeb(webResponse.body).pipe(response).on("finish", resolve).on("error", reject);
	});
}

const server = createServer(async (request, response) => {
	try {
		// Liveness/readiness for the container; deliberately not a route so it
		// answers even if the app itself fails to render.
		if (request.url === "/healthz") {
			response.writeHead(200, { "Content-Type": "text/plain; charset=utf-8" });
			response.end("ok");
			return;
		}

		if (await serveAsset(request, response)) return;

		await writeWebResponse(await handler.fetch(toWebRequest(request)), response);
	} catch (error) {
		console.error("Request failed:", error);
		if (!response.headersSent) {
			response.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" });
		}
		response.end("Internal Server Error");
	}
});

server.listen(PORT, HOST, () => {
	console.log(`dws-console listening on http://${HOST}:${PORT}`);
});

// Kubernetes sends SIGTERM; without this the container waits out its grace
// period on every rollout.
for (const signal of ["SIGTERM", "SIGINT"]) {
	process.on(signal, () => server.close(() => process.exit(0)));
}
