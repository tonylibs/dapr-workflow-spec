# Sourced by Bash startup hooks. Parse data without evaluating it as shell code.
# Missing credentials are normal before the launcher provisions them.
if [ -r "$HOME/.config/agent-sandbox/credentials.env" ]; then
    while IFS= read -r agent_credential_line || [ -n "$agent_credential_line" ]; do
        case ${agent_credential_line%%=*} in
            ANTHROPIC_API_KEY|CLAUDE_CODE_OAUTH_TOKEN|OPENAI_API_KEY|COPILOT_GITHUB_TOKEN|GEMINI_API_KEY)
                case $agent_credential_line in
                    *=*) export "$agent_credential_line" ;;
                esac
                ;;
        esac
    done < "$HOME/.config/agent-sandbox/credentials.env"
fi
unset agent_credential_line
