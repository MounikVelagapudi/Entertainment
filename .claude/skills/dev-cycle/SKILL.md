---
name: dev-cycle
description: Run a task through the Developer → Reviewer → QA pipeline and log the result. Use when the user types /dev-cycle or asks to run a task "through the workflow".
argument-hint: "<task description>  (empty = just load the workflow)"
---

# /dev-cycle

1. Read `.claude/workflow/workflow.md` and the three templates in `.claude/workflow/templates/`.
2. If `$ARGUMENTS` is empty: reply with a 3-line status (agents found, docs paths, last WORKLOG entry title) and wait for a task. Stop here.
3. Otherwise run the pipeline in `workflow.md` for this task:

   $ARGUMENTS

   - Fill each template and pass it as the prompt to the matching subagent (`developer`, `reviewer`, `qa`).
   - Models: developer inherits the session model; reviewer and qa run on sonnet. Override the reviewer to `model: "opus"` when the task touches auth/session, API keys/tokens, payments, timers/scheduling, or coroutine/concurrency code.
   - Respect the loop limits. Give the user a one-line status after each step.
   - If any report says `NEEDS_DECISION`, or the Reviewer lists anything under "Sensitive": pause and ask the user (AskUserQuestion) before continuing. Never pick the option yourself.
4. **Before handing off — always:** append the WORKLOG entry (and a DECISIONS entry if any non-obvious call was made) using `templates/log-entry.md`, and tick/annotate any matching item in `TODO.md`, `Enhancements.md`, or `CICD checklist.md`. Do this even if the cycle stopped early or failed.
5. Final message: what changed, review + QA verdicts, which docs were updated, follow-ups.
