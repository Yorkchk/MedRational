# MedRational: CI and Tests Explained

What happens automatically every time you open or update a pull request in **MedRational-Android** or **MedRational** (backend): what runs, what it checks, what it does *not* check, how to read the results, how to run the same thing locally, and how to add tests.

> State as of 2026-10-08. The workflows were added in [MedRational-Android#2](https://github.com/Yorkchk/MedRational-Android/pull/2) and [MedRational#4](https://github.com/Yorkchk/MedRational/pull/4), both merged into `main`. This document is identical in both repos and covers both.

---

## Table of contents

1. [Overview](#1-overview)
2. [Android CI, step by step](#2-android-ci-step-by-step)
3. [Backend CI, step by step](#3-backend-ci-step-by-step)
4. [What is and isn't tested today](#4-what-is-and-isnt-tested-today)
5. [Reading results and debugging failures](#5-reading-results-and-debugging-failures)
6. [Running the exact CI checks locally](#6-running-the-exact-ci-checks-locally)
7. [Dependabot: automatic update PRs](#7-dependabot-automatic-update-prs)
8. [Making CI actually block bad merges](#8-making-ci-actually-block-bad-merges)
9. [Warnings from the first runs (to do)](#9-warnings-from-the-first-runs-to-do)
10. [Writing your first real tests](#10-writing-your-first-real-tests)
11. [Where to go next](#11-where-to-go-next)

---

## 1. Overview

| | Android | Backend |
|---|---|---|
| Workflow file | `.github/workflows/android-ci.yml` | `.github/workflows/backend-ci.yml` |
| Workflow name (Actions tab) | **Android CI** | **Backend CI** |
| Job name (status check) | `build` | `build` |
| Runs on | every PR (opened or new commits pushed) + every push to `main` | same |
| Machine | GitHub-hosted `ubuntu-latest` VM, fresh every run | same, plus a PostgreSQL 16 container |
| Main command | `./gradlew lint testDebugUnitTest assembleDebug` | `./mvnw -B verify` |
| First run duration | **5 min 28 s** | **1 min 20 s** |
| First run result | ✅ pass | ✅ pass |
| Output kept after the run | `lint-report` artifact (HTML) | none |

**Lifecycle of one PR:**

```
you push commits to a branch with an open PR
        │
        ▼
GitHub sees `on: pull_request` → queues the workflow
        │
        ▼
a brand-new Ubuntu VM starts (nothing from your laptop exists on it)
        │
        ▼
steps run in order ──► any step fails? ──► job ❌, later steps skipped
        │                                     (except steps marked `if: always()`)
        ▼
all steps succeed ──► job ✅
        │
        ▼
result shown on the PR, the commit, and the Actions tab; the VM is destroyed
```

When the PR is merged, the push to `main` triggers the workflow once more. That keeps the README badge accurate for `main`.

---

## 2. Android CI, step by step

### 2.1 The file

```yaml
name: Android CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Lint, unit tests, and debug build
        run: |
          chmod +x gradlew
          ./gradlew lint testDebugUnitTest assembleDebug

      - name: Upload lint report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: lint-report
          path: app/build/reports/lint-results-debug.html
```

### 2.2 Each step explained

| # | Step | What it does | Why |
|---|---|---|---|
| 1 | `actions/checkout@v4` | Downloads the commit being tested onto the VM. For a PR, that's a temporary **merge of your branch into `main`**, so CI tests what `main` *would look like* after merging. | The VM starts empty. |
| 2 | `actions/setup-java@v4` (Temurin 17) | Installs JDK 17. | Android Gradle Plugin 8.7 needs JDK 17 to run. Your code still compiles to Java 11 bytecode (`jvmTarget = "11"`); that's separate. |
| 3 | `gradle/actions/setup-gradle@v4` | Sets up Gradle and **caches** downloaded dependencies and the Gradle distribution between runs. | The first run downloads everything (most of the 5.5 minutes). Later runs reuse the cache and get faster. |
| 4 | `chmod +x gradlew` | Marks the Gradle wrapper script as executable. | Windows doesn't store Linux's "executable" flag. Without this, Linux says `Permission denied`. |
| 4 | `./gradlew lint testDebugUnitTest assembleDebug` | Runs three Gradle tasks in one build (details below). | One Gradle invocation shares the work, e.g. compilation, between tasks. |
| 5 | Upload lint report (`if: always()`) | Saves `lint-results-debug.html` as a downloadable **artifact**. | `if: always()` makes it run **even when lint fails**, which is exactly when you want the report. |

### 2.3 What `lint` checks

**Android Lint** is a static analyzer: it reads your code, manifest, resources and Gradle files **without running the app**, and reports problems in categories like correctness, security, performance, accessibility, internationalization and usability. Examples: a missing `contentDescription` on an image, a hardcoded string that should be in `strings.xml`, an API used above your `minSdk`, an insecure `WebView` setting.

**How it decides pass or fail:**
- Each issue has a severity: *error*, *warning*, or *informational*.
- By default, **only errors fail the build** (`abortOnError = true`). Warnings are reported but don't make CI red.

**Current result** (same code as CI, run locally): **0 errors, 48 warnings**.

| Warnings | Lint ID | Meaning | Should you act? |
|---|---|---|---|
| 24 | `GradleDependency` | A newer version of a library is available | Dependabot opens PRs for these (§7). |
| 9 | `UseTomlInstead` | Some dependencies are declared inline in `app/build.gradle.kts` (`implementation("com.squareup.retrofit2:…")`) instead of in `gradle/libs.versions.toml` | Nice cleanup: move them into the version catalog. |
| 9 | `UnusedResources` | Resources (colors, strings…) that nothing references | Delete them to shrink the app. |
| 3 | `AndroidGradlePluginVersion` | A newer Android Gradle Plugin exists | Upgrade when convenient. |
| 3 | `ObsoleteLintCustomCheck` | A library ships lint checks built for an older lint version | Usually harmless; goes away with library updates. |

**To see them:** download the `lint-report` artifact from the run page (bottom, under **Artifacts**), unzip it, and open the HTML file. Or run `./gradlew lint` locally and open `app/build/reports/lint-results-debug.html`.

**To make lint stricter later** (for example, fail on warnings too), add this to `app/build.gradle.kts`:

```kotlin
android {
    lint {
        warningsAsErrors = true        // only once you're at 0 warnings
        abortOnError = true
        baseline = file("lint-baseline.xml")   // optional: accept today's warnings, fail on NEW ones
    }
}
```

A **baseline** is the professional way to adopt lint on an existing project. You run `./gradlew updateLintBaseline` once, which records the current 48 warnings as accepted, and from then on CI fails only when a PR introduces a **new** issue.

### 2.4 What `testDebugUnitTest` runs

It runs every test in `app/src/test/`. These are **local unit tests**: plain JVM tests on the CI machine, with no emulator and no Android framework (Android classes are stubs that throw if called).

**Today there is exactly one**, the Android Studio template:

```kotlin
// app/src/test/java/com/example/medrational_android/ExampleUnitTest.kt
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
```

It only proves the test infrastructure works. It tests none of your code. §10.1 shows how to write a real one.

The report is generated at `app/build/reports/tests/testDebugUnitTest/index.html` locally. CI doesn't upload it yet, but you could add another `upload-artifact` step for it.

**Not run in CI:** `app/src/androidTest/` (instrumented tests, currently the template `ExampleInstrumentedTest.useAppContext`). Those need an emulator or device (`./gradlew connectedAndroidTest`). See §11 for running an emulator in CI.

### 2.5 What `assembleDebug` checks

It builds the full debug APK:
1. Generates `BuildConfig` (including `BASE_URL`, which falls back to `http://10.0.2.2:8080/` on CI because there's no `local.properties`).
2. Compiles all Kotlin (any type error, unresolved reference, or wrong argument fails here).
3. Compiles Compose UI code through the Compose compiler plugin.
4. Merges the manifest and resources (duplicate or broken resources fail here).
5. Packages and signs the APK with the debug key.

**This is your main safety net today.** If someone breaks compilation anywhere in the app, CI goes red.

**Not built in CI:** the **release** variant (`assembleRelease`, with R8 shrinking). It was verified locally when R8 was enabled. Add `assembleRelease` to the command if you want CI to catch R8 problems such as missing keep rules; it adds a few minutes per run.

---

## 3. Backend CI, step by step

### 3.1 The file

```yaml
name: Backend CI

on:
  pull_request:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 5s
          --health-timeout 5s
          --health-retries 10

    env:
      SUPABASE_DB_HOST: localhost
      SUPABASE_DB_PORT: 5432
      SUPABASE_DB_DATABASE: postgres
      SUPABASE_DB_USERNAME: postgres
      SUPABASE_DB_PASSWORD: postgres
      JWT_SECRET: ci-only-dummy-secret-that-is-at-least-32-bytes-long
      CLOUDFLARE_ACCOUNT_ID: ci-dummy
      CLOUDFLARE_ACCESS_KEY_ID: ci-dummy
      CLOUDFLARE_SECRET_ACCESS_KEY: ci-dummy
      MAIL_HOST: localhost
      MAIL_PORT: 587
      MAIL_USERNAME: ci-dummy
      MAIL_PASSWORD: ci-dummy

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
          cache: maven

      - name: Build and test
        run: |
          chmod +x mvnw
          ./mvnw -B verify
```

### 3.2 The PostgreSQL service container

`services:` starts a **Docker container next to the job**. GitHub starts it first, then runs the health check (`pg_isready`) every 5 seconds, up to 10 times, and only starts your steps once Postgres reports ready. `ports: 5432:5432` makes it reachable from the job at `localhost:5432`.

It's a **brand-new, empty database every run**. Hibernate (`ddl-auto=update`) creates all your tables from the entities when the app starts. Nothing is shared with your Supabase database, and nothing persists after the run.

**Why a real Postgres and not H2** (an in-memory database)? H2 behaves differently from Postgres in data types, SQL dialect and constraints. Testing against the same engine as production catches problems H2 would hide.

### 3.3 The environment variables

Your config files contain placeholders like `${JWT_SECRET}` and `${MAIL_HOST}`. Spring **refuses to start** if a placeholder has no value and no default, so CI must give every one of them *some* value.

| Variables | CI value | Real connection made during the test? |
|---|---|---|
| `SUPABASE_DB_*` | point at the CI Postgres container | ✅ yes, the app connects and creates its schema |
| `JWT_SECRET` | a fake 50-character string | no (only used to sign tokens; must be ≥ 32 bytes for HMAC-SHA256) |
| `CLOUDFLARE_*` | `ci-dummy` | no; the `S3Client` bean is *created* but never called at startup |
| `MAIL_*` | `localhost` / `ci-dummy` | no; the mail sender connects only when an email is sent |

**Why not real values?** The workflow file is public, and CI must never be able to touch production data or send real email. If a future test ever needs a real key, it goes in **GitHub Secrets** (`${{ secrets.NAME }}`), never in the YAML.

**Why your `.env` doesn't help on CI:** `.env` is git-ignored, so it doesn't exist on the VM. Also, `MedRationalApplication.main` loads `.env` (with `ignoreIfMissing()`), but tests don't call `main`, so tests only see real environment variables, which is what the `env:` block provides.

**Rule:** when you add a new `${SOMETHING}` to `application.yaml` or `application.properties`, add it to `.env.example` **and** to this `env:` block, or CI will fail with `Could not resolve placeholder 'SOMETHING'`.

### 3.4 What `./mvnw -B verify` does

`-B` is batch mode (no colors or interactive output; cleaner logs). `verify` runs the Maven lifecycle **up to and including** the verify phase:

| Phase | What happens in this project |
|---|---|
| `validate` | Checks `pom.xml` is valid. |
| `compile` | Compiles `src/main/java` with Java 21, including Lombok annotation processing (`@Builder`, `@Getter`…). Any compile error fails here. |
| `test-compile` | Compiles `src/test/java`. |
| `test` | Runs the tests with Surefire: today, `MedRationalApplicationTests.contextLoads`. |
| `package` | Builds `target/MedRational-0.0.1-SNAPSHOT.jar` (the deployable app). |
| `verify` | Runs checks bound to this phase. None are configured yet; this is where coverage thresholds or integration tests would go (§11). |

### 3.5 What the single test actually proves

```java
@SpringBootTest
class MedRationalApplicationTests {
    @Test
    void contextLoads() { }
}
```

The method body is empty, but `@SpringBootTest` **starts the whole application** before running it. The test fails if startup fails. So passing proves:

- ✅ Every placeholder in the config resolves.
- ✅ The app can connect to PostgreSQL 16.
- ✅ Hibernate can create the schema from your entities on a real Postgres. Bad mappings, invalid column definitions and broken relationships fail here.
- ✅ **Every Spring Data repository method name is valid.** Spring parses derived queries like `findByUserIdAndFileId` at startup, so a typo in a field name fails here.
- ✅ Every `@Query` in JPQL is syntactically valid (also parsed at startup).
- ✅ All beans can be created and wired: no missing dependencies, no circular dependencies, no two beans of the same type when one is expected.
- ✅ The security configuration (`SecurityConfig`, `JwtAuthenticationFilter`, `CurrentUserGuard`) builds without errors.

What it does **not** prove:
- ❌ That any endpoint returns the right data or status code.
- ❌ That the security **rules** behave correctly (e.g. that `/users/{otherId}/favorites` really returns 403).
- ❌ Business logic: OTP expiry, favorite toggling, search filters, ZIP generation, analytics math.
- ❌ Integration with R2 or SMTP.

The log line to look for:

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

- **Failures** mean an assertion failed.
- **Errors** mean an exception, e.g. the context couldn't start.

Surefire also writes detailed reports to `target/surefire-reports/`.

---

## 4. What is and isn't tested today

| Area | Checked by CI? | How |
|---|---|---|
| Android app compiles (all Kotlin + Compose) | ✅ | `assembleDebug` |
| Android lint *errors* | ✅ | `lint` |
| Android lint *warnings* | ⚠️ reported only | `lint` report artifact |
| Android release build / R8 rules | ❌ | not in the workflow (local only) |
| Android ViewModel logic | ❌ | no tests yet (§10.1) |
| Android UI / navigation | ❌ | no instrumented tests in CI |
| Backend compiles | ✅ | `verify` → compile |
| Backend starts with a real Postgres | ✅ | `contextLoads` |
| JPA mappings / repository queries valid | ✅ | `contextLoads` (startup validation) |
| Backend endpoint behavior | ❌ | no MockMvc tests yet (§10.2) |
| Backend security rules (403s, admin-only) | ❌ | no tests yet (§10.2) |
| Android ↔ backend contract (JSON shapes) | ❌ | not covered; the `FavoriteResponse` mismatch bug would not have been caught |

**Bottom line:** today a green check means **"it builds and it starts"**. That's the foundation. The value grows with every test you add, because each one runs on every PR from then on, with no extra work.

---

## 5. Reading results and debugging failures

### 5.1 Where to look

| Place | How to get there | Best for |
|---|---|---|
| PR checks box | Bottom of the PR page, above the merge button | "Can I merge?" at a glance |
| Run page | PR → **Details**, or **Actions** tab → click the run | Logs, annotations, artifacts |
| Commit icons | Next to each commit in the commit list | History of which commits were green |
| Email | Automatic on failures of your pushes | Being notified |
| Terminal | `gh pr checks <#> --watch`, `gh run view --log-failed` | Staying in the terminal |

### 5.2 Anatomy of a run page

```
Android CI  #2                                  ← workflow name + run number
✓ build   5m 28s                                ← the job (click it)
   ✓ Set up job
   ✓ Run actions/checkout@v4
   ✓ Run actions/setup-java@v4
   ✓ Run gradle/actions/setup-gradle@v4
   ✓ Lint, unit tests, and debug build          ← click to expand the log
   ✓ Upload lint report
   ✓ Post ...                                   ← automatic cleanup steps
Annotations                                     ← warnings GitHub wants you to see (§9)
Artifacts: lint-report                          ← downloadable files
```

### 5.3 Debugging a ❌

1. Open the failed step (the one with the red X).
2. **Scroll to the bottom of its log.** The real error is almost always in the last 20–30 lines, just above `Error: Process completed with exit code 1`.
3. Reproduce it locally with the same command (§6).
4. Fix it on the **same branch**, commit, and push. CI re-runs on the PR automatically.
5. If the failure looks random (a network timeout while downloading dependencies), click **Re-run jobs** or run `gh run rerun <id>`.

### 5.4 Common failures and what they mean

**Android**

| Log message | Cause | Fix |
|---|---|---|
| `e: file:///…/Foo.kt:12:5 Unresolved reference: bar` | Kotlin compile error | Fix the code; check you committed every file. |
| `Lint found errors in the project; aborting build.` | Lint error (not a warning) | Open the lint-report artifact, fix it or (justified) suppress with `@SuppressLint("Id")`. |
| `FAILED` under `> Task :app:testDebugUnitTest`, with `ExampleTest > something FAILED` | A unit test assertion failed | Open the test report locally: `app/build/reports/tests/testDebugUnitTest/index.html`. |
| `Could not resolve com.foo:bar:1.2.3` | Dependency not downloadable | Typo in the version, or a temporary repository outage (re-run). |
| `./gradlew: Permission denied` | Executable bit missing | Already handled by `chmod +x gradlew`. |

**Backend**

| Log message | Cause | Fix |
|---|---|---|
| `[ERROR] COMPILATION ERROR` + file:line | Java compile error | Fix the code. |
| `Could not resolve placeholder 'XYZ' in value "${XYZ}"` | New config variable not provided to CI | Add `XYZ` to the workflow `env:` block and `.env.example`. |
| `Failed to determine a suitable driver class` / `Connection refused` | Database not reachable | Check the `services:` block and the `SUPABASE_DB_*` values. |
| `No property 'xyz' found for type 'Favorite'` | A repository method name references a field that doesn't exist | Fix the method name or the entity field. |
| `UnsatisfiedDependencyException … No qualifying bean of type` | A bean can't be wired (missing `@Component`/`@Service`, or two candidates) | Read the "Caused by" chain at the bottom. |
| `Tests run: 1, Failures: 0, Errors: 1` | An exception during the test (usually the context failed to start) | Scroll up to the first `Caused by:` line. That's the root cause. |

---

## 6. Running the exact CI checks locally

Always try this **before pushing**. It's faster than waiting for CI, and you see failures in your own terminal.

### 6.1 Android (from `MedRational-Android/`)

```powershell
.\gradlew.bat lint testDebugUnitTest assembleDebug
# optional, also check the release/R8 build:
.\gradlew.bat assembleRelease
```

Reports:
- `app/build/reports/lint-results-debug.html`
- `app/build/reports/tests/testDebugUnitTest/index.html`

### 6.2 Backend (from `MedRational/`), with Docker Desktop running

```powershell
# 1. Start a throwaway Postgres 16 (55432 avoids clashing with anything on 5432)
docker run -d --rm --name medrational-ci-pg -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:16

# 2. Same env values as the workflow (only for this PowerShell window)
$env:SUPABASE_DB_HOST='localhost'; $env:SUPABASE_DB_PORT='55432'
$env:SUPABASE_DB_DATABASE='postgres'; $env:SUPABASE_DB_USERNAME='postgres'; $env:SUPABASE_DB_PASSWORD='postgres'
$env:JWT_SECRET='ci-only-dummy-secret-that-is-at-least-32-bytes-long'
$env:CLOUDFLARE_ACCOUNT_ID='ci-dummy'; $env:CLOUDFLARE_ACCESS_KEY_ID='ci-dummy'; $env:CLOUDFLARE_SECRET_ACCESS_KEY='ci-dummy'
$env:MAIL_HOST='localhost'; $env:MAIL_PORT='587'; $env:MAIL_USERNAME='ci-dummy'; $env:MAIL_PASSWORD='ci-dummy'

# 3. The exact CI command
.\mvnw.cmd -B verify

# 4. Clean up (the container deletes itself because of --rm)
docker stop medrational-ci-pg
```

This is exactly how the backend workflow was validated before it was pushed. Result: `Tests run: 1, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

To run a single test class: `.\mvnw.cmd test -Dtest=MedRationalApplicationTests` (or `-Dtest=ClassName#methodName`).

---

## 7. Dependabot: automatic update PRs

Both repos have `.github/dependabot.yml`, which takes effect once merged into `main`. Every week Dependabot checks:
- **Android:** Gradle dependencies (`build.gradle.kts`, `libs.versions.toml`) and GitHub Actions versions.
- **Backend:** Maven dependencies (`pom.xml`) and GitHub Actions versions.

For each outdated dependency it opens a PR like `Bump com.squareup.retrofit2:retrofit from 2.11.0 to 2.12.0` (at most 5 open at once per ecosystem). **CI runs on each of those PRs**, and that's the point of having both:

- ✅ **Green:** read the linked release notes for anything alarming, then squash-merge.
- ❌ **Red:** the update breaks something. Either fix it on the Dependabot branch (you can push commits to it) or close the PR. Closing it makes Dependabot skip that version.

Expect a burst of PRs the first week (your lint report already shows 24 outdated dependencies), then a trickle.

---

## 8. Making CI actually block bad merges

Right now CI is **advisory**: a red ❌ shows on the PR, but GitHub still lets you click merge. To make it **mandatory**, add a ruleset on `main` with "Require status checks to pass → `build`". The step-by-step instructions are in [GITHUB_REPO_SETUP.md §7.1](GITHUB_REPO_SETUP.md#71-protect-main-rulesets).

**MedRational-Android is private.** On a free GitHub account, rulesets and branch protection are only available for **public** repositories. Either make the repo public (good for a portfolio, after a secret check; the history was already checked and contains no secrets) or upgrade to GitHub Pro. The backend repo is public, so this works there today.

---

## 9. Warnings from the first runs (to do)

The first Android run page showed these **annotations**. None failed the build, but each deserves attention:

| Annotation | Meaning | Action |
|---|---|---|
| "Node.js 20 is deprecated… actions/checkout@v4, actions/setup-java@v4, actions/upload-artifact@v4, gradle/actions/setup-gradle@v4 are being forced to run on Node.js 24" | These action versions are old | Bump to `@v5` (`checkout`, `setup-java`, `upload-artifact`, `setup-gradle`). Dependabot's `github-actions` updates will propose this automatically after merge. |
| "setup-java v4 is deprecated… migrate to actions/setup-java@v5" | Same as above | Same. |
| "The ubuntu-latest label will migrate to Ubuntu 26 beginning October 19, 2026" | The runner OS image changes soon | Usually nothing to do. If something breaks around that date, pin `runs-on: ubuntu-24.04` temporarily. |

The backend workflow uses the same `checkout@v4` and `setup-java@v4`, so it needs the same bumps.

---

## 10. Writing your first real tests

Once a test is in the repo, it runs on every PR automatically. Below are two concrete examples aimed at real code. They are **examples to add**: they're not in the repos yet and each needs a couple of test dependencies.

### 10.1 Android: a ViewModel unit test (`SearchViewModel`)

**Why this class:** `SearchViewModel` takes its API as a constructor parameter (`api: MedRationalApi = ApiClient.api`), so a test can pass a fake one. It also has real logic worth protecting: the 350 ms debounce, and the Idle / Loading / Success / Error states.

**1. Add test dependencies** to `app/build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("io.mockk:mockk:1.13.13")                                  // fake objects
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")     // control time
}
```

**2. Create `app/src/test/java/com/example/medrational_android/viewmodel/SearchViewModelTest.kt`:**

```kotlin
package com.example.medrational_android.viewmodel

import com.example.medrational_android.data.api.MedRationalApi
import com.example.medrational_android.data.model.FileSearchResult
import com.example.medrational_android.data.model.PageResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    // A virtual clock: delay(350) doesn't actually wait; we move time forward by hand
    private val dispatcher = StandardTestDispatcher()
    private val api = mockk<MedRationalApi>()

    private val ecgFile = FileSearchResult(
        id = 1, reasoningId = 2, reasoningTitle = "Chest pain", categoryId = 3,
        categoryName = "Cardiology", fileName = "ecg.pdf", fileType = "application/pdf",
        publicUrl = "https://example.com/ecg.pdf", fileSizeBytes = 1024
    )

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which doesn't exist on the JVM; replace it
        Dispatchers.setMain(dispatcher)
        // The ViewModel loads categories in init {}; give it an answer
        coEvery { api.getCategories() } returns Response.success(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `search waits for the 350ms debounce, then shows results`() = runTest(dispatcher) {
        coEvery { api.searchFiles("ecg", null) } returns
            Response.success(PageResponse(content = listOf(ecgFile), totalElements = 1, totalPages = 1, number = 0))

        val viewModel = SearchViewModel(api)
        viewModel.onQueryChanged("ecg")

        advanceTimeBy(349); runCurrent()                       // just before the debounce
        coVerify(exactly = 0) { api.searchFiles(any(), any()) }  // nothing sent yet

        advanceUntilIdle()                                       // let the debounce fire
        assertEquals(SearchUiState.Success(listOf(ecgFile)), viewModel.uiState.value)
    }

    @Test
    fun `typing quickly sends only one request`() = runTest(dispatcher) {
        coEvery { api.searchFiles(any(), any()) } returns
            Response.success(PageResponse(content = emptyList(), totalElements = 0, totalPages = 0, number = 0))

        val viewModel = SearchViewModel(api)
        viewModel.onQueryChanged("e")
        advanceTimeBy(100)
        viewModel.onQueryChanged("ec")
        advanceTimeBy(100)
        viewModel.onQueryChanged("ecg")
        advanceUntilIdle()

        coVerify(exactly = 1) { api.searchFiles("ecg", null) }   // earlier keystrokes were cancelled
    }

    @Test
    fun `clearing the query goes back to Idle without calling the API`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(api)
        viewModel.onQueryChanged("   ")
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 0) { api.searchFiles(any(), any()) }
    }

    @Test
    fun `server error shows the Error state`() = runTest(dispatcher) {
        coEvery { api.searchFiles(any(), any()) } throws java.io.IOException("timeout")

        val viewModel = SearchViewModel(api)
        viewModel.onQueryChanged("ecg")
        advanceUntilIdle()

        assertEquals(SearchUiState.Error("timeout"), viewModel.uiState.value)
    }
}
```

**3. Run it:** `.\gradlew.bat testDebugUnitTest`. Once merged, it runs on every PR.

**Key ideas in this test:**
- **Mocking (`mockk`)** replaces the real network API with a fake whose answers you script with `coEvery { … } returns …`. Tests must never depend on a real server.
- **Virtual time (`StandardTestDispatcher`, `advanceTimeBy`, `advanceUntilIdle`)** makes `delay(350)` instant and controllable, so the debounce can be tested exactly and quickly.
- **`Dispatchers.setMain`** is required for any test of a ViewModel that uses `viewModelScope`.
- **One behavior per test, named as a sentence.** When it fails, the name tells you what broke.

**Harder to test today:** `FavoriteViewModel` and `AuthViewModel` take a `TokenManager`, which uses Android's `EncryptedSharedPreferences` and can't run in a JVM unit test. The usual fix is a small refactor: extract an interface (e.g. `SessionStore` with `getUserId()`, `getToken()`…) that `TokenManager` implements, and have the ViewModels depend on the interface. Tests can then pass a simple fake.

### 10.2 Backend: a security test for `CurrentUserGuard`

**Why this test:** the guard added on 2026-10-08 (users can only access their own `/users/{userId}/…` data) is security-critical. If someone accidentally removes a `requireSelf` call, `contextLoads` still passes. Only a behavior test catches it.

**1. Add the Spring Security test support** to `pom.xml`, next to the other test starters:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

**2. Create `src/test/java/com/example/MedRational/Security/FavoriteAccessTest.java`:**

```java
package com.example.MedRational.Security;

import com.example.MedRational.Entities.Role;
import com.example.MedRational.Entities.User;
import com.example.MedRational.Repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest          // full app, real Postgres (the CI service container)
@AutoConfigureMockMvc    // MockMvc sends fake HTTP requests through the real security filters
@Transactional           // every test's DB changes are rolled back afterwards
class FavoriteAccessTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    private User saveUser(String email) {
        return userRepository.save(User.builder()
                .email(email)
                .fullName("Test User")
                .role(Role.ROLE_USER)              // must be set: @Builder ignores the field's default value
                .createdAt(LocalDateTime.now())    // column is NOT NULL
                .build());
    }

    @Test
    @WithMockUser(username = "alice@test.com")   // pretend Alice is logged in (principal name = email)
    void userCanReadTheirOwnFavorites() throws Exception {
        User alice = saveUser("alice@test.com");

        mockMvc.perform(get("/api/v1/users/{id}/favorites", alice.getId()))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "alice@test.com")
    void userCannotReadSomeoneElsesFavorites() throws Exception {
        saveUser("alice@test.com");
        User bob = saveUser("bob@test.com");

        mockMvc.perform(get("/api/v1/users/{id}/favorites", bob.getId()))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice@test.com")
    void userCannotToggleSomeoneElsesFavorite() throws Exception {
        saveUser("alice@test.com");
        User bob = saveUser("bob@test.com");

        mockMvc.perform(post("/api/v1/users/{id}/favorites/{fileId}/toggle", bob.getId(), 1L))
               .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/users/{id}/favorites", 1L))
               .andExpect(status().isForbidden());   // or isUnauthorized(), depending on SecurityConfig
    }
}
```

**3. Run it** with the local CI setup from §6.2 (Postgres container + env vars):

```powershell
.\mvnw.cmd test -Dtest=FavoriteAccessTest
```

**Notes:**
- If your IDE flags the `AutoConfigureMockMvc` import, let it suggest the right one. Spring Boot 4 moved several test annotations into new packages.
- `@WithMockUser` works with your `JwtAuthenticationFilter`: with no `Authorization` header the filter does nothing, so the mock user stays logged in. `CurrentUserGuard` then looks up `alice@test.com` in the (test) database, which is why the test saves Alice first.
- The last test asserts `isForbidden()`. If it fails with 401, your security setup returns "unauthorized" for anonymous users. Adjust the assertion to whatever the intended behavior is: deciding the expected behavior is part of writing tests.
- The same pattern works for the download-tracking endpoints (`/users/{userId}/recent-downloads`) and for admin-only rules (`@WithMockUser(username = "...", authorities = "ROLE_ADMIN")` on `/api/v1/admin/analytics/overview`).

### 10.3 What to test first (priority order)

1. **Security rules** (backend): own-data-only, admin-only endpoints. These are the costliest to get wrong.
2. **Auth flows** (backend): OTP generated, expired OTP rejected, wrong code rejected, JWT returned on success.
3. **ViewModel state logic** (Android): search, favorites toggling (after the `SessionStore` refactor), login error handling.
4. **JSON contract** (both): a backend test asserting the JSON shape of `/users/{id}/favorites` (e.g. `jsonPath("$.content[0].file.id")`). That would have caught the `FavoriteResponse` mismatch bug.
5. **Service logic** (backend): plain unit tests with Mockito for services, with no Spring and no database, which makes them very fast.

---

## 11. Where to go next

Ordered from most to least valuable for this project:

| Improvement | How | Benefit |
|---|---|---|
| Require CI to pass before merge | Ruleset on `main` (§8) | CI stops being optional |
| Bump action versions to v5 | Merge the Dependabot PRs, or edit the YAML | Removes deprecation warnings |
| Lint baseline | `./gradlew updateLintBaseline` + `baseline = file(...)` | Fail only on **new** lint issues |
| Upload test reports | Another `actions/upload-artifact` step for `app/build/reports/tests/` and `target/surefire-reports/` | Read failures without re-running locally |
| Code coverage | **JaCoCo** (Maven plugin `jacoco-maven-plugin`; Android `jacoco` or **Kover**) with a minimum threshold in `verify` | Shows which code no test touches |
| Build the release APK in CI | Add `assembleRelease` | Catches R8 / keep-rule problems |
| Instrumented UI tests in CI | [`reactivecircus/android-emulator-runner`](https://github.com/ReactiveCircus/android-emulator-runner) + `connectedAndroidTest` | Real Compose UI tests on an emulator (slow; maybe nightly only) |
| Release workflow | `on: push: tags: ['v*']` → build → `gh release create` with the APK/JAR attached | One-command releases |
| Static analysis | **detekt** / **ktlint** (Kotlin), **Spotless** / **Checkstyle** (Java) | Consistent style, more bugs caught early |
| Remove the hardcoded JWT fallback | `JwtUtil`: `@Value("${jwt.secret}")` with no default | The app fails fast instead of running with a public key |
