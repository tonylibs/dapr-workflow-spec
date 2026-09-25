#!/bin/sh
# Configure agent CLI credentials from KEY=VALUE lines on stdin.
#
# The host launchers (new-ssh-sandbox.ps1, new_ssh_sandbox.py) stream the host's
# agent tokens here with `docker exec -i` right after the sandbox is created, so the
# tokens never appear in `docker inspect`, a command line, or the OpenSandbox store.
#
# sshd does not hand the container's environment to SSH sessions, so the tokens are
# written to /root/.ssh/environment, which sshd loads for every session (interactive
# or not) for exactly the names allowed by PermitUserEnvironment in the image's sshd
# config. Keep ALLOWED_NAMES and that list in sync.
#
# Re-running replaces the previous set: a name missing from stdin is removed.
# Values are never printed.
set -eu

ALLOWED_NAMES="ANTHROPIC_API_KEY CLAUDE_CODE_OAUTH_TOKEN OPENAI_API_KEY COPILOT_GITHUB_TOKEN GEMINI_API_KEY"
ENV_FILE=/root/.ssh/environment
CLAUDE_CONFIG=/root/.claude.json
AGY_SETTINGS=/root/.gemini/antigravity-cli/settings.json

is_allowed() {
    for allowed in $ALLOWED_NAMES; do
        [ "$1" = "$allowed" ] && return 0
    done
    return 1
}

# Update a JSON file in place with a jq filter, keeping it private to root.
jq_in_place() {
    file=$1
    shift
    tmp=$(mktemp "$file.XXXXXX")
    jq "$@" "$file" > "$tmp"
    chmod 600 "$tmp"
    mv "$tmp" "$file"
}

mkdir -p /root/.ssh
chmod 700 /root/.ssh
staged=$(mktemp /root/.ssh/environment.XXXXXX)
chmod 600 "$staged"
trap 'rm -f "$staged"' EXIT

# Windows launchers may send CRLF line endings.
tr -d '\r' | while IFS= read -r line || [ -n "$line" ]; do
    [ -z "$line" ] && continue
    name=${line%%=*}
    value=${line#*=}
    if [ "$name" = "$line" ] || ! is_allowed "$name"; then
        echo "agent-auth-setup: rejected a line whose name is not an allowed token name" >&2
        exit 1
    fi
    case $value in
        '' | *[[:space:]]*)
            echo "agent-auth-setup: $name is empty or contains whitespace" >&2
            exit 1
            ;;
    esac
    printf '%s=%s\n' "$name" "$value" >> "$staged"
done

mv "$staged" "$ENV_FILE"
trap - EXIT

# Read one value back from the environment file without exporting it into this shell.
token() {
    sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1
}

configured=""

# Claude Code reads CLAUDE_CODE_OAUTH_TOKEN and ANTHROPIC_API_KEY from the session
# environment. An interactive session asks once before using ANTHROPIC_API_KEY;
# pre-approve it the same way that prompt does (by the key's last 20 characters).
anthropic_key=$(token ANTHROPIC_API_KEY)
if [ -n "$anthropic_key" ]; then
    [ -s "$CLAUDE_CONFIG" ] || echo '{}' > "$CLAUDE_CONFIG"
    key_suffix=$(printf '%s' "$anthropic_key" | tail -c 20)
    jq_in_place "$CLAUDE_CONFIG" --arg suffix "$key_suffix" '
        .customApiKeyResponses.approved = (((.customApiKeyResponses.approved // []) - [$suffix]) + [$suffix])
        | .customApiKeyResponses.rejected = ((.customApiKeyResponses.rejected // []) - [$suffix])'
    configured="$configured claude(api-key)"
fi
if [ -n "$(token CLAUDE_CODE_OAUTH_TOKEN)" ]; then
    configured="$configured claude(oauth-token)"
fi

# Codex does not read API keys from the environment; it needs a stored login.
openai_key=$(token OPENAI_API_KEY)
if [ -n "$openai_key" ]; then
    printf '%s' "$openai_key" | codex login --with-api-key >/dev/null
    configured="$configured codex"
fi

# Copilot CLI reads COPILOT_GITHUB_TOKEN from the session environment directly.
if [ -n "$(token COPILOT_GITHUB_TOKEN)" ]; then
    configured="$configured copilot"
fi

# agy only honours GEMINI_API_KEY with modelProvider=gemini, and refuses to start with
# that provider but no key, so set the provider only while a key is present.
mkdir -p "$(dirname "$AGY_SETTINGS")"
[ -s "$AGY_SETTINGS" ] || echo '{}' > "$AGY_SETTINGS"
if [ -n "$(token GEMINI_API_KEY)" ]; then
    jq_in_place "$AGY_SETTINGS" '.modelProvider = "gemini"'
    configured="$configured agy"
else
    jq_in_place "$AGY_SETTINGS" 'if .modelProvider == "gemini" then del(.modelProvider) else . end'
fi

echo "agent-auth-setup: configured:${configured:- none}"
