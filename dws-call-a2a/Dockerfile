# syntax=docker/dockerfile:1

# --- builder stage: resolve + install the pinned, production-only dependency set ---
FROM python:3.13-slim AS builder
COPY --from=ghcr.io/astral-sh/uv:0.11.7 /uv /uvx /bin/

ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PROJECT_ENVIRONMENT=/app/.venv

WORKDIR /app

# Dependencies first, cached independently of source changes.
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

# Then the project itself (a separate layer so editing src/ doesn't invalidate
# the dependency-install layer above).
COPY src ./src
RUN uv sync --frozen --no-dev

# --- runtime stage ---
FROM python:3.13-slim AS runtime
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY --from=builder /app/.venv /app/.venv
COPY --from=builder /app/src /app/src

# python:3.13-slim ships no non-root user by default -- create one.
RUN groupadd --system --gid 1000 dws && \
    useradd --system --uid 1000 --gid dws --no-create-home dws
USER dws

EXPOSE 8080
CMD ["python", "-m", "dws_call_a2a"]
