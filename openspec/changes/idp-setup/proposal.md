## Why

`dws-console`'s auth roadmap (`docs/roadmaps/dws-auth.md`) needs a working IdP before any login,
token-verification, or gateway phase can be built or tested. Phase 0 delivers that IdP —
[Dex](https://dexidp.io/) as an optional in-chart dependency of `charts/dws` — plus a
bootstrap admin login generated automatically at install time, so every `dex.enabled=true`
install has a working credential without hand-editing `values.yaml` with a password.

## What Changes

- Add the upstream `dex` chart (repo `dexidp/helm-charts`, `https://charts.dexidp.io`) as a
  conditional Helm dependency in `charts/dws/Chart.yaml`, gated on `condition: dex.enabled`,
  following the same pattern already used for `postgresql`/`dapr`.
- Configure Dex via `values.yaml`:
  - `staticClients`: register `dws-console` as a public PKCE client (`public: true`, no secret)
    with a configurable redirect URI (`dex.consoleRedirectURI`).
  - `staticPasswords`: seeded with exactly one bootstrap admin entry.
- Bootstrap admin credential, generated at install time, never hand-set in `values.yaml`:
  - Password generated in-template with `randAlphaNum`, guarded by Helm's `lookup` against any
    existing Secret so `helm upgrade` never rotates it.
  - Hashed with Sprig's `bcrypt` for `staticPasswords[].hash` — template-only, no Job/script.
  - Email + plaintext password stored in a new chart-managed Secret
    (`<dex-fullname>-admin-credentials`), mirroring the `existingSecret`-override shape already
    used for the admin DB URL (`charts/dws/templates/admin/secret.yaml`).
  - Default email configurable via `dex.adminUser.email` (e.g. `admin@dws.local`).
- Add `charts/dws/templates/NOTES.txt` (doesn't exist yet), printing the `kubectl get secret`
  command to retrieve the generated admin email/password after install/upgrade.

Out of scope for this change (later roadmap phases): console login code, `dws-admin`/
`dws-controller` app code, the admin gateway, JWT/role verification middleware, and Dex user
management (Phase 7).

## Capabilities

### New Capabilities
- `helm-dex-idp`: `charts/dws`'s optional in-chart Dex dependency — conditional Helm
  dependency, `staticClients`/`staticPasswords` wiring, the bootstrap-admin credential
  mechanism (generate-once password, bcrypt hash, dedicated Secret), and `NOTES.txt`
  credential-retrieval output.

### Modified Capabilities
(none — this is a new, additive optional dependency; existing `postgresql.enabled`/
`dapr.enabled` behavior and chart output when `dex.enabled=false` are unchanged)

## Impact

- `charts/dws/Chart.yaml`: new conditional dependency entry.
- `charts/dws/values.yaml`: new `dex.*` values block.
- `charts/dws/templates/`: new templates under a `dex/` (or similarly scoped) subdirectory for
  the bootstrap-admin Secret, plus a new top-level `NOTES.txt`.
- `charts/dws/templates/_helpers.tpl`: new `dws.dex.fullname` (and related) helpers, following
  the existing `dws.postgres.fullname`/`dws.admin.fullname` pattern.
- No change to `dws-controller`, `dws-orchestrator`, `dws-admin`/console app code, or any
  runtime component — this change is chart-only.
- `docs/roadmaps/dws-auth.md`: Phase 0 status updated once implemented.
