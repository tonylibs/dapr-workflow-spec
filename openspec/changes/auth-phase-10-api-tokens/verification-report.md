# Verification Report: auth-phase-10-api-tokens

Updated 2026-10-31 after implementation and live tests. Schema: spec-driven, repo-local.

| Dimension | Status |
|---|---|
| Completeness | 38/38 tasks checked; live evidence recorded in verify.md |
| Correctness | All 14 requirements have implementation evidence; one auth-off APISIX configuration warning remains |
| Coherence | APISIX/Dapr spike passed; D4 updated; D1 token separation now enforced for supplied Secret names |

## Critical

None. All task checkboxes are complete. Individual pod recovery succeeded for admin, APISIX and controller; the final migration rehearsal passed 19 assertions including real rollback. The extended restart harness exited 1 at a Windows pipe-closure assertion after the successful recoveries; this was corrected by capturing logs to a file and is disclosed in verify.md. The standard gateway run exited 0 with 36 assertions.

## Warning

With `auth.enabled=false, apisix.enabled=true`, the default APISIX extraEnvVars still references the absent admin token Secret (`charts/dws/values.yaml:193`). The documented workaround is to clear `apisix.extraEnvVars` and `apisix.apisix.nginx.envs` for that standalone topology. This remains a divergence from the unconditional auth-off guarantee in `specs/helm-dapr-api-tokens/spec.md:12`; extend the auth-off render matrix and make that wiring conditional before treating this topology as supported by defaults.

## Resolved findings

- Shared supplied Dapr API Secret names are rejected in preflight; negative render coverage added.
- All three generated token values were preserved across a real Helm upgrade (hash equality).
- APISIX merges both filters in one Service proxy-rewrite plugin and resolves the environment reference. A token-protected echo app observed the distinct app token, including when the client forged the incoming header.
- The auth-on DB Helm test now invokes the health route through an injected Job sidecar; auth-off rendering stays unchanged.
- Admin lint/test/build passed; all 20 suites and 159 tests passed.
- All six chart scripts passed on Helm 4.3.0, including 30 token-render groups; lint/default template passed.
- Both controller tests and the DB health Helm test passed live.
- Gateway live test passed 36 assertions. Full matrices, subscription, token replacement, pod-IP checks, and SSE timing are recorded in [verify.md](verify.md).

No controller app-port guard is added: D7 explicitly defers it. Direct controller app access returned 200, while its actual sidecar public API returned 401 without a token.
## Final assessment

No critical checklist issues remain. One warning remains for auth-off/APISIX-only default wiring. Ready for archive with that documented limitation; this report does not claim that edge configuration satisfies the auth-off guarantee.
