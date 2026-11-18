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

## 2026-11-05 — Details route keeps a String arg, parsed with toIntOrNull
- **Context:** `ROUTE_SCREEN2` crashed on a non-numeric id. The obvious fix is `NavType.IntType`, which the reviews route uses.
- **Decision:** Keep `NavType.StringType` and parse in the destination with `toIntOrNull()`; pop back on null.
- **Alternatives:** `IntType` on screen2 too.
- **Why:** With `IntType`, a non-Int route doesn't match and `navigate(String)` throws at the caller (push path, tab click). A String arg always matches, so bad input is handled in one place instead of at every caller.
- **Task:** Fix details screen id crash

## 2026-11-05 — Fastlane files hand-written, Ruby 3.3, workflow switched early
- **Context:** Phase 2 setup, but the Mac has no Homebrew/rbenv. `fastlane init` is interactive and needs Ruby 3.x.
- **Decision:** Wrote `Appfile`/`Fastfile` by hand, pinned Ruby 3.3.6, and switched CI to `bundle exec fastlane test` before verifying locally.
- **Alternatives:** wait for a local Ruby and run `fastlane init`; Ruby 3.4.
- **Why:** `init` only generates those two files, and a hand-written version stays minimal. On Ruby 3.4, gems that fastlane depends on (e.g. `abbrev`, `mutex_m`) moved out of the default set. The branch isn't pushed, so switching the workflow early is free as long as `Gemfile.lock` is committed before the first push.
- **Task:** CI/CD Phase 2: Fastlane setup (files only)
