# Entertainment (NEW)

Personal Android practice app: Kotlin, Jetpack Compose, multi-module (`:app`, `:core`, `:feature:tabs:*`). JDK 21.

## Commands
- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew testDebugUnitTest` (one module: `./gradlew :core:testDebugUnitTest`)
- Lint + tests, same as CI: `./gradlew lintDebug testDebugUnitTest --continue`

## Agent workflow
This repo uses a Developer → Reviewer → QA pipeline.
- Config: `.claude/workflow/workflow.md`
- Agents: `.claude/agents/{developer,reviewer,qa}.md`
- Templates: `.claude/workflow/templates/`
- Run it: `/dev-cycle <task>` (no args = load + show status)

At session start, read `.claude/workflow/workflow.md` and the last entry in `docs/ai-workflow/WORKLOG.md` so you know where things left off.

## Docs rule
After finishing any task — through `/dev-cycle` or not — append a summary to `docs/ai-workflow/WORKLOG.md`, add non-obvious decisions to `docs/ai-workflow/DECISIONS.md`, and update `TODO.md` / `Enhancements.md` / `CICD checklist.md` if the task advanced one of their items. Append only; formats in `.claude/workflow/templates/log-entry.md`.

## Sensitive changes — ask first
Before reading or changing API keys, tokens, credentials, signing config, `keys.properties` / `local.properties`, auth/session code, or timer/timeout/expiry values: stop, explain what you'd change, and ask the user how to approach it. A PreToolUse hook (`.claude/hooks/sensitive-guard.sh`) also prompts on these.

## Code style
- Comments: default to none. When needed, a terse `/* ... */` WHY note; KDoc only on public API.
- Don't commit or push unless asked.
