# CI/CD Checklist

Order of work: get plain Gradle CI working first, add Fastlane on top, and do signed builds last. Full background is in `Enhancements.md`.

## Before you start
- Remote: `github.com/MounikVelagapudi/Entertainment`, so CI runs on GitHub Actions.
- JDK: **21**. Android Studio's bundled JDK is 21, and Robolectric needs 21 to run SDK 35. `Enhancements.md` says 17, which is wrong for this project.
- Ruby: your Mac only has the system Ruby (2.6). Fastlane needs 3.1 or newer, installed through rbenv.
- `.github/`, `Gemfile`, `fastlane/` and release signing don't exist yet.

---

## Phase 1: CI with Gradle

- [ ] Commit `feature/tabs/pos/src/` (main + test). It's untracked right now, so CI would build an empty module and `app` would fail.
- [x] Run `./gradlew testDebugUnitTest` locally and confirm all modules pass. *(2026-11-03: 245 tests, 0 failures, after the `core` fix in the log below)*
- [x] Run `./gradlew lintDebug` locally. *(It failed on 4 real errors. I fixed them instead of adding a baseline, so no baseline was needed.)*
  - ~~add a lint baseline~~ not needed
- [x] Set `org.gradle.jvmargs=-Xmx4g` in `gradle.properties`.
- [ ] Add the repo secret `TMDB_API_KEY` (Settings → Secrets and variables → Actions).
- [x] Add `.github/workflows/android.yml` *(created 2026-11-03, slightly extended from the version below; see the log)*:

```yaml
name: Android CI

on:
  pull_request: { branches: [main] }
  push: { branches: [main] }

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - name: Cache Robolectric jars
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository/org/robolectric
          key: robolectric-${{ hashFiles('gradle/libs.versions.toml') }}
      - name: Write keys.properties
        env:
          TMDB_API_KEY: ${{ secrets.TMDB_API_KEY }}
        run: echo "TMDB_API_KEY=${TMDB_API_KEY}" > keys.properties
      - name: Lint + unit tests
        run: ./gradlew lintDebug testDebugUnitTest --continue --stacktrace
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: |
            **/build/reports/
            **/build/test-results/
          retention-days: 14
```

- [ ] Push and open a PR. The `test` check should go green.
- [ ] Break a test on purpose in a throwaway PR and confirm the check goes red.
- [ ] Turn on branch protection: Settings → Branches → `main`
  - [ ] require a pull request
  - [ ] require the `test` status check (it only shows up after the workflow has run once)
  - [ ] block force pushes

### Optional: coverage with Kover
- [ ] Add `kover` to `[plugins]` in `gradle/libs.versions.toml`, with `apply false` in the root build file.
- [ ] Apply it in `feature/tabs/pos/build.gradle.kts`, and exclude `*_Factory*`, `Hilt_*`, `*_HiltModules*`, `*_Impl*`, `*ComposableSingletons*` and anything annotated `@Composable`.
- [ ] Add `variant("debug") { verify { rule { minBound(60) } } }`.
- [ ] Run `./gradlew :feature:tabs:pos:tasks --group verification` and confirm the `kover*Debug` tasks exist (Kover on AGP 9 is new).
- [ ] Add `:feature:tabs:pos:koverVerifyDebug` to the CI command.

### Optional: POS-only job
- [ ] Add a job that runs `./gradlew :feature:tabs:pos:testDebugUnitTest`, filtered with `dorny/paths-filter` on `feature/tabs/pos/**` and `core/**`.
- [ ] Put the filter in the job's `if:`, not in `on.paths`. With `on.paths`, a required check that gets skipped sits on "pending" forever.

---

## Phase 2: Fastlane

- [x] `brew install rbenv ruby-build` *(2026-11-05, Homebrew 7.0.12, rbenv 1.3.2, plus `openssl@3 libyaml`)*
- [x] `rbenv install 3.3.6` *(`.ruby-version` = `3.3.6` already written 2026-11-05; rbenv picks it up automatically)*
- [x] Create the `Gemfile`: *(2026-11-05, pinned `~> 2.225` and loads `fastlane/Pluginfile` for Phase 3)*
  ```ruby
  source "https://rubygems.org"
  gem "fastlane"
  ```
- [x] Run `bundle install` and commit `Gemfile.lock`. *(fastlane 2.241.2, 104 gems; `x86_64-linux` platform added. Not committed yet)*
- [x] ~~Run `bundle exec fastlane init`~~ *(2026-11-05: wrote `fastlane/Appfile` + `Fastfile` by hand instead; `init` is interactive and only generates those two files)*
- [x] Add the test lanes to `fastlane/Fastfile`: *(plus a `build_debug` lane)*
  ```ruby
  default_platform(:android)

  platform :android do
    lane :test do
      gradle(task: "lintDebug testDebugUnitTest", flags: "--continue")
    end

    lane :test_pos do
      gradle(task: ":feature:tabs:pos:testDebugUnitTest")
    end
  end
  ```
