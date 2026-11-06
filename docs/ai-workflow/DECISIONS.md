# Decisions

Append-only. Non-obvious calls only. Format: `.claude/workflow/templates/log-entry.md`.

## 2026-11-04 — Reviewer is read-only and runs in a fresh context
- **Context:** The agent that writes code is a poor reviewer of it; its context is full of its own reasoning.
- **Decision:** Reviewer has no Edit/Write tools and only sees the diff plus the Developer's stated decisions.
- **Alternatives:** single agent self-reviewing; reviewer allowed to fix things directly.
- **Why:** independent judgment, and fixes stay attributable to the Developer step.
- **Task:** Set up Developer / Reviewer / QA workflow

## 2026-11-05 — Reviews: network-only page 1, own module, code-only error text
- **Context:** First feature through `/dev-cycle`; needed to pick module placement, data strategy, and how to surface TMDB errors.
- **Decision:** New `:feature:tabs:tmdb:reviews` module with its own Hilt module; network-only, page 1. API errors show only the HTTP code ("Couldn't load reviews (401)").
- **Alternatives:** put it in `:app/detailsScreen`; offline-first via `OfflineFirstCache`; Paging 3; show the server message.
- **Why:** matches the toprated/popular module pattern and keeps scope small (user chose). TMDB's error JSON doesn't match core's `ErrorResponse`, so the parsed message would be the raw body.
- **Task:** Add Movie Reviews screen
