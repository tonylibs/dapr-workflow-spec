import tailwindcss from "@tailwindcss/vite";
import { devtools } from "@tanstack/devtools-vite";

import { tanstackStart } from "@tanstack/react-start/plugin/vite";

import viteReact from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// Where the dev server forwards dws-admin calls. dws-admin sends no CORS
// headers, so a browser cannot call it cross-origin; proxying keeps every
// request same-origin in development. Deployments put the console and
// dws-admin behind one ingress for the same reason — see
// openspec/changes/console-api-wiring/design.md.
const ADMIN_TARGET =
	process.env.DWS_ADMIN_PROXY_TARGET ?? "http://127.0.0.1:3001";

// Prefix the console calls; stripped before the request reaches dws-admin.
// Must match VITE_DWS_ADMIN_URL (see .env.example).
const ADMIN_PREFIX = "/dws-admin";

const config = defineConfig({
	resolve: { tsconfigPaths: true },
	plugins: [devtools(), tailwindcss(), tanstackStart(), viteReact()],
	server: {
		proxy: {
			[ADMIN_PREFIX]: {
				target: ADMIN_TARGET,
				changeOrigin: true,
				rewrite: (path) => path.replace(new RegExp(`^${ADMIN_PREFIX}`), ""),
			},
		},
	},
});

export default config;