- [x] Run `bundle exec fastlane test` locally and confirm it passes. *(2026-11-05: 275 tests, 0 failures, lint clean)*
- [x] Add `fastlane/report.xml`, `fastlane/README.md`, `*.jks` and `*service-account*.json` to `.gitignore`. *(also `*.keystore`, `fastlane/test_output`, `vendor/bundle`)*
- [x] Change the workflow to use Fastlane: *(done before local verification; don't push until `Gemfile.lock` exists)*
  - [x] add `- uses: ruby/setup-ruby@v1` with `bundler-cache: true` after setup-gradle
  - [x] replace the `./gradlew ...` step with `bundle exec fastlane test`
- [ ] Open a PR and confirm the check is still green.

---

## Phase 3: Signed builds to testers (optional)

- [ ] Create a keystore: `keytool -genkey -v -keystore release.jks -alias entertainment -keyalg RSA -keysize 2048 -validity 10000`
- [ ] Back up the keystore and its passwords somewhere other than the repo.
- [ ] In `app/build.gradle.kts` (Step 2 in `Enhancements.md`):
  - [ ] a release `signingConfig` that reads `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` from env vars
  - [ ] `versionCode` from `-PversionCode`, falling back to 1
- [ ] Turn on R8 (`isMinifyEnabled = true`) and fix the keep rules for kotlinx.serialization, Retrofit and Hilt.
- [ ] Confirm `./gradlew assembleRelease` builds and the app runs.
- [ ] Add the secrets:
  - [ ] `KEYSTORE_BASE64`: `base64 -i release.jks | pbcopy`
  - [ ] `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
  - [ ] `FIREBASE_APP_ID`
  - [ ] `FIREBASE_SA_JSON` (service account with the App Distribution Admin role)
- [ ] Run `bundle exec fastlane add_plugin firebase_app_distribution`.
- [ ] Add a `beta` lane: `test`, then assemble the release with `versionCode = BUILD_NUMBER`, then `firebase_app_distribution` to the `qa` group.
- [ ] Add a `beta` workflow job:
  - [ ] `needs: test`, and only runs on pushes to `main`
  - [ ] decode the keystore into `$RUNNER_TEMP/release.jks`
  - [ ] pass the secrets through `env:`, and `BUILD_NUMBER: ${{ github.run_number }}`
- [ ] Merge to `main` and confirm the QA group gets the App Distribution email.
- [ ] Run `apksigner verify --print-certs app-release.apk` and confirm the release key signed it.

### Later
- [ ] Play Store: upload the first AAB to the internal track by hand, then add an `internal` lane that runs `upload_to_play_store`, using the `PLAY_JSON_KEY` secret.
- [ ] Firebase Test Lab: add a `build_for_test_lab` lane (`assembleDebug assembleDebugAndroidTest`) and a separate job. Use 1–2 devices on PRs, a full device matrix nightly, and don't make it a required check.
- [ ] Restrict the TMDB key on the provider side. It ships inside the APK no matter what CI does.

---

## Implementation log

### 2026-11-03: Phase 1 implemented locally (Claude)

**1. Ran the CI command locally to see what CI would hit.**
`./gradlew lintDebug testDebugUnitTest --continue`, using Android Studio's JDK 21 (`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`).
- This confirmed JDK 21: `gradle/gradle-daemon-jvm.properties` sets `toolchainVersion=21`, so the Gradle daemon has to be 21. The shell's default `java` is 17; Gradle only works because it finds 21.
- The first run failed with 3 broken tasks:

| Task | Error | Cause |
|---|---|---|
| `:core:compileDebugUnitTestKotlin` | `Unresolved reference 'mockwebserver3'` | `SessionRefreshFlowTest` uses MockWebServer, but `core` never declared it (the catalog entry `okhttp-mockwebserver` already existed) |
| `:feature:tabs:pos:lintDebug` | 3 × `MissingPermission` (ACCESS_NETWORK_STATE) | `AndroidNetworkMonitor` calls `ConnectivityManager`, but the POS manifest declares no permissions. It only worked at runtime because WorkManager merges that permission in. |
| `:app:lintDebug` | `MissingPermission` on `NotificationManagerCompat.notify` | `canPost()` is checked, but lint can't follow the check, and the grant can be revoked between the check and `notify()` |

**2. Fixes:**
- `core/build.gradle.kts`: added `testImplementation(libs.okhttp.mockwebserver)`.
- `feature/tabs/pos/src/main/AndroidManifest.xml`: added `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`. The module that uses the permission now declares it.
- `app/.../push/PushNotifications.kt`: wrapped `notify()` in `try { } catch (_: SecurityException) { }`.
- `gradle.properties`: `-Xmx2048m` → `-Xmx4g`.

**3. Reran it:** `BUILD SUCCESSFUL`.
- Unit tests: 245, 0 failures (pos 195, app 17, toprated 16, core 11, 6 other modules with 1 each).
- Lint: 0 errors. Warnings are left as they are.

**4. Added `.github/workflows/android.yml`.** It's the Phase 1 workflow above, plus:
- `workflow_dispatch`, so you can run it by hand from the Actions tab
- the job is named `test`, which is the name to require in branch protection
- comments explaining the JDK 21 pin and the empty secret on fork PRs

I checked that the YAML parses (Ruby `YAML.load_file`), that `gradlew` is executable, and that `gradle-wrapper.jar` and `gradle-daemon-jvm.properties` are tracked in git. It hasn't run on a GitHub runner yet.

**Still to do (yours, or tell me to do it):**
- [ ] Commit. CI only sees committed files, and this branch (`feature/movie-reviews`) has a lot of uncommitted and untracked work, including `feature/tabs/pos/src/` and `core/src/test/.../session/`. Everything the build needs has to be committed, or CI fails even though the local build passes.
- [ ] Push and open a PR into `main`. That triggers the first run.
- [ ] Add the `TMDB_API_KEY` secret. The build compiles without it (it falls back to `""`), so this isn't blocking.
- [ ] Turn on branch protection once the `test` check has run once.
- [ ] Fix the 3 high-severity POS bugs from the test review (yours).

### 2026-11-05: Phase 2 files written (Claude)

Couldn't run anything locally: there's no Homebrew, no rbenv, and only the system Ruby 2.6. So this is everything that doesn't need Ruby 3.x:
- `.ruby-version` = `3.3.6`. Ruby 3.4+ stops bundling some gems Fastlane needs, so 3.3 is the safe line.
- `Gemfile`: `fastlane ~> 2.225`, plus `eval_gemfile` of `fastlane/Pluginfile` so `add_plugin` works in Phase 3.
- `fastlane/Appfile`: `package_name("com.example.entertainment")`. No `json_key` until the Play lanes exist.
- `fastlane/Fastfile`: lanes `test` (same command as Phase 1 CI), `test_pos`, `build_debug`.
- `.gitignore`: Fastlane outputs, keystores, service-account JSONs, `vendor/bundle`.
- `.github/workflows/android.yml`: `ruby/setup-ruby@v1` with `bundler-cache: true` after setup-gradle. The test step is now `bundle exec fastlane test`.

Checked: YAML parses, `ruby -c` passes on `Fastfile` and `Gemfile`. Not run.

**Still to do (yours):**
- [ ] Install Homebrew, then `brew install rbenv ruby-build`, `rbenv init`, `rbenv install 3.3.6`.
- [ ] `bundle install` → creates `Gemfile.lock`. Then `bundle lock --add-platform x86_64-linux` so the Linux runner can use it.
- [ ] `bundle exec fastlane test` → should match Phase 1 (275 tests, lint clean).
- [ ] Commit `.ruby-version`, `Gemfile`, `Gemfile.lock`, `fastlane/`, then push and check the `test` check is green.

### 2026-11-05: Ruby installed, `fastlane test` green (Claude)
- The user installed Homebrew 7.0.12. Claude ran `brew install rbenv ruby-build openssl@3 libyaml`, added `eval "$(rbenv init - zsh)"` to `~/.zshrc`, and ran `rbenv install 3.3.6` (with `RUBY_CONFIGURE_OPTS=--with-openssl-dir=$(brew --prefix openssl@3)`).
- `bundle install`: fastlane 2.241.2. `bundle lock --add-platform x86_64-linux`.
- `bundle exec fastlane test` (JAVA_HOME = Android Studio JBR 21): BUILD SUCCESSFUL, 275 tests, 0 failures, lint clean.
- Note: installing `openssl@3` unlinked `openssl@4` in Homebrew. Harmless for Ruby, but run `brew link openssl@4` if something else needed it on PATH.

**Still to do:** commit `.ruby-version`, `Gemfile`, `Gemfile.lock`, `fastlane/`, plus Phase 1 files and `feature/tabs/pos/src/`. Push, open a PR, and confirm the `test` check is green on the runner.

---

## Known pitfalls
- **Robolectric and compileSdk 37.** `feature/tabs/pos/src/test/resources/robolectric.properties` pins `sdk=35`. Don't remove it.
- **First Robolectric run.** It downloads a large SDK jar. That's why the workflow caches `~/.m2/repository/org/robolectric`.
- **SDK platform 37.1** may not be on the runner. If AGP's auto-download is flaky, add a step before Gradle: `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.1"`.
- **Fork PRs** don't get secrets. The TMDB key falls back to `""`, so the build still compiles. No unit test should call the real API.
- **MockK agent warnings** on newer JDKs: add `-XX:+EnableDynamicAgentLoading` to the unit tests' `jvmArgs` if they show up.
- **Leaked WorkManager databases** in the tests: `PosSyncSchedulerTest` needs an `@After` that calls `WorkManagerTestInitHelper.closeWorkDatabase()`.
- **Update `Enhancements.md`** to match: JDK 21, secrets through `env:`, and the Robolectric cache step.
