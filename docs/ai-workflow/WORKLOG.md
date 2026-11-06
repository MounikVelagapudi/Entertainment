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

## 2026-11-05 — Fix Movie Reviews review minors
- **Branch:** feature/movie-reviews
- **Summary:** Reviews route arg is now `NavType.IntType` read via `getInt` (pop back if missing); Reviews navigation uses `launchSingleTop = true`; rating formatted with `Locale.ROOT` ("8.0/10" on de/fr).
- **Files:** `app/src/main/java/com/example/entertainment/MainActivity.kt`, `feature/tabs/tmdb/reviews/src/main/java/com/example/reviews/presentation/MovieReviewsScreen.kt`
- **Review:** APPROVE (0 loops), Sensitive: none — Developer's claim that a non-Int id "won't match, so can't crash" is wrong: `navigate(String)` throws when no route matches. Only unreachable today because `ROUTE_SCREEN2` already crashes on a non-Int id via `Integer.valueOf`. The `containsKey` pop-back branch is unreachable (harmless).
- **QA:** PASS — no tests added (changes are inline in Compose/NavHost; no UI test infra); `./gradlew lintDebug testDebugUnitTest --continue` → 275 tests, 0 failed, lint clean.
- **Follow-ups:** make `ROUTE_SCREEN2` parse with `toIntOrNull()` (or `IntType`) and pass the Int to details + reviews; manual device checks: de/fr locale rating, double-tap Reviews → single back, push → details → Reviews → back. Agent cost: dev ~14k tokens/26 s, reviewer ~12k/20 s, QA ~13k/28 s.

## 2026-11-05 — Fix details screen id crash
- **Branch:** feature/movie-reviews
- **Summary:** `ROUTE_SCREEN2` now parses `mediaId` once with `toIntOrNull()`; null → pop back once, render nothing. The parsed Int feeds both `MediaDetailScreen` and the reviews route, so a non-numeric id can no longer crash details or make the reviews `navigate()` throw. Closes the follow-up from "Fix Movie Reviews review minors".
- **Files:** `app/src/main/java/com/example/entertainment/MainActivity.kt`
- **Review:** APPROVE (0 loops, sonnet), Sensitive: none — 2 minors, no change needed: theoretical double pop if user hits back in the same frame; `MediaDetailScreen`'s `LaunchedEffect(Unit)` is fine since the entry's id is fixed.
- **QA:** PASS (sonnet) — no tests added (inline NavHost, no UI test infra); `./gradlew lintDebug testDebugUnitTest --continue` → 275 tests, 0 failed, lint clean (counts confirmed by orchestrator; QA didn't capture them).
- **Follow-ups:** manual checks: `screen2/abc` → back to main, details → Reviews → back, double-tap Reviews. Extract route-arg parsing into a pure function if nav logic needs unit coverage. Agent cost: dev ~12k tokens/29 s, reviewer ~11k/20 s, QA ~9k/15 s.

## 2026-11-05 — CI/CD Phase 2: Fastlane setup (files only)
- **Branch:** feature/movie-reviews
- **Summary:** Added Bundler + Fastlane config (`test`, `test_pos`, `build_debug` lanes). CI now runs `bundle exec fastlane test` via `ruby/setup-ruby`. Nothing has run locally because the Mac has no Homebrew/rbenv, only system Ruby 2.6.
- **Files:** `.ruby-version`, `Gemfile`, `fastlane/Appfile`, `fastlane/Fastfile`, `.gitignore`, `.github/workflows/android.yml`, `CICD checklist.md`
- **Review:** n/a (config only) — YAML parses, `ruby -c` passes on `Fastfile`/`Gemfile`
- **QA:** not run — needs Ruby 3.3.6 locally
- **Follow-ups:** user installs Homebrew → rbenv → Ruby 3.3.6; `bundle install` + `bundle lock --add-platform x86_64-linux`; `bundle exec fastlane test`; commit `Gemfile.lock` before pushing.

## 2026-11-05 — CI/CD Phase 2: Ruby toolchain + Fastlane verified locally
- **Branch:** feature/movie-reviews
- **Summary:** Installed rbenv/ruby-build and Ruby 3.3.6 (the user had installed Homebrew), ran `bundle install` (fastlane 2.241.2) and added the `x86_64-linux` lockfile platform for CI. `bundle exec fastlane test` passes.
- **Files:** `Gemfile.lock` (new), `~/.zshrc` (rbenv init, outside repo), `CICD checklist.md`
- **Review:** n/a (toolchain/config)
- **QA:** PASS — `bundle exec fastlane test` → 275 tests, 0 failed, lint clean
- **Follow-ups:** commit Fastlane files + lockfile, push, confirm green on the GitHub runner; Phase 3 (signing + Firebase App Distribution).

## 2026-11-06 — Unit tests: test-data builders + per-class builders
- **Branch:** feature/movie-reviews
- **Summary:** Moved every mock and object construction out of the test classes. Test data now comes from `testdata/*TestData.kt` builders with named, defaulted params; each class under test has a `<Class>Builder.kt` that owns its mocks/fakes with `withX()` setup and `build()`. Test classes are arrange/act/assert only. Behaviour unchanged.
- **Files:** `app` (MovieTestData, HomeViewModel/MediaDetailsViewModel/TrendingUseCase builders), `toprated` (TopRatedTestData, repo + use-case builders), `popular` (PopularTestData, repo builder), `reviews` (ReviewsTestData, repo/use-case/ViewModel builders), `core/session` (FakeTmdb, SessionTestDoubles, GuestSessionApiBuilder), `pos` (PosTestData, PosApiTestData, PosEntityTestData, FakeBarcodeScanner, PosRepositoryScenarios, MockPosHttpClient, TestWorkManager, 8 builders). Convention added to `qa.md` and `CLAUDE.md`.
- **Review:** n/a (done directly, not via /dev-cycle)
- **QA:** PASS — `./gradlew lintDebug testDebugUnitTest --continue` → 275 tests, 0 failed, lint clean (same count as before the refactor)
- **Follow-ups:** `MainDispatcherRule` is still copied in app/pos/reviews (a shared test-fixtures module would remove that); `StorageHealthMonitorTest` and `SimulatedBarcodeScannerTest` left as-is (no-arg / two real objects, a builder adds nothing).
