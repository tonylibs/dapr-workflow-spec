# Sourced by Bash startup hooks. Parse data without evaluating it as shell code.
# Missing credentials are normal before the launcher provisions them.
export JAVA_HOME=/opt/java/openjdk
export GOPATH=/root/go
export GOMODCACHE=/root/go/pkg/mod
export UV_TOOL_BIN_DIR=/root/.local/bin
export PATH="/root/.local/bin:/root/.local/share/pnpm/bin:/usr/local/go/bin:/root/go/bin:$JAVA_HOME/bin:$PATH"

if [ -r "$HOME/.config/agent-sandbox/credentials.env" ]; then
    while IFS= read -r agent_credential_line || [ -n "$agent_credential_line" ]; do
        case ${agent_credential_line%%=*} in
            ANTHROPIC_API_KEY|CLAUDE_CODE_OAUTH_TOKEN|OPENAI_API_KEY|COPILOT_GITHUB_TOKEN|GEMINI_API_KEY|GH_TOKEN)
                case $agent_credential_line in
                    *=*) export "$agent_credential_line" ;;
                esac
                ;;
        esac
    done < "$HOME/.config/agent-sandbox/credentials.env"
fi
unset agent_credential_line
