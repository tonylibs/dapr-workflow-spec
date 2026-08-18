# `dws-console` Auth Roadmap

Supersedes the `dws-console.md` Phase 4 (definition submission) / Phase 5 (auth) split — those
can't ship safely in isolation (an unauthenticated write path is a bad default), so this tracks
them as one dependency-ordered sequence. `dws-console.md` should link here once this lands.

Ground rules decided during design, carried through every phase below:

- Login lives in the React app (OIDC Authorization Code + PKCE, public client, token in memory —
  never `localStorage`).
- JWT/role verification is Dapr-only, never hand-rolled in app code — `middleware.http.bearer` (+
  optional role/Rego middleware) on each sidecar that needs to gate something.
- `dws-console` only ever calls `dws-admin`. `dws-controller` is purely internal, reached only by
  `dws-admin`, server-to-server, through `dws-admin`'s own sidecar.
- Reads stay unauthenticated for now — guarding them is a deliberately separate later phase (6).

## 1. Phase dependency graph

```mermaid
flowchart TD
  P0["Phase 0: Dex<br/>in-chart IdP, staticClients/staticPasswords"] --> P1["Phase 1: Console login<br/>PKCE, in-memory token"]
  P0 --> P2["Phase 2: dws-controller Dapr-gated<br/>sidecar + bearer/role middleware"]
  P2 --> P3["Phase 3: dws-admin write-relay<br/>stateless proxy to dws-controller"]
  P3 --> P4["Phase 4: Admin gateway<br/>nginx: CORS preflight + proxy to<br/>dws-admin's own sidecar invoke path"]
  P1 --> P5["Phase 5: Console write UI<br/>submit definitions end-to-end"]
  P4 --> P5
  P5 --> P6["Phase 6: Guard reads too<br/>(deferred, separate phase)"]
  P0 --> P7["Phase 7: User management<br/>admin creates users, assigns built-in role<br/>(further-out, exploratory)"]
  P4 --> P7
```

## 2. Phased roadmap

| Phase | Scope | Depends on | Status |
|---|---|---|---|
| **0** | Add Dex as an optional in-chart dependency (toggle like `postgresql.enabled`); `staticPasswords` for dev users, `staticClients` registers `dws-console` as a public PKCE client; auto-generate a bootstrap admin login (see §2a) | — | ❌ not started |
| **1** | React OIDC client (Authorization Code + PKCE), in-memory token, silent renew, logout | Phase 0 | ❌ not started |
| **2** | Enable `dws-controller`'s Dapr sidecar (`dapr.io/enabled`/`app-id`/`app-port`); add `bearer` + optional role/Rego `Component`s and a `Configuration` wiring them to its inbound pipeline | Phase 0 (needs the IdP's JWKS endpoint) | ❌ not started |
| **3** | New route in `dws-admin`: stateless relay that forwards the `Authorization` header + body to `dws-controller` via `dws-admin`'s own local sidecar invoke call. No verification logic — `dws-admin` never inspects the token | Phase 2 | ❌ not started |
| **4** | New `admin-gateway` nginx Deployment/Service/ConfigMap (chart-bundled, not an assumed cluster Ingress): answers CORS preflight, proxies the real request to `dws-admin`'s sidecar invoke path. Extend `dws-admin`'s Service with its sidecar port. Add `bearer`/role `Component`s + `Configuration` to `dws-admin`'s sidecar, scoped to this route only | Phase 3 | ❌ not started |
| **5** | Wire the console's definition-submission UI to call the gateway with the bearer token attached; reads keep using the existing direct `dws-admin` path unchanged | Phases 1 and 4 | ❌ not started |
| **6** | Guard reads: move `dws-admin`'s read routes onto the same gateway+sidecar+bearer path, retire the old direct/CORS-only route | Phase 5 | ❌ not started — deliberately deferred, tracked here so it isn't lost |
| **7** | User management: an admin-only console screen to create users and assign one of a small set of built-in roles (e.g. `admin`/`operator`/`viewer`). New `dws-admin` route, reusing Phase 4's gateway + Dapr role check (only `admin`-role tokens may call it), which manages users through **Dex's own gRPC management API** — no user/password storage or hashing added to DWS's own database | Phases 0, 4 | ❌ not started — further-out, exploratory; see §4 for a real open risk before committing to this shape |

