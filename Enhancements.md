# Enhancements: CI/CD with Fastlane + Firebase Test Lab

Status: **not yet enabled**. This is the plan and reference for adding it to this project.
Step-by-step working list: `CICD checklist.md`. Order: plain Gradle CI first, then Fastlane, then signed builds.

---

## 1. TL;DR

- **GitHub Actions** is the trigger. It runs on every PR and on every merge to `main`.
- **Fastlane** is the script. It puts "lint → test → build → sign → upload" into named *lanes*, and the same lanes run on a laptop and in CI.
- **Firebase Test Lab** runs instrumented/UI tests on real devices across a list of OS versions.
- **Security:** CI injects secrets (API keys, the keystore, the Play upload credential) at build time, so they never sit in git or on developer laptops. Signing keys and upload credentials never go into the APK at all.

> Interview one-liner (once it's actually set up):
> "I set up a Fastlane + GitHub Actions pipeline that runs lint and unit tests on every PR. Merges to main produce a signed build in Firebase App Distribution, with no manual steps. Secrets are injected from CI and never committed, and instrumented tests run on a Firebase Test Lab device matrix."

---

## 2. Where this project is today

| Area | Current state | Gap |
|---|---|---|
| CI | None (no `.github/workflows/`) | Nothing checks PRs |
| POS sources | `feature/tabs/pos/src/` untracked in git | CI would build an empty module and `app` fails |
| Unit tests | 195 POS tests (Robolectric 4.16, `sdk=35`) | Need JDK 21 on CI and a Robolectric cache |
| Lint | Never run | First `lintDebug` will likely fail, so a baseline is needed |
| Fastlane | Not installed, no `fastlane/` | Releases would be manual |
| API key | `TMDB_API_KEY` read from `keys.properties` (gitignored) into `BuildConfig` in `app` and `core` | CI has no way to get the key yet |
| `google-services.json` | Committed to git | OK for Firebase (it's an identifier, not a secret), but restrict the key in Google Cloud Console |
| Release signing | No `signingConfig` for release | Can't produce an uploadable build |
| Versioning | `versionCode = 1` hardcoded | Play/Firebase reject duplicate version codes |
| R8 / minify | `isMinifyEnabled = false` | No obfuscation and no mapping file to upload |
| Instrumented tests | Only `ExampleInstrumentedTest.kt` | Test Lab has little to run yet |

---

## 3. How Fastlane helps with API keys and secrets (the honest version)

**Be precise about this in interviews.** "Fastlane keeps secrets out of the APK" is only half true, and a good interviewer will push on it.

### What Fastlane + CI actually solve

| Secret | Without CI/Fastlane | With CI/Fastlane |
|---|---|---|
| Release keystore + passwords | On someone's laptop, maybe emailed or put in Slack | Stored as encrypted GitHub Secrets, decoded to a temp file during the job, deleted afterwards |
| Play Console service-account JSON | Downloaded locally by whoever releases | CI secret, used only by the `supply` / `upload_to_play_store` step |
| Firebase App Distribution token | Personal login on a dev machine | Service account in CI |
| `TMDB_API_KEY` | In `keys.properties` on each dev machine | CI writes `keys.properties` from a secret at build time; never committed |
| Different keys per environment (dev/staging/prod) | Hand-edited files, easy to ship the wrong one | Each lane picks its own secret set, e.g. the `beta` lane uses staging keys |

The signing keystore and upload credentials are **build-time only**. They never go into the binary. That's where Fastlane really helps.

### What Fastlane does NOT solve

Any key the app uses **at runtime**, like `TMDB_API_KEY` in `BuildConfig`, **ships inside the APK**. Anyone can pull it out with `apktool` or `strings`. CI keeps it out of git, but it doesn't keep it out of the binary.

To actually protect a runtime key, you need one of these:
1. **Backend proxy (best):** the app calls your server, and the server adds the TMDB key. The key never reaches the device.
2. **Restrict the key** on the provider side (package name + SHA-1 restrictions where the provider supports them, quotas, rotation).
3. **Make extraction harder:** R8 obfuscation, NDK storage, Play Integrity / App Check. These slow an attacker down but don't stop one.

> Interview answer: "CI and Fastlane keep secrets out of source control and off developer machines. They also keep build-time credentials like the keystore and Play upload key out of the artifact entirely. For runtime API keys I'm clear that they're extractable from any client, so the real fix is a backend proxy plus key restrictions. The pipeline helps there by injecting different keys per environment, so prod keys only exist in the prod lane."

---

## 4. Advantages of Fastlane in this project

1. **Repeatable builds:** a clean runner with pinned JDK, Gradle and Fastlane versions. No more "works on my machine."
2. **No manual release steps:** signing, version bump, packaging, upload and release notes all happen in one lane. Nobody forgets to bump `versionCode` or uploads a debug build by mistake.
3. **PRs are gated:** lint + unit tests run before merge, so a broken `main` gets much rarer.
4. **Faster feedback:** every merge to `main` gives QA a build within minutes.
5. **Secrets stay centralised** (see §3). When someone leaves the team, you rotate a secret in one place.
6. **The release process is code:** the `Fastfile` is reviewed in PRs like any other change. The knowledge doesn't depend on one person.
7. **Store metadata as code:** `supply` keeps changelogs, localized descriptions and screenshots in git, separate from the binary. You can update the store listing without a new build.
8. **Artifacts go to the right places:** R8 mapping files and native symbols go to Crashlytics/Sentry/Play after the build. Crash reports stay readable and the APK stays small.
9. **Works for iOS too:** if the KMP/iOS plan comes back, the same tool handles `match` (certificates) and `pilot` (TestFlight).
10. **Large plugin ecosystem:** Firebase App Distribution, Slack notifications, Test Lab, version helpers.

## 5. Disadvantages / trade-offs

1. **Ruby dependency:** Android devs now maintain a Ruby toolchain (`Gemfile`, Bundler). The system Ruby on macOS is old, so you need `rbenv`/`asdf` or CI's `ruby/setup-ruby`.
2. **Another layer to debug:** when a lane fails you're reading Fastlane output wrapped around Gradle output, so it's harder to tell which layer failed.
3. **It overlaps with Gradle and GitHub Actions:** for a small app, the Gradle Play Publisher plugin plus plain Actions steps can do most of this without Ruby. Fastlane pays off when you have several lanes, environments or platforms.
4. **Plugins can be poorly maintained:** some community plugins lag behind Google API changes.
5. **Secret management is now critical:** CI secrets are a high-value target. You need branch protection, no secrets for fork PRs, and least-privilege service accounts.
6. **CI minutes cost money:** Test Lab device minutes and macOS runners (for iOS) aren't free, so you have to pick device matrices carefully.
7. **Setup cost up front:** keystore handling, the Play service account and the first manual upload (Play requires the first AAB to be uploaded by hand) take a day or two.

---

## 6. Configuration process (step by step)

### Step 0: Prerequisites
- Ruby ≥ 3.1 via `rbenv` (`brew install rbenv ruby-build`, `rbenv install 3.3.x`, `rbenv local 3.3.x`). This Mac only has system Ruby 2.6.
- JDK 21. AGP 9 runs on 17, but Robolectric needs 21 for SDK 35, which the POS tests use. Android Studio's bundled JDK is already 21.
- A release keystore (`keytool -genkey -v -keystore release.jks -alias entertainment -keyalg RSA -keysize 2048 -validity 10000`). **Back it up.** If you lose it, you can't update the app on Play unless you use Play App Signing.
- For Play uploads: a Google Cloud service account with "Release manager" access in Play Console. The first AAB must be uploaded manually.
- For Firebase App Distribution: the Firebase app ID plus a service account with the "Firebase App Distribution Admin" role.

### Step 1: Install Fastlane with Bundler
```bash
# project root
cat > Gemfile <<'EOF'
source "https://rubygems.org"
gem "fastlane"
EOF
bundle install          # creates Gemfile.lock -> commit it
bundle exec fastlane init   # choose Android, package com.example.entertainment
bundle exec fastlane add_plugin firebase_app_distribution
```
Commit `Gemfile`, `Gemfile.lock`, `fastlane/Fastfile`, `fastlane/Appfile` and `fastlane/Pluginfile`.
Also commit `.ruby-version`.
Add `fastlane/report.xml`, `fastlane/README.md` (generated), `*.jks` and `*service-account*.json` to `.gitignore`.

### Step 2: Release signing from environment variables (`app/build.gradle.kts`)
```kotlin
android {
    signingConfigs {
        create("release") {
            // Set by CI; missing locally, so local release builds stay unsigned.
            System.getenv("KEYSTORE_PATH")?.let { storeFile = file(it) }
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    defaultConfig {
        // CI passes -PversionCode=<run number>; local builds stay at 1.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true   // produces mapping.txt for Crashlytics/Play
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```
> Turning on R8 needs keep rules for kotlinx.serialization, Retrofit and Hilt. Test a release build locally before turning it on in CI.

### Step 3: `fastlane/Appfile`
```ruby
package_name("com.example.entertainment")
json_key_data_raw(ENV["PLAY_JSON_KEY"])   # only needed for Play lanes
```

### Step 4: `fastlane/Fastfile`
```ruby
default_platform(:android)

platform :android do
  desc "PR gate: lint + unit tests across all modules"
  lane :test do
    gradle(task: "lintDebug testDebugUnitTest", flags: "--continue")
  end

  desc "POS module unit tests only"
  lane :test_pos do
    gradle(task: ":feature:tabs:pos:testDebugUnitTest")
  end

  desc "Debug APK + androidTest APK for Firebase Test Lab"
  lane :build_for_test_lab do
    gradle(tasks: ["assembleDebug", "assembleDebugAndroidTest"])
  end

  desc "Signed release APK to Firebase App Distribution (QA)"
  lane :beta do
    test
    gradle(
      task: "assemble",
      build_type: "Release",
      properties: { "versionCode" => ENV.fetch("BUILD_NUMBER", "1") }
    )
    firebase_app_distribution(
      app: ENV["FIREBASE_APP_ID"],
      service_credentials_json_data: ENV["FIREBASE_SA_JSON"],
      groups: "qa",
      release_notes: changelog_from_git_commits(commits_count: 10, pretty: "- %s")
    )
  end

  desc "Signed AAB to Play internal track"
  lane :internal do
    gradle(
      task: "bundle",
      build_type: "Release",
      properties: { "versionCode" => ENV.fetch("BUILD_NUMBER", "1") }
    )
    upload_to_play_store(
      track: "internal",
      aab: lane_context[SharedValues::GRADLE_AAB_OUTPUT_PATH],
      mapping: "app/build/outputs/mapping/release/mapping.txt",
      skip_upload_metadata: false,      # uses fastlane/metadata/android/<locale>/
      skip_upload_screenshots: true
    )
  end
end
```
Store metadata: run `bundle exec fastlane supply init` once. It pulls the current listing into `fastlane/metadata/android/en-US/` (title, descriptions, `changelogs/<versionCode>.txt`). Edit those files in git after that.

### Step 5: GitHub Secrets
Repo → Settings → Secrets and variables → Actions:

| Secret | Contents |
|---|---|
| `TMDB_API_KEY` | The TMDB key |
| `KEYSTORE_BASE64` | `base64 -i release.jks \| pbcopy` |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Signing values |
| `FIREBASE_APP_ID` | From Firebase project settings |
| `FIREBASE_SA_JSON` | Service account JSON (App Distribution + Test Lab roles) |
| `PLAY_JSON_KEY` | Play Console service account JSON |

### Step 6: GitHub Actions workflow (`.github/workflows/android.yml`)
Start with plain Gradle: replace `bundle exec fastlane test` with `./gradlew lintDebug testDebugUnitTest --continue --stacktrace` and drop the Ruby step. Switch to Fastlane once Steps 1–4 are done. `CICD checklist.md` has the Gradle-only version.

Before the first run:
- Commit `feature/tabs/pos/src/`.
- Run `./gradlew lintDebug` locally. If it's noisy, add `lint { baseline = file("lint-baseline.xml") }`, run `./gradlew updateLintBaseline`, and commit the baselines.
- Set `org.gradle.jvmargs=-Xmx4g` in `gradle.properties`.

```yaml
name: Android CI

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4      # Gradle + build cache
      - name: Cache Robolectric jars
        uses: actions/cache@v4
        with:
          path: ~/.m2/repository/org/robolectric
          key: robolectric-${{ hashFiles('gradle/libs.versions.toml') }}
      - uses: ruby/setup-ruby@v1
        with: { bundler-cache: true }
      - name: Write keys.properties
        env:
          TMDB_API_KEY: ${{ secrets.TMDB_API_KEY }}
        run: echo "TMDB_API_KEY=${TMDB_API_KEY}" > keys.properties
      - run: bundle exec fastlane test
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: test-reports
          path: |
            **/build/reports/
            **/build/test-results/
          retention-days: 14

  beta:
    needs: test
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 20 }                  # for changelog_from_git_commits
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 21 }
      - uses: gradle/actions/setup-gradle@v4
      - uses: ruby/setup-ruby@v1
        with: { bundler-cache: true }
      - name: Prepare secrets
        env:
          TMDB_API_KEY: ${{ secrets.TMDB_API_KEY }}
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: |
          echo "TMDB_API_KEY=${TMDB_API_KEY}" > keys.properties
          echo "${KEYSTORE_BASE64}" | base64 --decode > "$RUNNER_TEMP/release.jks"
      - run: bundle exec fastlane beta
        env:
          KEYSTORE_PATH: ${{ runner.temp }}/release.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          FIREBASE_APP_ID: ${{ secrets.FIREBASE_APP_ID }}
          FIREBASE_SA_JSON: ${{ secrets.FIREBASE_SA_JSON }}
          BUILD_NUMBER: ${{ github.run_number }}
```
Notes:
- GitHub doesn't pass secrets to workflows from fork PRs. Since `keys.properties` falls back to `""`, those builds still compile; only tests that need the network would fail.
- `$RUNNER_TEMP` is wiped after each job, so the keystore never outlives the job.
- Secrets go through `env:`, never `${{ secrets.* }}` inline in `run:`, which avoids shell injection and quoting problems.
- A POS-only job can be added with `dorny/paths-filter` on `feature/tabs/pos/**` and `core/**`. Put the filter in the job's `if:`, not in `on.paths`; otherwise a required check that gets skipped stays "pending" forever.
- Optional coverage: apply Kover to `:feature:tabs:pos` and exclude Hilt, Room and Compose generated code. Gate it with `koverVerifyDebug` at `minBound(60)`. First run `./gradlew :feature:tabs:pos:tasks --group verification` to check that the tasks exist on AGP 9.

Runner pitfalls:
- `feature/tabs/pos/src/test/resources/robolectric.properties` pins `sdk=35`. Robolectric 4.16 doesn't support compileSdk 37, so leave the pin in place.
- Platform 37.1 may be missing on the runner. If the download is flaky, add `yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager "platforms;android-37.1"` before the Gradle steps.
- MockK agent warnings on newer JDKs: add `-XX:+EnableDynamicAgentLoading` to the unit tests' `jvmArgs`.
- `PosSyncSchedulerTest` leaks WorkManager databases. Add an `@After` that calls `WorkManagerTestInitHelper.closeWorkDatabase()`.

### Step 7: Branch protection
Settings → Branches → `main` → require the `test` check to pass, require PRs, and block force pushes. The check name only appears in the picker after the workflow has run once. Without this, CI only tells you something broke; it doesn't stop the merge.

### Step 8: Verify it works
1. Open a PR that breaks a unit test. The check goes red and merge is blocked.
2. Fix it and merge. The `beta` job runs and the QA group gets an App Distribution email with the changelog.
3. Download the APK and confirm it's signed with the release key: `apksigner verify --print-certs app-release.apk`.
4. Run `strings` on the APK and confirm the keystore password isn't anywhere in it. (The TMDB key *will* be there; see §3.)

---

## 7. Proving the speed gain (where a "~30% faster" number comes from)

Never quote a percentage you didn't measure. Here's how to get a real one:

**Before (baseline):** time the manual flow end to end. That's pull → run tests locally → bump version → build release → find the keystore → sign → upload → write release notes → notify QA. Do it 3–5 times and take the median. Manual flows are usually 20–40 min of someone's attention.

**After:** look at GitHub Actions job durations (the Actions tab shows them) from merge to "build available to QA."

**Where the speed actually comes from:**
| Change | Why it's faster |
|---|---|
| Gradle build cache + config cache via `setup-gradle` | Unchanged modules aren't recompiled. This multi-module setup (`core`, `feature:tabs:*`) benefits a lot |
| Validation runs first and fails fast | Lint + unit tests (~minutes) run before the expensive release build, so a bad commit never reaches packaging |
| Parallel jobs | `test` on PRs, `beta` only on `main`. Test Lab can run in its own job alongside them |
| `concurrency: cancel-in-progress` | Pushing again cancels the stale run instead of queueing behind it |
| No human hand-offs | The biggest win is removing *waiting time* (finding the keystore, asking who has Play access), not CPU time |
| Running only affected modules (advanced) | Only test the modules a PR touches, using a path filter or a dependency-graph plugin |

---

## 8. Firebase Test Lab

### Running it
```bash
gcloud firebase test android run \
  --type instrumentation \
  --app app/build/outputs/apk/debug/app-debug.apk \
  --test app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --device model=Pixel2.arm,version=30 \
  --device model=oriole,version=33 \
  --device model=akita,version=34 \
  --num-flaky-test-attempts 2 \
  --timeout 15m \
  --results-bucket gs://<bucket> --results-dir "run-$GITHUB_RUN_NUMBER"
```
Add this as a CI job after `fastlane build_for_test_lab`, authenticated with `google-github-actions/auth` using `FIREBASE_SA_JSON`.
List the available devices with `gcloud firebase test android models list`.

### Choosing the device/OS matrix
- Base it on **real usage**: the top devices and OS versions from Play Console → Statistics / Android vitals.
- Always include the **edges**: `minSdk` (24), `targetSdk` (36), and one large-screen or foldable device.
- **Tiered:** PRs run on 1–2 devices (fast and cheap). Nightly or pre-release runs the full matrix.
- Local equivalent: **Gradle Managed Devices** (`testOptions.managedDevices`) run the same matrix on emulators without Test Lab costs.

### Prompt: "How do you identify flaky tests?"
- `--num-flaky-test-attempts N` reruns failures. Test Lab marks a test **flaky** if it fails and then passes on a retry, which is different from a consistent **failure**.
- Track it over time: the same test passing and failing on the **same commit** is flaky by definition. Watch the flaky rate per test in Test Lab history or a dashboard.
- Usual causes: missing idling resources, `Thread.sleep`, animations not disabled, shared state between tests, real network calls. Fix them with Espresso IdlingResources or Compose `waitUntil`, Android Test Orchestrator with `clearPackageData`, fake repositories via Hilt test modules, and disabling animations on the device.
- Policy: **quarantine** a known flaky test (move it to a non-blocking suite with a ticket) so it doesn't train the team to ignore red builds. Never just keep retrying it.

### Prompt: "How do you confirm a device-specific failure?"
- The test fails **consistently** on one model/OS and passes on the others in the same run. That's a device-specific failure, not flakiness.
- Use Test Lab's per-device **video, screenshots, logcat and performance data** to see what happened.
- Reproduce it: rerun on only that device (`--device model=X,version=Y`), then try the same OS on a different model. That tells you whether it's the **OS version** or the **vendor/model**.
- Common causes: OEM behaviour (battery optimisation, permission dialogs), API-level behaviour changes, screen size/density, WebView version.

### Prompt: "Do you rerun regression on all devices after the fix?"
- **No, not straight away.** First rerun on the **failing device**, plus one control device, to confirm the fix.
- Then the **normal PR tier** runs automatically when the fix merges.
- The **full matrix** runs in the scheduled nightly or pre-release run. That catches any regression the fix caused on other devices without paying for a full matrix on every commit.
- Exception: if the fix touches shared code (e.g. the networking layer in `core`), trigger the full matrix before release.

---

## 9. Interview Q&A (from my notes)

**Q: Which specific step did Fastlane help the team with?**
Signing and distribution. Before, the release keystore lived on one machine, and building, signing and uploading took a person 20–40 minutes. Now the `beta` lane does it on every merge to `main`, using secrets from CI. That removed a single point of failure and a manual hand-off.

**Q: Which step improved the developer process?**
The PR gate. `fastlane test` runs lint + unit tests across every module on every PR, and branch protection requires it to pass. Developers get a pass/fail in minutes and don't run the whole suite locally before pushing. Reviewers stop reviewing code that doesn't compile.

**Q: How did you get faster validation / earlier builds (~30%)?**
Fail-fast ordering (cheap checks before the release build), the Gradle build cache in CI (the app is multi-module, so unchanged modules are skipped), parallel jobs, cancelling stale runs, and removing the human waiting time from the release flow. *Measured as:* median time from merge to QA build, before vs after. (Use your real numbers from §7.)

**Q: What can Fastlane do beyond building the APK?**
- Secrets live in CI and are injected at build time; signing and upload credentials never enter the binary (§3, including the caveat about runtime keys).
- `supply` manages store metadata, changelogs and screenshots as files in git, separate from the binary.
- Pre-build validation lanes run lint, unit tests and static analysis before packaging.
- Mapping files and symbols are uploaded to Crashlytics, Sentry or Play after the build, so crashes stay readable and the APK stays small.

---

## 10. Rollout checklist

Full tickable version: `CICD checklist.md`.

- [ ] Commit `feature/tabs/pos/src/`
- [ ] Get `./gradlew lintDebug` passing (use a baseline if needed); raise Gradle heap to 4g
- [ ] Add the Gradle-only `.github/workflows/android.yml` (JDK 21, Robolectric cache)
- [ ] Install Ruby via rbenv, add `Gemfile`, run `bundle exec fastlane init`
- [ ] Create a release keystore and back it up; enrol in Play App Signing
- [ ] Add a release `signingConfig` and a `versionCode` property to `app/build.gradle.kts`
- [ ] Turn on R8 for release and fix keep rules; check that a local release build runs
- [ ] Write the `Fastfile` lanes: `test`, `build_for_test_lab`, `beta`, `internal`
- [ ] Add the GitHub Secrets (§5)
- [ ] Switch the workflow's test step to `bundle exec fastlane test`
- [ ] Turn on branch protection on `main`
- [ ] Restrict the TMDB key on the provider side; consider a backend proxy
- [ ] Write real instrumented tests (Compose UI tests for the main screens) so Test Lab has something to run
- [ ] Add the Test Lab job: PR tier with 1–2 devices, nightly full matrix
- [ ] Record before/after timings for the interview number
