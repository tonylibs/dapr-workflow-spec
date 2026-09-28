#!/usr/bin/env bash
# Start the .omnigent orchestrator in a server-managed OpenShell sandbox.
#
# Uploads the tracked files under .omnigent/ as an agent bundle through the
# multipart POST /v1/sessions API with host_type "managed". The server
# provisions the sandbox from its `sandbox.openshell` config and clones the
# workspace repository into it.
set -euo pipefail

: "${OMNIGENT_SERVER:?set OMNIGENT_SERVER to the Omnigent server URL}"
WORKSPACE="${DWS_WORKSPACE:-https://github.com/tonylibs/dapr-workflow-spec#main}"
PROVIDER="${OMNIGENT_SANDBOX_PROVIDER:-openshell}"
TITLE="${DWS_SESSION_TITLE:-dws-orchestrator (openshell)}"

repo_root="$(git -C "$(dirname "$0")" rev-parse --show-toplevel)"
bundle="$(mktemp --suffix=.tar.gz)"
trap 'rm -f "$bundle"' EXIT

# Tracked files only, with paths relative to .omnigent/ (config.yaml at the root).
(cd "$repo_root/.omnigent" && git ls-files -z | tar --null -czf "$bundle" -T -)

metadata="$(jq -cn \
  --arg title "$TITLE" \
  --arg workspace "$WORKSPACE" \
  --arg provider "$PROVIDER" \
  '{title: $title, host_type: "managed", sandbox_provider: $provider, workspace: $workspace}')"

auth=()
if [[ -n "${OMNIGENT_TOKEN:-}" ]]; then
  auth=(-H "Authorization: Bearer ${OMNIGENT_TOKEN}")
fi

response="$(curl -fsS "${auth[@]}" \
  -F "metadata=${metadata}" \
  -F "bundle=@${bundle};type=application/gzip" \
  "${OMNIGENT_SERVER%/}/v1/sessions")"

echo "$response" | jq .
session_id="$(echo "$response" | jq -r '.session_id // empty')"
if [[ -n "$session_id" ]]; then
  echo
  echo "Provisioning runs in the background. Attach with:"
  echo "  omnigent attach ${session_id} --server ${OMNIGENT_SERVER}"
fi
