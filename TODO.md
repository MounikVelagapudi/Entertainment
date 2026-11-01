# TODO

## 1. Try multiple Claude agents
- [ ] Run a parallel code review: separate agents for correctness, Compose performance, and tests, then compare what each one finds
- [ ] Use `isolation: worktree` so two agents work on separate branches without stepping on each other (e.g. one on the search tab, one on CI)
- [ ] Try a Plan agent to design a feature, then a separate agent to implement it, then `/code-review` on the result
- [ ] Note token cost and time against a single-agent run, for the multi-agent interview talking points

## 2. Implement Enhancements.md
Follow the rollout checklist in [Enhancements.md](Enhancements.md#10-rollout-checklist).
- [ ] Fastlane installed with Bundler; lanes: `test`, `build_for_test_lab`, `beta`, `internal`
- [ ] Release keystore and a `signingConfig` that reads from environment variables
- [ ] `versionCode` passed in from CI
- [ ] R8 on for release, with keep rules fixed
- [ ] GitHub Secrets and `.github/workflows/android.yml`
- [ ] Branch protection on `main`
- [ ] Restrict the TMDB key; consider a backend proxy
- [ ] Record before/after timings

## 3. Free CI/CD + test suite
A pipeline that costs $0, so it can run before any paid setup (Play account, Test Lab Blaze plan).

**Free tools**
- GitHub Actions: unlimited minutes on public repos, 2,000 min/month on private repos (Linux runners)
- Gradle Managed Devices: emulator tests on the CI runner, no Test Lab bill
- Firebase App Distribution: free
- Firebase Test Lab on the Spark (free) plan: small daily quota of virtual and physical device runs, fine for a nightly smoke test

**Test suite to add**
- [ ] Unit tests: ViewModels and use cases (MockK + Turbine + `MainDispatcherRule`), repositories with fake data sources, the Room DAO and `OfflineFirstCache`
- [ ] Compose UI tests: home tabs render, navigation to the detail screen, search input/results, error and empty states
- [ ] Hilt test modules with fake repositories so UI tests never hit the network
- [ ] Coverage report (Kover or JaCoCo), uploaded as a CI artifact

**Pipeline**
- [ ] PR job: `lintDebug testDebugUnitTest` plus coverage
- [ ] Emulator job: Gradle Managed Device (e.g. Pixel 6, API 34) running `connectedDebugAndroidTest`, on `main` or nightly to save minutes
- [ ] Optional: nightly Test Lab smoke run within the free quota
- [ ] Upload test reports as artifacts; require the PR job to pass before merging

## 4. Firebase Test Lab test flows (device + OS setup)
Answers to the interview questions on flaky and device-specific failures: [Enhancements.md §8](Enhancements.md#8-firebase-test-lab).
The Firebase project already exists (`app/google-services.json`), so Test Lab just needs turning on.

### Process
- [ ] **Enable Test Lab:** Firebase console → Test Lab. Stay on the Spark (free) plan to start; check the current daily quota of virtual and physical runs.
- [ ] **Install the gcloud CLI:** `brew install --cask google-cloud-sdk`, then `gcloud auth login` and `gcloud config set project <firebase-project-id>`.
- [ ] **See which devices and OS versions exist:**
  ```bash
  gcloud firebase test android models list            # model IDs, physical vs virtual, supported API levels
  gcloud firebase test android versions list          # OS versions
  gcloud firebase test android locales list
  ```
- [ ] **Start with a Robo test** (no test code needed, since there's only `ExampleInstrumentedTest` right now). It crawls the UI and reports crashes:
  ```bash
  ./gradlew assembleDebug
  gcloud firebase test android run --type robo \
    --app app/build/outputs/apk/debug/app-debug.apk \
    --device model=MediumPhone.arm,version=34 --timeout 5m
  ```
- [ ] **Write real instrumented tests** (Compose UI tests; see task 3), then build both APKs:
  `./gradlew assembleDebug assembleDebugAndroidTest`
- [ ] **Put the device/OS matrix in a config file**, `testlab.yml`, kept in the repo so the matrix is reviewed like any other change:
  ```yaml
  # Run with: gcloud firebase test android run testlab.yml:pr-smoke
  pr-smoke:                      # every PR: fast and cheap
    type: instrumentation
    app: app/build/outputs/apk/debug/app-debug.apk
    test: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    device:
      - {model: MediumPhone.arm, version: 34}
    num-flaky-test-attempts: 1
    timeout: 10m

  nightly-full:                  # nightly / pre-release: the whole matrix
    type: instrumentation
    app: app/build/outputs/apk/debug/app-debug.apk
    test: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    device:
      - {model: Pixel2.arm, version: 28}                       # older-OS edge
      - {model: MediumPhone.arm, version: 34}
      - {model: oriole, version: 33}                           # physical Pixel 6
      - {model: akita, version: 34}                            # physical Pixel 8a
      - {model: MediumTablet.arm, version: 34, orientation: landscape}
    num-flaky-test-attempts: 2
    num-uniform-shards: 2        # split tests across shards to finish faster
    use-orchestrator: true
    environment-variables: {clearPackageData: "true"}   # no shared state between tests
    timeout: 20m
  ```
  Model IDs change over time, so check them against `models list` before relying on them.
- [ ] **Pick the matrix from data:** Play Console → Android vitals → top devices and OS versions. Always include `minSdk` (24), `targetSdk` (36) and one large screen. Test Lab doesn't offer every API level, so cover 24 with the oldest version available or a Gradle Managed Device.
- [ ] **Run a subset of tests when needed:**
  `--test-targets "class com.example.entertainment.home.HomeScreenTest"` or `--test-targets "annotation androidx.test.filters.LargeTest"`
- [ ] **Rerun one device to confirm a device-specific failure:**
  `gcloud firebase test android run testlab.yml:nightly-full --device model=oriole,version=33`
- [ ] **Read the results:** Firebase console → Test Lab shows video, screenshots, logcat and performance for each device. Tests that pass on a retry are marked *flaky*, which is different from *failed*.
- [ ] **Hook it into CI:**
  - Create a service account with the *Firebase Test Lab Admin* role and store its JSON as the `FIREBASE_SA_JSON` secret.
  - In the workflow, authenticate with `google-github-actions/auth@v2` and `google-github-actions/setup-gcloud@v2`.
  - Run `fastlane build_for_test_lab`, then `gcloud firebase test android run testlab.yml:pr-smoke`.
  - Add a nightly `schedule:` (cron) workflow that runs `testlab.yml:nightly-full`.
- [ ] **Optional, all in Gradle:** the Firebase Test Lab Gradle plugin (`com.google.firebase.testlab`, still experimental) lets you declare Test Lab devices in `build.gradle.kts` the same way as Gradle Managed Devices, then run `./gradlew <device>DebugAndroidTest`. Compare it with the YAML approach and keep one of them.
