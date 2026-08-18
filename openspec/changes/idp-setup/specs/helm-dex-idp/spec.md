## Purpose

Makes Dex an optional, chart-managed identity provider for `charts/dws`, installable by the
chart itself via a conditional dependency, with a bootstrap admin login generated automatically
at install time so a working login always exists once Dex is enabled.

## ADDED Requirements

### Requirement: Dex renders as a conditional chart dependency

`charts/dws`'s `Chart.yaml` SHALL declare the upstream `dex-idp` chart (repository
`https://charts.dexidp.io`) as a dependency gated on `condition: dex.enabled`. `values.yaml`
SHALL expose `dex.enabled`, defaulting to `false`. When `dex.enabled` is `true`, `helm install`/
`upgrade` SHALL install Dex as part of the release. When `dex.enabled` is `false`, the chart
SHALL NOT render or install any Dex resources, and the release output SHALL be unchanged from
before this capability existed.

Owning component: `charts/dws` (`Chart.yaml`, `values.yaml`).

#### Scenario: Dex enabled renders Dex resources

- **WHEN** `helm template charts/dws` (or `helm install`) is run with `dex.enabled=true`
- **THEN** the rendered/installed release includes the `dex-idp` subchart's Deployment and
  Service, the bootstrap-admin credentials Secret, and `NOTES.txt` output describing how to
  retrieve the admin login

#### Scenario: Dex disabled (default) renders no Dex resources

- **WHEN** `helm template charts/dws` is run with default values (`dex.enabled=false`)
- **THEN** no `dex-idp` subchart resources, bootstrap-admin Secret, or Dex-related content in
  `NOTES.txt` are rendered, and the rest of the chart's rendered output is unchanged

### Requirement: dws-console is registered as a public PKCE client

When `dex.enabled` is `true`, Dex's `staticClients` configuration SHALL register a client for
`dws-console` with `public: true` (no client secret) and a redirect URI sourced from
`dex.consoleRedirectURI`.

Owning component: `charts/dws` (Dex `values.yaml` passthrough).

#### Scenario: Console client is public with a configurable redirect URI

- **WHEN** `helm template charts/dws` is run with `dex.enabled=true` and a custom
  `dex.consoleRedirectURI`
- **THEN** the rendered Dex configuration's `staticClients` entry for `dws-console` has
  `public: true`, no `secret` field, and `redirectURIs` containing the configured value

### Requirement: Exactly one bootstrap admin static password is seeded

When `dex.enabled` is `true`, Dex's `staticPasswords` configuration SHALL be seeded with exactly
one entry representing the bootstrap admin user, identified by `dex.adminUser.email` (default
`admin@dws.local`).

Owning component: `charts/dws` (Dex `values.yaml` passthrough).

#### Scenario: Bootstrap admin appears in staticPasswords

- **WHEN** `helm template charts/dws` is run with `dex.enabled=true`
- **THEN** the rendered Dex configuration's `staticPasswords` contains exactly one entry whose
  email matches `dex.adminUser.email`

### Requirement: Bootstrap admin password is generated once and never rotated on upgrade

The bootstrap admin password SHALL be generated in-template using `randAlphaNum`, guarded by a
Helm `lookup` against any existing admin-credentials Secret in the target namespace/release. A
first `helm install` SHALL generate a new random password. A subsequent `helm upgrade` against
the same release SHALL reuse the password already stored in that Secret rather than generating
or storing a new one. The plaintext password SHALL NOT appear in `values.yaml` or be settable by
the operator through chart values.

Owning component: `charts/dws` (bootstrap-admin credentials template).

#### Scenario: First install generates a password

- **WHEN** `helm install` runs with `dex.enabled=true` against a release with no pre-existing
  admin-credentials Secret
- **THEN** a new random password is generated and stored in the rendered Secret

#### Scenario: Upgrade preserves the existing password

- **WHEN** `helm upgrade` runs against a release that already has an admin-credentials Secret
  from a prior install
- **THEN** the rendered Secret's password matches the value already stored in the existing
  Secret, not a newly generated value

### Requirement: Bootstrap admin password is bcrypt-hashed for Dex's staticPasswords

The bootstrap admin password SHALL be hashed with Sprig's `bcrypt` template function to populate
`staticPasswords[].hash`. Hashing SHALL happen entirely within Helm templates — no Job, init
container, or external script SHALL be introduced to compute the hash.

Owning component: `charts/dws` (Dex `values.yaml` passthrough).

#### Scenario: staticPasswords hash is a valid bcrypt hash of the generated password

- **WHEN** `helm template charts/dws` is run with `dex.enabled=true`
- **THEN** the rendered `staticPasswords[].hash` value is a bcrypt hash, and verifying it against
  the plaintext password stored in the admin-credentials Secret succeeds

### Requirement: Bootstrap admin credentials are stored in a dedicated Secret

When `dex.enabled` is `true`, the chart SHALL render a Secret (named `<dex fullname>-admin-
credentials`) containing the bootstrap admin's email and plaintext password, following the same
`existingSecret`-override shape already used for the admin component's database URL. The
credentials SHALL NOT be written into `values.yaml` or any ConfigMap.

Owning component: `charts/dws` (bootstrap-admin credentials template).

#### Scenario: Admin credentials Secret is rendered

- **WHEN** `helm template charts/dws` is run with `dex.enabled=true`
- **THEN** a Secret named `<dex fullname>-admin-credentials` is rendered containing the admin
  email and plaintext password as data fields

### Requirement: NOTES.txt prints the credential-retrieval command

The chart SHALL include a `NOTES.txt` that, when `dex.enabled` is `true`, prints a `kubectl get
secret` command (naming the actual rendered Secret and namespace) an operator can run to
retrieve the generated admin email and password after install or upgrade. When `dex.enabled` is
`false`, this content SHALL NOT appear in `NOTES.txt` output.

Owning component: `charts/dws` (`templates/NOTES.txt`).

#### Scenario: Install output includes a real retrieval command

- **WHEN** `helm install`/`helm template` runs with `dex.enabled=true`
- **THEN** the rendered `NOTES.txt` output includes a non-empty, executable `kubectl get secret`
  command referencing the actual Secret name and namespace used by that release

#### Scenario: Dex disabled omits Dex guidance from NOTES.txt

- **WHEN** `helm install`/`helm template` runs with `dex.enabled=false`
- **THEN** the rendered `NOTES.txt` output contains no Dex-related credential-retrieval guidance
