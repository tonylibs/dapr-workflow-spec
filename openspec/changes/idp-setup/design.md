## Context

`charts/dws` already pulls in two upstream charts as conditional dependencies (`Chart.yaml`):
`postgresql` (`condition: postgresql.enabled`) and `dapr` (`condition: dapr.enabled`). Both
follow the same shape — an `oci://`/https dependency entry, a top-level values block matching
the subchart's own name, and chart-owned templates (`templates/admin/secret.yaml`,
`templates/dapr-ready-hook.yaml`) that read both the subchart's rendered names and sibling
values. `_helpers.tpl` defines one `dws.<component>.fullname` helper per component
(`dws.postgres.fullname`, `dws.admin.fullname`, …), used both by chart-owned templates and by
cross-references to subchart-rendered resource names (`dws.postgres.host`). This change adds
Dex (chart name `dex`, repo `dexidp/helm-charts`, version `0.24.1`) the same way.

The `dex` chart exposes a single free-form `config` values key that is dumped directly (via
`toYaml`) into a Secret it creates itself when `configSecret.create` is `true` (the default),
which its Deployment mounts as `/etc/dex/config.yaml`. Because `values.yaml` is plain data, not
a template, nothing computed at render time (a generated password, a `bcrypt` hash) can be
injected through that passthrough — sprig/template functions only run inside `.tpl` files. The
chart also supports `configSecret.create: false` with `configSecret.name: <existing secret>`,
letting a *different* Secret supply `config.yaml` instead. This change uses that: `charts/dws`
sets `dex.configSecret.create: false` and renders the entire Dex configuration itself, in a
chart-owned template, precisely so the bootstrap admin's hash can be computed in-line. The
subchart is left to provide only the Deployment/Service/RBAC around that externally-supplied
config.

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

**D2 — Two chart-owned Secrets: a retrieval Secret (plaintext, operator-facing) and a config
Secret (Dex-facing, hash only).** `charts/dws/templates/dex/admin-secret.yaml` (parallel to
`templates/admin/secret.yaml`) renders `<dws.dex.adminCredentials.fullname>` with `stringData: {
email, password }` — the one `NOTES.txt` tells the operator to `kubectl get`. Separately,
`charts/dws/templates/dex/config-secret.yaml` renders the Secret named by
`dex.configSecret.name`, containing the full Dex `config.yaml` (base64, under `data`, matching
the subchart's own `secret.yaml` shape exactly) — `issuer`, `storage`, `web`, `enablePasswordDB`,
the `staticClients` entry, and `staticPasswords` with the `bcrypt` hash. `values.yaml` sets
`dex.configSecret.create: false` and a fixed `dex.configSecret.name` (default `dex-config`) so
the subchart mounts this chart-rendered Secret instead of building its own from a static
passthrough. Splitting the two keeps the plaintext-bearing Secret narrowly scoped (only an
operator with Secret-read access sees the password) separate from the Dex-facing Secret (which
only ever holds the hash).

**D3 — The password itself is computed once, by a single named template, called from both
Secrets.** Rather than each of the two Secret templates independently calling
`lookup`/`randAlphaNum` (risking divergence — the retrieval Secret storing password A while
Dex's config hashes password B), `dws.dex.adminPassword` (a named template in `_helpers.tpl`)
encapsulates the lookup-guarded generation:
- If `dex.adminUser.existingSecret` is set, read the password from that operator-supplied Secret
  (via `lookup`) instead of generating one.
- Otherwise, `lookup` the chart's own admin-credentials Secret; reuse its stored password if
  found, else `randAlphaNum 20`.

Both `templates/dex/admin-secret.yaml` (plaintext) and `dws.dex.config`'s `staticPasswords` entry
(wrapped in `bcrypt`) call `include "dws.dex.adminPassword" .`. Helm template evaluation is
deterministic per values/lookup input within a single render, so both call sites resolve to the
same generated (or reused) password.

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
- **[Risk] Dex's config schema has no Helm-side validation — a malformed `issuer`/`storage`/
  `staticClients` shape in `dws.dex.config`'s output only surfaces as a Dex pod CrashLoop at
  runtime, not a template-time failure** → Mitigation: `helm lint`/`helm template` in the
  acceptance criteria catch YAML-shape mistakes in the rendered Secret; the dry-run install
  criterion (spec-required) additionally proves the pod actually starts, not just that the
  Secret renders.
- **[Risk] `dex.configSecret.name` is a fixed literal (not derived from `dws.fullname`/release
  name like other chart resources), since values.yaml cannot call template helpers** →
  Mitigation: documented default (`dex-config`) is overridable like `admin.database.existingSecret`;
  acceptable for a single Dex instance per namespace, the only supported topology in Phase 0.
- **[Trade-off] Bootstrap admin is a single static password, not rotatable without manual Secret
  deletion** → Accepted per roadmap §2a; this is explicitly a bootstrap/dev mechanism, not a
  production credential-management story.
