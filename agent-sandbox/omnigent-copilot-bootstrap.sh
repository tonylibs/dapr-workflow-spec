#!/bin/sh
# Provision GitHub Copilot credentials for omnigent-spawned agents.
#
# WORKAROUND for an upstream omnigent bug -- delete this script (and its calls in
# agent-auth-setup.sh and the Dockerfile) once omnigent adds COPILOT_GITHUB_TOKEN to both
# _LOCAL_DAEMON_ENV_ALLOWLIST (omnigent/cli.py) and HARNESS_CREDENTIAL_ENV_VARS.
#
# omnigent strips COPILOT_GITHUB_TOKEN / GH_TOKEN / GITHUB_TOKEN at two allowlist barriers,
# _build_host_daemon_env (omnigent/cli.py) and _build_runner_env (omnigent/host/connect.py),
# even though its own copilot_harness.py documents those vars as its fallback chain. An
# exported token therefore never reaches the `harness: copilot` SDK or a `copilot` CLI run by
# an agent ("No authentication information found"). Same bug class as the M8 (2026-07-15)
# comment in omnigent/cli.py, which fixed it for CLAUDE_CODE_OAUTH_TOKEN.
#
# This writes the token to two on-disk locations reached via HOME, which both barriers keep:
#   1. $OMNIGENT_HOME/secrets.json key "copilot" + a `copilot:` block in config.yaml
#        -> read by _build_copilot_spawn_env; fixes `harness: copilot`
#   2. ~/.config/gh/hosts.yml oauth_token for the GitHub host
#        -> read by the copilot CLI (and gh/git when GH_TOKEN is absent); fixes agents
#           shelling out to `copilot`, but only for agents whose bwrap sandbox mounts it:
#           the spec must grant `read_paths: ["~/.config/gh"]` (see .omnigent/config.yaml)
# Both are required: (1) alone leaves agents that shell out to `copilot` broken.
#
# load_secret() asks the OS keyring first and reads the file store only when
# OMNIGENT_DISABLE_KEYRING=1 or the keyring raises a KeyringError. This image has no
# keyring backend (keyring.get_keyring() is the fail backend, which always raises), so the
# file store is used; a reachable-but-empty keyring would instead return None.
#
# Token: COPILOT_GITHUB_TOKEN, else GH_TOKEN, else GITHUB_TOKEN; no-op when none is set.
# COPILOT_GH_HOST (default github.com) picks the hosts.yml entry; COPILOT_GH_USER skips the
# `gh api user` login lookup. Idempotent; every file it writes is mode 0600. Values are never
# printed or passed on a command line.
set -eu

COPILOT_TOKEN="${COPILOT_GITHUB_TOKEN:-${GH_TOKEN:-${GITHUB_TOKEN:-}}}"
if [ -z "$COPILOT_TOKEN" ]; then
    echo "omnigent-copilot-bootstrap: no COPILOT_GITHUB_TOKEN/GH_TOKEN/GITHUB_TOKEN set; nothing to do." >&2
    exit 0
fi
export COPILOT_TOKEN

OMNIGENT_HOME="${OMNIGENT_CONFIG_HOME:-$HOME/.omnigent}"
GH_CONFIG="${GH_CONFIG_DIR:-$HOME/.config/gh}"
GH_HOSTNAME="${COPILOT_GH_HOST:-github.com}"

umask 077
mkdir -p "$OMNIGENT_HOME" "$GH_CONFIG"

# Replace $1 with the staged file $2, keeping it private.
install_private() {
    chmod 600 "$2"
    mv "$2" "$1"
}

# 1a. omnigent's file secret store: a flat JSON map. Refuse to touch a corrupt file rather
# than replace it and silently drop the other secrets it holds.
SECRETS="$OMNIGENT_HOME/secrets.json"
[ -s "$SECRETS" ] || echo '{}' > "$SECRETS"
tmp=$(mktemp "$SECRETS.XXXXXX")
if ! jq '.copilot = $ENV.COPILOT_TOKEN' "$SECRETS" > "$tmp" 2>/dev/null; then
    rm -f "$tmp"
    echo "omnigent-copilot-bootstrap: $SECRETS is not valid JSON; fix or remove it and rerun." >&2
    exit 1
fi
install_private "$SECRETS" "$tmp"

# 1b. The `copilot:` block. Appended as text rather than parsed (the system python3 has no
# yaml module): omnigent's config.yaml is flat top-level blocks, so appending a new one
# cannot disturb existing keys, and an existing block is left alone.
CONFIG="$OMNIGENT_HOME/config.yaml"
[ -f "$CONFIG" ] || : > "$CONFIG"
if ! grep -q '^copilot:' "$CONFIG"; then
    # Without a trailing newline, `copilot:` would be glued onto the previous line.
    if [ -s "$CONFIG" ] && [ -n "$(tail -c 1 "$CONFIG")" ]; then
        printf '\n' >> "$CONFIG"
    fi
    printf 'copilot:\n  github_token_ref: keychain:copilot\n' >> "$CONFIG"
elif ! grep -q '^  github_token_ref: keychain:copilot$' "$CONFIG"; then
    echo "omnigent-copilot-bootstrap: $CONFIG already has a copilot: block without" \
        "'github_token_ref: keychain:copilot'; left unchanged, harness: copilot may not authenticate." >&2
fi
chmod 600 "$CONFIG"

# 2. gh hosts.yml: the copilot CLI only honours a gh login by reading oauth_token from this
# file. Rewrite just this host's top-level block and keep any other hosts. Skip entirely when
# the block already holds this token, so reruns make no network call.
HOSTS="$GH_CONFIG/hosts.yml"
[ -f "$HOSTS" ] || : > "$HOSTS"
current_token=$(awk -v host="$GH_HOSTNAME:" '
    /^[^[:space:]]/ { in_host = ($0 == host) }
    in_host && $1 == "oauth_token:" { print $2; exit }' "$HOSTS")
if [ "$current_token" != "$COPILOT_TOKEN" ]; then
    gh_user="${COPILOT_GH_USER:-}"
    if [ -z "$gh_user" ]; then
        gh_user=$(GH_TOKEN="$COPILOT_TOKEN" GH_HOST="$GH_HOSTNAME" gh api user --jq .login 2>/dev/null || true)
    fi
    tmp=$(mktemp "$HOSTS.XXXXXX")
    awk -v host="$GH_HOSTNAME:" '
        /^[^[:space:]]/ { in_host = ($0 == host) }
        !in_host' "$HOSTS" > "$tmp"
    printf '%s:\n    oauth_token: %s\n    user: %s\n    git_protocol: https\n' \
        "$GH_HOSTNAME" "$COPILOT_TOKEN" "${gh_user:-x-access-token}" >> "$tmp"
    install_private "$HOSTS" "$tmp"
fi
chmod 600 "$HOSTS"

echo "omnigent-copilot-bootstrap: provisioned Copilot credentials for $GH_HOSTNAME"
