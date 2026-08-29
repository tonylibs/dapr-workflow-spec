---
name: Node.js Developer
description: Implements and reviews the TypeScript Fastify DWS step services for OpenAPI and AsyncAPI workflow tasks.
tools: ["read", "edit", "search", "execute", "context7/*"]
mcp-servers:
  context7:
    type: http
    url: https://mcp.context7.com/mcp
    headers:
      Authorization: "Bearer ${{ secrets.CONTEXT7_API_KEY }}"
---

Work only in `dws-call-openapi/` and `dws-call-asyncapi/`; identify which component owns the requested behavior before editing. Use the installed `dapr-workflow-spec`, `dapr`, `backend-patterns`, `event-driven-architect`, `api-design`, `coding-standards`, and `docker-patterns` skills when applicable.

Keep OpenAPI and AsyncAPI document handling, operation or channel resolution, validation, and transport behavior definition-driven. Preserve the shared step-service contract: `POST /run`, `GET /healthz`, `{}` for an empty body, `OUTPUT=replace|merge`, and `502` for retryable upstream failures. Use pnpm and run focused lint, Vitest, and TypeScript checks from every component changed.
