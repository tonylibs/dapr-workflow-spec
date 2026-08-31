# ECC for Codex CLI

This supplements the root `AGENTS.md` with a repo-local ECC baseline.

## Repo Skill

- Repo-generated Codex skill: `.agents/skills/dapr-workflow-spec/SKILL.md`
- Claude-facing companion skill: `.claude/skills/dapr-workflow-spec/SKILL.md`
- Keep user-specific credentials and private MCPs in `~/.codex/config.toml`, not in this repo.

## MCP Baseline

Treat `.codex/config.toml` as the default ECC-safe baseline for work in this repository.
The generated baseline enables GitHub, Context7, Exa, Memory, Playwright, and Sequential Thinking.

## Multi-Agent Support

- Explorer: read-only evidence gathering
- Reviewer: correctness, security, and regression review
- Docs researcher: API and release-note verification
- Orchestrator: routes DWS work to the component-owning specialist
- Quarkus developer: `dws-controller/`
- Java Spring developer: `dws-orchestrator/`, `dws-step/`
- Go developer: `dws-call-http/`, `dws-call-grpc/`, `dws-run/`
- Node.js developer: `dws-call-openapi/`, `dws-call-asyncapi/`
- .NET developer: `dws-flow/`
- NestJS developer: `dws-admin/`
- Frontend developer: `dws-console/`
- Platform deployment developer: Helm, Kubernetes, Docker, and image-build CI

## Workflow Files

- No dedicated workflow command files were generated for this repo.

Use these workflow files as reusable task scaffolds when the detected repository workflows recur.
