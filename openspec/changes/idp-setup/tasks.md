## 1. Chart dependency

- [ ] 1.1 Add `dex-idp` (repo `https://charts.dexidp.io`) as a conditional dependency in `charts/dws/Chart.yaml`, gated on `condition: dex.enabled`, matching the existing `postgresql`/`dapr` entries. Pin an explicit chart version.
- [ ] 1.2 Run `helm dependency update charts/dws` and confirm the new chart appears under `charts/dws/charts/`.

## 2. Helpers and values

- [ ] 2.1 Add `dws.dex.fullname`, `dws.dex.selectorLabels`, and `dws.dex.adminCredentials.fullname` to `charts/dws/templates/_helpers.tpl`, mirroring the existing `dws.postgres.*`/`dws.admin.*` helpers.
- [ ] 2.2 Add a named template `dws.dex.adminPassword` in `_helpers.tpl` implementing the lookup-guarded `randAlphaNum` generation from design.md's D3 (reused by both the Dex config's `staticPasswords` hash and the admin-credentials Secret's plaintext).
- [ ] 2.3 Add a `dex:` block to `charts/dws/values.yaml`: `enabled: false`, `consoleRedirectURI`, `adminUser.email` (default `admin@dws.local`), `adminUser.existingSecret`/`existingSecretKey` overrides (mirroring `admin.database.existingSecret`), and `config.staticClients`/`config.staticPasswords`/`config.enablePasswordDB` wired to register `dws-console` as a public PKCE client and seed the single bootstrap admin entry (hash via `dws.dex.adminPassword` + `bcrypt`).

## 3. Bootstrap-admin credentials Secret

- [ ] 3.1 Add `charts/dws/templates/dex/admin-secret.yaml`, gated on `.Values.dex.enabled` and `(not .Values.dex.adminUser.existingSecret)`, rendering `<dws.dex.adminCredentials.fullname>` with `stringData: {email, password}` sourced from `dex.adminUser.email` and the `dws.dex.adminPassword` template.
- [ ] 3.2 Confirm (via `helm template`) the Secret's plaintext password and the Dex config's `staticPasswords[].hash` are derived from the same generated value in a single render pass.

## 4. NOTES.txt

- [ ] 4.1 Add `charts/dws/templates/NOTES.txt`, printing a `kubectl get secret <fullname> -n <namespace> -o jsonpath=...` command for the admin email/password, gated on `.Values.dex.enabled`.

## 5. Verification

- [ ] 5.1 Run `helm lint charts/dws` and `helm template charts/dws` with `dex.enabled=true`; confirm they succeed and the output includes the Dex Deployment/Service, the admin-credentials Secret, and Dex `NOTES.txt` content.
- [ ] 5.2 Run `helm template charts/dws` with default values (`dex.enabled=false`); confirm no Dex-related resources or `NOTES.txt` content render, and the rest of the chart's output is unchanged from before this change.
- [ ] 5.3 Dry-run install against a real (e.g. kind/local) cluster with `dex.enabled=true`; capture the rendered Secret, Dex Deployment/Service, and the `NOTES.txt` output's retrieval command, and confirm the command actually returns the credentials.
- [ ] 5.4 Against the same release, run `helm upgrade` unchanged and confirm the admin-credentials Secret's password value is byte-for-byte identical before and after (proves the `lookup` guard, not just its presence in the template).
- [ ] 5.5 Update `docs/roadmaps/dws-auth.md`'s Phase 0 status (table row and mermaid graph label) to reflect completion.
