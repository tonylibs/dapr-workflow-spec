## Context

`charts/dws` already pulls in two upstream charts as conditional dependencies (`Chart.yaml`):
`postgresql` (`condition: postgresql.enabled`) and `dapr` (`condition: dapr.enabled`). Both
follow the same shape — an `oci://`/https dependency entry, a top-level values block matching
the subchart's own name, and chart-owned templates (`templates/admin/secret.yaml`,
`templates/dapr-ready-hook.yaml`) that read both the subchart's rendered names and sibling
values. `_helpers.tpl` defines one `dws.<component>.fullname` helper per component
(`dws.postgres.fullname`, `dws.admin.fullname`, …), used both by chart-owned templates and by
cross-references to subchart-rendered resource names (`dws.postgres.host`). This change adds
Dex (`dex-idp`, repo `dexidp/helm-charts`) the same way.

The `dex-idp` chart exposes a single free-form `config` values key that is dumped directly as
Dex's own YAML config (schema at `https://dexidp.io/docs/`) — there is no structured
`staticClients`/`staticPasswords` schema in the subchart's own `values.yaml` beyond that
passthrough. Everything Dex-specific in this change (`staticClients`, `staticPasswords`,
`enablePasswordDB`) is therefore written under `dex.config.*` in `charts/dws/values.yaml`,
flowing straight into the subchart as-is — the same pattern already used for `postgresql.auth.*`
flowing into the Bitnami subchart.

See `proposal.md` for motivation; see `specs/helm-dex-idp/spec.md` for the behavior contract.

## Goals / Non-Goals

**Goals:**
- Reuse the existing conditional-dependency, `<component>.fullname` helper, and
  `existingSecret`-override patterns exactly, rather than inventing new chart conventions for
  Dex specifically.
- Keep the bootstrap-admin mechanism entirely template-side (no Job, no init container, no
  external script) per the roadmap's explicit constraint.
- Leave `dex.enabled=false` (default) fully backward-compatible: zero new rendered resources,
  zero new `NOTES.txt` content.

**Non-Goals:**
- Anything from Phase 1 onward in `docs/roadmaps/dws-auth.md` — console login code, Dapr
  `bearer`/role middleware, the admin gateway, Dex's gRPC user-management API. This change stops
  at "Dex is installable and has one working login."
- Supporting non-static Dex connectors (LDAP/SAML/upstream OIDC). `staticPasswords` is
  explicitly a dev/quickstart shape per the roadmap; swapping it for a real connector is a
  separate, later change.
- Validating or rotating the bootstrap admin password after its initial generation — rotation,
  if ever needed, is an operator-driven `kubectl delete secret` + `helm upgrade` action, not a
  feature this change builds.

## Decisions

**D1 — One new `dws.dex.fullname` helper, mirroring `dws.postgres.fullname`/
`dws.admin.fullname`.** `_helpers.tpl` gets `dws.dex.fullname` (`<release>-dex`, following the
existing `printf "%s-<component>" (include "dws.fullname" .)` pattern) plus a
`dws.dex.selectorLabels`/`dws.dex.adminCredentials.fullname` (`<dws.dex.fullname>-admin-
credentials`) as needed by the Secret template and `NOTES.txt`. Chosen over hardcoding the name
inline in each template so every Dex-owned resource name derives from one source, matching how
`dws.admin.fullname` is reused across `templates/admin/*.yaml`.

**D2 — Bootstrap-admin Secret is a chart-owned template outside the `dex-idp` subchart, not
subchart-templated.** The subchart only renders Dex's own Deployment/Service/ConfigMap from
`dex.config`; it has no concept of "also mint me a credentials Secret." A new
`charts/dws/templates/dex/admin-secret.yaml` (parallel to `templates/admin/secret.yaml`) owns:
- Generating the password: `{{- $existing := lookup "v1" "Secret" (include "dws.namespace" .)
  (include "dws.dex.adminCredentials.fullname" .) }}` — if found, reuse
  `$existing.data.password | b64dec`; if not, `randAlphaNum 20`.
- Rendering `stringData: { email: ..., password: ... }`.

This mirrors `templates/admin/secret.yaml`'s existing `existingSecret`-guarded shape (`{{- if
and ... (not .Values.dex.adminUser.existingSecret) }}`), extended with the `lookup` guard for
upgrade-stability, which the admin DB Secret doesn't need (that value is operator-literal, not
generated).

**D3 — The bcrypt hash is computed inline in the `dex.config.staticPasswords` values block via a
named template, not duplicated between the Secret and the Dex config.** Rather than each of the
Secret template and the `dex.config` block independently calling `lookup`/`randAlphaNum`
(risking the two diverging, e.g. Secret stores password A but Dex is configured with a hash of
password B), a single named template (e.g. `dws.dex.adminPassword`, defined in `_helpers.tpl`)
encapsulates the lookup-guarded generation and is called once per render pass from both the
`staticPasswords` block (wrapped in `bcrypt`) and the admin-credentials Secret (plaintext).
Because Helm template evaluation is deterministic per values/lookup input within a single
render, both call sites resolve to the same generated (or reused) password.

**D4 — `dex.enabled` defaults to `false`.** Unlike `postgresql`/`dapr` (both default `true`,
since the chart's core admin/controller stack needs a database and Dapr to function at all), Dex
is net-new optional infrastructure with no existing consumer yet (Phase 1's console login lands
in a later change) — installing it by default would deploy an unused IdP. Revisit the default
once Phase 1 wires the console to it.

**D5 — `NOTES.txt` is new; gate its Dex section on `.Values.dex.enabled`.** No `NOTES.txt`
exists today. Rather than scoping it to Dex alone, it's written as the chart's general
post-install notes file (the standard Helm convention), with the credential-retrieval block
conditionally rendered — future non-Dex notes can be added to the same file without another
migration.

## Risks / Trade-offs

- **[Risk] `lookup` returns nothing during `helm template`/`--dry-run` (no live cluster to query),
  so a fresh `helm template` always looks like a first install** → Mitigation: this is standard,
  documented Helm `lookup` behavior; the spec's acceptance scenarios rely on `helm install`/real
  dry-run against a cluster (per the requirement's own `helm upgrade` scenario), not offline
  `helm template`, to prove the reuse path. `helm template` is only used to prove *rendering*
  (Secret/Deployment/NOTES shape), not the reuse guarantee.
- **[Risk] `dex-idp` subchart's `config` values shape is a free-form passthrough with no schema
  validation from Helm** → Mitigation: `helm lint`/`helm template` in the acceptance criteria
  catches YAML-shape mistakes; a malformed `staticClients`/`staticPasswords` block would surface
  as a Dex pod CrashLoop at runtime, not a template-time failure — acceptable since Phase 0's
  acceptance criteria are template/dry-run-level, not a live-login test.
- **[Trade-off] Bootstrap admin is a single static password, not rotatable without manual Secret
  deletion** → Accepted per roadmap §2a; this is explicitly a bootstrap/dev mechanism, not a
  production credential-management story.
