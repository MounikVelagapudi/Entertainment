# Work log

Append-only. One entry per `/dev-cycle` task, newest at the bottom. Format: `.claude/workflow/templates/log-entry.md`.

## 2026-11-04 — Set up Developer / Reviewer / QA workflow
- **Branch:** feature/movie-reviews
- **Summary:** Added three subagent definitions, prompt templates, the `/dev-cycle` skill, and `CLAUDE.md` so the workflow loads in every session.
- **Files:** `CLAUDE.md`, `.claude/agents/*.md`, `.claude/workflow/**`, `.claude/skills/dev-cycle/SKILL.md`, `docs/ai-workflow/*`
- **Review:** n/a (config only)
- **QA:** n/a
- **Follow-ups:** run a first real task through `/dev-cycle`; compare token cost vs single-agent (TODO.md §1).

## 2026-11-05 — Add Movie Reviews screen
- **Branch:** feature/movie-reviews
- **Summary:** New `:feature:tabs:tmdb:reviews` module (TMDB `3/movie/{id}/reviews`, page 1, network-only) with data/domain/presentation split like toprated. "Reviews" button on the details screen opens `reviews/{mediaId}`; screen shows Loading / list / Empty / Error + Retry.
- **Files:** `settings.gradle.kts`, `app/build.gradle.kts`, `app/.../MainActivity.kt`, `app/.../detailsScreen/MediaDetailScreen.kt`, `feature/tabs/tmdb/reviews/**` (build file, manifest, `data/`, `domain/`, `di/ReviewsModule.kt`, `presentation/`, `src/test/**`)
- **Review:** APPROVE (0 loops) — 3 minors left open: route arg parsed via `Integer.valueOf` (crash on bad id, same as details), double-tap "Reviews" stacks two entries, rating `format` uses device locale.
- **QA:** PASS — 30 tests (mapper 6, repo 8, use case 2, ViewModel 14); `./gradlew lintDebug testDebugUnitTest --continue` → 275 tests, 0 failed, lint clean.
- **Follow-ups:** fix the 3 review minors; Compose UI/nav tests for the screen; offline cache + paging if wanted. Agent cost: dev ~61k tokens/2.6 min, review ~25k/0.6 min, QA ~43k/2.4 min.
