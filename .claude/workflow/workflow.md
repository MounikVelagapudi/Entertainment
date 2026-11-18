# Dev → Review → QA workflow

Config for the three-agent pipeline. Read by `/dev-cycle` and referenced from `CLAUDE.md`.

## Agents
| Role | Definition | Can edit | Template |
|---|---|---|---|
| Developer | `.claude/agents/developer.md` | production code | `templates/developer-task.md` |
| Reviewer | `.claude/agents/reviewer.md` | nothing (read-only) | `templates/reviewer-task.md` |
| QA | `.claude/agents/qa.md` | `src/test`, `src/androidTest` only | `templates/qa-task.md` |

Models: Developer inherits the session model (Opus). Reviewer and QA run on Sonnet; the Reviewer is bumped to Opus for auth, keys, payments, timers, or concurrency work.

## Pipeline
1. **Plan** (main session) — restate the task, list acceptance criteria and affected modules. Ask the user if scope is unclear.
2. **Develop** — spawn `developer` with the filled developer template.
3. **Review** — spawn `reviewer` (fresh context, sees only the diff).
   - `CHANGES_REQUESTED` → send blockers/majors back to `developer`, then review again. Max **2** loops, then stop and ask the user.
4. **QA** — spawn `qa` with the filled QA template.
   - `FAIL` from a prod bug → back to `developer`, then re-run QA. Max **2** loops.
5. **Log** — main session appends to the docs below using `templates/log-entry.md`. Required, even if the cycle stopped early.
6. **Hand off** — summarize to the user. No commit unless asked.

## Docs to update after every task
| File | What goes in |
|---|---|
| `docs/ai-workflow/WORKLOG.md` | One entry per task (newest at the bottom): summary, files, review + QA verdicts |
| `docs/ai-workflow/DECISIONS.md` | Only non-obvious decisions: context, choice, alternatives, why |
| `TODO.md` / `Enhancements.md` / `CICD checklist.md` | Tick boxes or add a dated note if the task advanced one of their items |

Append only — never rewrite or delete earlier entries.

## Sensitive changes (user decides)
API keys, tokens, credentials, signing, `keys.properties` / `local.properties`, auth/session code, and timer/timeout/expiry/WorkManager scheduling values.
- Any agent hitting one returns `NEEDS_DECISION`. The orchestrator pauses, asks the user (AskUserQuestion with the agent's options), then resumes that step with the answer.
- Reviewer's "Sensitive" list is shown to the user before QA starts.
- Enforced too: `.claude/hooks/sensitive-guard.sh` (PreToolUse) and `permissions.ask` in `.claude/settings.json` prompt the user on matching reads/edits/commands.

## Token budget
- Orchestrator passes paths and report excerpts to agents, never whole files or diffs.
- Agents follow the "Token budget" section in their own definition.
- Record each agent's token/time cost in the WORKLOG entry.

## Limits
- Agents don't commit, push, or edit workflow logs.
- Keep each agent brief short; pass file paths, not file contents.
