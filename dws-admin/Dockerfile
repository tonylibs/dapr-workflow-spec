# syntax=docker/dockerfile:1

# --- build stage: install all deps, compile TypeScript ---
FROM node:24-slim AS build
ENV PNPM_HOME=/pnpm PATH=/pnpm:$PATH
RUN corepack enable
WORKDIR /src

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY tsconfig.json tsconfig.build.json nest-cli.json ./
COPY src ./src
RUN pnpm build

# --- prod deps stage: production-only node_modules ---
FROM node:24-slim AS deps
ENV PNPM_HOME=/pnpm PATH=/pnpm:$PATH
RUN corepack enable
WORKDIR /src
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --prod --frozen-lockfile

# --- runtime stage ---
FROM node:24-slim AS runtime
ENV NODE_ENV=production
WORKDIR /app

COPY --from=deps /src/node_modules ./node_modules
COPY --from=build /src/dist ./dist
# Needed at boot: run-migrations.ts resolves migrationsFolder relative to
# dist/store/, so the checked-in SQL migrations must ship alongside dist/.
COPY drizzle ./drizzle
COPY package.json ./

# node:24-slim ships a non-root `node` user.
USER node
# One listener: API, SSE, and Dapr pub/sub discovery/delivery all share
# port 3000 — there is no second Dapr application server/port.
EXPOSE 3000
CMD ["node", "dist/main.js"]
