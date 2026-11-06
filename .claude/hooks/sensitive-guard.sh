#!/bin/bash
# PreToolUse guard: pause and ask the user before Claude touches secrets or timer/timeout settings.
# Prints a permissionDecision "ask" when something matches; prints nothing otherwise.

input=$(cat)
tool=$(jq -r '.tool_name // ""' <<<"$input")
path=$(jq -r '.tool_input.file_path // ""' <<<"$input")
text=$(jq -r '[.tool_input.content, .tool_input.new_string, .tool_input.command] | map(select(. != null)) | join("\n")' <<<"$input")

secret_files='(keys\.properties|local\.properties|google-services\.json|\.jks$|\.keystore$|\.p12$|\.env$)'
secret_text='(keys\.properties|local\.properties|API_KEY|api_?key|apiKey|secret|password|passwd|Bearer |Authorization|access_?token|refresh_?token|signingConfig|storePassword|keyPassword)'
timer_text='(timeout|Timeout|TimeUnit\.|Duration\.|\.(milliseconds|seconds|minutes|hours)\b|delay\([0-9]|PeriodicWorkRequest|setBackoffCriteria|setInitialDelay|SESSION_[A-Z_]*|IDLE_[A-Z_]*|EXPIRY|expiresAt|\bttl\b|\bTTL\b)'

reason=""
if [[ "$path" =~ $secret_files ]]; then
  reason="touches a secrets file ($path)"
elif [[ "$tool" != "Read" ]] && grep -Eq "$secret_text" <<<"$text"; then
  reason="involves API keys / credentials / tokens"
elif [[ "$tool" == "Edit" || "$tool" == "Write" ]] && grep -Eq "$timer_text" <<<"$text"; then
  reason="changes timer / timeout / session-expiry settings"
fi

[[ -z "$reason" ]] && exit 0

jq -n --arg r "Sensitive change: $reason. Approve only if you've agreed on the approach; otherwise deny and tell Claude how to handle it." '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "ask",
    permissionDecisionReason: $r
  }
}'
