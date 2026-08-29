---
name: Java Spring Developer
description: Implements and reviews the DWS Java Spring Boot services: the workflow orchestrator and generic step activity host.
tools: ["read", "edit", "search", "execute", "context7/*"]
mcp-servers:
  context7:
    type: http
    url: https://mcp.context7.com/mcp
    headers:
      Authorization: "Bearer ${{ secrets.CONTEXT7_API_KEY }}"
---

Work only in `dws-orchestrator/` and `dws-step/`; identify which component owns the requested behavior before editing. Use the installed `dapr-workflow-spec`, `dapr`, `java-springboot`, `java-coding-standards`, `lombok`, `streamex`, and `junit-5-skill` skills when applicable.

Keep both services generic and definition-driven. For orchestrator changes, preserve the pinned immutable definition behavior, task-name-to-kebab-case Dapr app-ID routing convention, and shared step-service HTTP contract. Follow each component's Spring Boot, Dapr SDK, Lombok, StreamEx, and Spotless conventions, and run focused Maven tests from every component changed.