## 2a. Phase 0 detail — bootstrap admin user

So there's always a working login right after `helm install` with the console/Dex enabled, without
anyone hand-editing `values.yaml` with a password:

- Generate the password in-template with `randAlphaNum`, guarded by `lookup` against the existing
  Secret so `helm upgrade` doesn't rotate it on every release (the standard Bitnami-chart pattern
  for admin passwords).
- Hash it with Sprig's `bcrypt` function for Dex's `staticPasswords[].hash` — Dex requires bcrypt,
  and this keeps the whole thing template-only, no Job/script needed.
- Store email + plaintext password in a new chart-managed Secret (e.g.
  `{{ include "dws.dex.fullname" . }}-admin-credentials`), same `existingSecret`-override shape
  already used for the Postgres/admin DB URL — never in `values.yaml` itself.
- Default identity configurable (`dex.adminUser.email`, e.g. `admin@dws.local`); password is
  generated, never user-supplied by default.
- Add `charts/dws/templates/NOTES.txt` (doesn't exist yet) to print the `kubectl get secret ...`
  retrieval command after install/upgrade — the standard place Helm surfaces a generated credential.

## 3. Rationale for ordering

- **0 before 1/2**: nothing can validate or issue a token without an IdP to point at.
- **2 before 3**: `dws-controller` must already be Dapr-gated before `dws-admin` is allowed to relay
  real writes to it — never let the relay exist ahead of the thing it's relaying to.
- **3 before 4**: the gateway's only job is getting traffic to the relay route safely; build the
  route first so there's something real to point it at.
- **4 before 5**: don't wire the write UI to a CORS/preflight path that doesn't work yet.
- **6 last, and separate**: reads are lower-risk and already shipped unauthenticated — folding them
  in early would block writes on a change that isn't urgent. Revisit once 0–5 are stable.
- **7 depends on 0 and 4, not on 1/2/3/5/6**: user management only needs an IdP to manage (Phase 0)
  and an existing Dapr-gated admin write path to reuse (Phase 4) — it doesn't touch `dws-controller`
  or reads at all, so it can be picked up any time after those two, independent of the rest.

## 4. Open items

- Exact Dapr role/Rego middleware type name — confirm against current `docs.dapr.io` middleware
  reference before Phase 2/4 implementation (flagged during design, not yet verified).
- Dex's `staticClients`/`staticPasswords` are a dev/quickstart shape — real deployments will swap
  in a connector (LDAP/SAML/upstream OIDC) or a different IdP entirely; nothing above should assume
  Dex specifically beyond Phase 0's chart toggle.
- `dws-admin`'s CORS module (`src/config/cors.ts`) becomes redundant for the write path once Phase
  4 lands (the gateway owns CORS there) — decide whether to leave it as-is for reads or trim it.
- **Phase 7 real risk**: Dex's local/static-password connector has [known, still-open upstream gaps
  around attaching a `groups` claim per user](https://github.com/dexidp/dex/issues/1080) (also
  [#3958](https://github.com/dexidp/dex/issues/3958)) — dynamic user *creation* is well-supported
  via [Dex's gRPC API](https://dexidp.io/docs/configuration/api/) (`CreatePassword`/`UpdatePassword`/
  `DeletePassword`/`ListPasswords`, built for exactly this), but cleanly attaching a *role* to that
  user for the token to carry is the open design question — resolve before committing to "assign a
  built-in role" as a literal Dex group. A fallback (e.g. a small role-lookup table in `dws-admin`'s
  own DB, keyed by subject, consulted alongside — not instead of — Dapr's role check) may end up
  being necessary; this needs a short spike at the start of Phase 7, not an assumption baked in now.
