# syntax=docker/dockerfile:1

# --- build stage: install all deps, compile TypeScript ---
FROM node:24-slim AS build
ENV PNPM_HOME=/pnpm PATH=/pnpm:$PATH
RUN corepack enable
WORKDIR /src

# Install with a frozen lockfile; pnpm-workspace.yaml allows the node-jq and
# esbuild build scripts to run (node-jq fetches the jq binary for this platform).
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --frozen-lockfile

COPY tsconfig.json ./
COPY src ./src
RUN pnpm build

# --- prod deps stage: production-only node_modules (with the linux jq binary) ---
FROM node:24-slim AS deps
ENV PNPM_HOME=/pnpm PATH=/pnpm:$PATH
RUN corepack enable
WORKDIR /src
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN pnpm install --prod --frozen-lockfile

# --- runtime stage ---
FROM node:24-slim AS runtime
ENV NODE_ENV=production
WORKDIR /app

COPY --from=deps /src/node_modules ./node_modules
COPY --from=build /src/dist ./dist
COPY package.json ./

# node:24-slim ships a non-root `node` user.
USER node
EXPOSE 8080
CMD ["node", "dist/index.js"]
