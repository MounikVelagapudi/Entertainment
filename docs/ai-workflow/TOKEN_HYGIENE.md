# Token hygiene: asking Claude directly (no agents)

For quick questions or small changes where `/dev-cycle` is overkill. The whole conversation is re-sent on every message, so a long chat makes every new prompt more expensive and less focused. Prompt caching softens the cost, but the context still fills up.

## 1. Session hygiene
- **One task per conversation.** Run `/clear` when the task is done or the topic changes.
- **`/compact <what to keep>`** when the task isn't finished but the chat is long. It summarizes the history and keeps what you name, e.g. `/compact keep the session-timeout design decisions`.
- **Carry over the result, not the history.** Start the new chat with the final code path or a 3–5 line summary. Easiest: have Claude append it to `WORKLOG.md`, then start fresh with "read the last WORKLOG entry".
- **Fresh session instead of `--continue`** when you don't need the old details. `--continue` brings back the whole history.
- **`/context`** shows what's filling the context window. Check it when a session feels slow or vague.

## 2. Ask small, specific questions
- **Point, don't paste.** Use `@path/File.kt` or "lines 40–80 of X" instead of pasting whole files or logs.
- **Say how much answer you want:** "answer only", "just the diff", "max 5 bullets", "don't explain the code".
- **Batch related questions** into one prompt instead of five back-and-forth turns.
- **Trim command output:** ask for `./gradlew :core:testDebugUnitTest | tail -40`, not the full build log.

## 3. Pick the right size of model and effort
- **`/model`:** Sonnet or Haiku for simple Q&A, renames and boilerplate; Opus for design, tricky bugs, and auth or concurrency code.
- **`/effort`:** lower for routine edits, higher for hard problems.

## 4. Avoid rework (the biggest hidden cost)
- **Plan first for anything multi-file** (Shift+Tab into plan mode). A wrong first attempt plus a fix costs more than a plan.
- **Give acceptance criteria up front** so Claude doesn't guess and redo.
- **Big searches** ("where do we use X across modules?") → ask Claude to use the Explore subagent. It returns only the conclusion, so your main chat stays small.

## 5. Keep always-loaded files short
`CLAUDE.md` and the memory index load on every session, so they add to every single prompt. Keep them to rules and pointers; put the details in docs that Claude reads only when needed.
