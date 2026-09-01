# syntax=docker/dockerfile:1

# dws-console is the one component on npm rather than pnpm (it ships a
# package-lock.json), so this image installs with `npm ci` where the other
# Node components use pnpm.

# --- build stage: install all deps, build client + SSR bundles ---
FROM node:24-slim AS build
WORKDIR /src

COPY package.json package-lock.json ./
RUN npm ci

# Vite inlines VITE_* variables into the client bundle at build time, so the
# dws-admin URL is baked into the image rather than read at boot. The default
# is a same-origin path, which is what a deployment putting the console and
# dws-admin behind one ingress wants; override with
# `--build-arg VITE_DWS_ADMIN_URL=https://admin.example` for a split origin
# (dws-admin must then allow that origin via CORS_ORIGINS).
ARG VITE_DWS_ADMIN_URL=/dws-admin
ENV VITE_DWS_ADMIN_URL=$VITE_DWS_ADMIN_URL
ARG VITE_OIDC_ISSUER_URI
ARG VITE_OIDC_CLIENT_ID
ARG VITE_OIDC_SCOPES
ENV VITE_OIDC_ISSUER_URI=$VITE_OIDC_ISSUER_URI
ENV VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
ENV VITE_OIDC_SCOPES=$VITE_OIDC_SCOPES

COPY tsconfig.json tsr.config.json vite.config.ts ./
COPY src ./src
RUN npm run build

# --- prod deps stage: production-only node_modules ---
FROM node:24-slim AS deps
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --omit=dev

# --- runtime stage ---
FROM node:24-slim AS runtime
ENV NODE_ENV=production
WORKDIR /app

COPY --from=deps /src/node_modules ./node_modules
COPY --from=build /src/dist ./dist
# The SSR bundle imports react/@tanstack from node_modules rather than bundling
# them, which is why the production install above ships with the image.
COPY server.js package.json ./

# node:24-slim ships a non-root `node` user.
USER node
EXPOSE 3000
CMD ["node", "server.js"]
