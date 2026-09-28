#!/usr/bin/env bash
# Start the .omnigent orchestrator in a server-managed OpenShell sandbox, the
# managed-sandbox counterpart of `omnigent run .omnigent [-p PROMPT]`.
#
# Uploads the tracked files under .omnigent/ as an agent bundle through the
# multipart POST /v1/sessions API with host_type "managed". The server
# provisions the sandbox from its `sandbox.openshell` config and clones the
# workspace repository into it. With -p, waits for the sandbox to be ready and
# sends the prompt as the first message. Then opens the TUI via `omnigent attach`.
#
# Usage: run-orchestrator.sh [-p PROMPT] [--no-attach]
set -euo pipefail

usage() { echo "usage: $0 [-p PROMPT] [--no-attach]" >&2; exit 2; }

prompt=""
attach=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    -p | --prompt) [[ $# -ge 2 ]] || usage; prompt="$2"; shift 2 ;;
    --no-attach) attach=0; shift ;;
    -h | --help) usage ;;
    *) usage ;;
  esac
done

: "${OMNIGENT_SERVER:?set OMNIGENT_SERVER to the Omnigent server URL}"
server="${OMNIGENT_SERVER%/}"
WORKSPACE="${DWS_WORKSPACE:-https://github.com/tonylibs/dapr-workflow-spec#main}"
PROVIDER="${OMNIGENT_SANDBOX_PROVIDER:-openshell}"
TITLE="${DWS_SESSION_TITLE:-dws-orchestrator (openshell)}"
READY_TIMEOUT_S="${DWS_READY_TIMEOUT_S:-900}"

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
  "${server}/v1/sessions")"
session_id="$(jq -r '.session_id // empty' <<<"$response")"
[[ -n "$session_id" ]] || { echo "unexpected response: $response" >&2; exit 1; }
echo "session ${session_id}" >&2

if [[ -n "$prompt" ]]; then
  # sandbox_status goes provisioning -> cloning -> starting -> connecting -> ready/failed,
  # and is null once the launch has succeeded. A message sent before a runner is bound
  # is rejected with runner_unavailable, so wait first.
  deadline=$((SECONDS + READY_TIMEOUT_S))
  last_stage=""
  while :; do
    session="$(curl -fsS "${auth[@]}" "${server}/v1/sessions/${session_id}")"
    stage="$(jq -r '.sandbox_status.stage // empty' <<<"$session")"
    runner_id="$(jq -r '.runner_id // empty' <<<"$session")"
    if [[ "$stage" != "$last_stage" && -n "$stage" ]]; then
      echo "sandbox: ${stage}" >&2
      last_stage="$stage"
    fi
    if [[ "$stage" == "failed" ]]; then
      echo "sandbox launch failed: $(jq -r '.sandbox_status.error' <<<"$session")" >&2
      exit 1
    fi
    if [[ "$stage" == "ready" || ( -z "$stage" && -n "$runner_id" ) ]]; then
      break
    fi
    if ((SECONDS >= deadline)); then
      echo "sandbox not ready after ${READY_TIMEOUT_S}s (last stage: ${last_stage:-none})" >&2
      exit 1
    fi
    sleep 3
  done

  event="$(jq -cn --arg text "$prompt" \
    '{type: "message", data: {role: "user", content: [{type: "input_text", text: $text}]}}')"
  curl -fsS "${auth[@]}" -H 'Content-Type: application/json' -d "$event" \
    "${server}/v1/sessions/${session_id}/events" >/dev/null
  echo "prompt sent" >&2
fi

if ((attach)); then
  exec omnigent attach "$session_id" --server "$server"
fi
echo "$session_id"
