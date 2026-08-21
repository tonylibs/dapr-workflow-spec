import tailwindcss from "@tailwindcss/vite";
import { devtools } from "@tanstack/devtools-vite";

import { tanstackStart } from "@tanstack/react-start/plugin/vite";

import viteReact from "@vitejs/plugin-react";
import { oidcSpa } from "oidc-spa/vite-plugin";
import { defineConfig } from "vite";

// Where the dev server forwards dws-admin calls. Proxying keeps every request
// same-origin in development, which sidesteps CORS entirely; dws-admin also
// serves CORS headers (see its CORS_ORIGINS), so pointing VITE_DWS_ADMIN_URL
// straight at it works too.
const ADMIN_TARGET =
	process.env.DWS_ADMIN_PROXY_TARGET ?? "http://127.0.0.1:3001";

// Prefix the console calls; stripped before the request reaches dws-admin.
// Must match VITE_DWS_ADMIN_URL (see .env.example).
const ADMIN_PREFIX = "/dws-admin";

const config = defineConfig({
	resolve: { tsconfigPaths: true },
	// oidcSpa() wires OIDC into the TanStack Start client/server entries so login
	// works with SSR (see src/lib/oidc.ts). It declares enforce: "pre", so Vite
	// orders it ahead of tanstackStart() wherever it sits in this list.
	plugins: [devtools(), tailwindcss(), oidcSpa(), tanstackStart(), viteReact()],
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
