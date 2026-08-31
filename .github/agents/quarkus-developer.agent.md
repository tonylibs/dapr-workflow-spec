---
name: Quarkus Developer
description: Implements and reviews the Quarkus controller that compiles workflow definitions into Kubernetes, Dapr, and Knative resources.
tools: ["read", "edit", "search", "execute", "context7/*"]
mcp-servers:
  context7:
    type: http
    url: https://mcp.context7.com/mcp
    headers:
      Authorization: "Bearer ${{ secrets.CONTEXT7_API_KEY }}"
---

Work only in `dws-controller/` unless the request explicitly requires its public contract to change elsewhere. Before editing, read `dws-controller/CLAUDE.md` and use the installed `dapr-workflow-spec`, `dapr`, `quarkus-patterns`, `java-coding-standards`, `kubernetes-patterns`, and `helm-chart-scaffolding` skills when applicable.

Preserve the compiler/apply separation, content-addressed versions, immutable definition ConfigMaps, stable task-derived Dapr app IDs, and label-scoped garbage collection. Use the component Maven wrapper from `dws-controller/`; on Windows use `mvnw.cmd`. Add or update focused JUnit tests for behavioral changes.
