# Setting Up a GitHub Repository the Professional Way

A reusable, step-by-step checklist for starting any new project on GitHub with a production-style workflow. It's written from setting up **MedRational** (Android + Spring Boot), and it includes the mistakes made along the way so you don't repeat them.

---

## Table of contents

0. [The big picture](#0-the-big-picture)
1. [One-time machine setup](#1-one-time-machine-setup)
2. [Create the project and the repository](#2-create-the-project-and-the-repository)
3. [Files every repository should have](#3-files-every-repository-should-have)
4. [Secrets: keep them out of git](#4-secrets-keep-them-out-of-git)
5. [The `.github/` folder](#5-the-github-folder)
6. [Continuous Integration (CI) with GitHub Actions](#6-continuous-integration-ci-with-github-actions)
7. [Repository settings on GitHub](#7-repository-settings-on-github)
8. [The daily workflow: branch → PR → merge](#8-the-daily-workflow-branch--pr--merge)
9. [Commit messages: Conventional Commits](#9-commit-messages-conventional-commits)
10. [Versioning and releases](#10-versioning-and-releases)
11. [Troubleshooting: problems we actually hit](#11-troubleshooting-problems-we-actually-hit)
12. [Copy-paste checklist for a new project](#12-copy-paste-checklist-for-a-new-project)
13. [Glossary](#13-glossary)

---

## 0. The big picture

The workflow is **GitHub Flow**, the standard for solo developers and small teams:

```
main ─────●─────────────●─────────────●──────────►   always working, always releasable
           \           / \           /
            ●───●───●─┘   ●───●───●─┘                short-lived branches, one per change
          feat/login     fix/crash-on-logout
                 ▲                ▲
            Pull Request     Pull Request            CI runs here; merge only when green
```

The rules:

1. **`main` is sacred.** Nobody pushes to it directly, not even you. It always builds and always works.
2. **Every change gets its own branch**, created from an up-to-date `main`.
3. **Every branch goes back through a Pull Request (PR).** The PR is where CI runs and where the change is documented.
4. **Merge only when CI is green.** GitHub enforces this once you set up branch protection (§7).
5. **Squash and merge.** Each PR becomes exactly one clean commit on `main`.
6. **Delete the branch after merging.** Branches are temporary.

**Why not GitFlow** (`develop`, `release/*`, `hotfix/*`)? It was designed for software shipped in big scheduled versions. For one person or a small team it's mostly overhead. GitHub Flow is what most modern companies use.

**Why not a long-lived `features` branch** (what MedRational did at first)?
- Everything piles up in one place.
- There's no review point and no CI gate.
- You can't release one feature without all the others.
- `main` and `features` drift apart until merging becomes painful.

---

## 1. One-time machine setup

Do this once per computer, not per project.

### 1.1 Install the tools

| Tool | Why | Install (Windows) |
|---|---|---|
| Git | Version control | https://git-scm.com (includes Git Bash) |
| GitHub CLI (`gh`) | Create repos and PRs, watch CI, and change settings from the terminal | `winget install --id GitHub.cli -e` |
| Docker Desktop | Run databases locally and reproduce CI | https://docker.com |

After installing anything, **open a new terminal**. Terminals that were already open won't find the new command.

### 1.2 Tell git who you are

```bash
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"     # same email as your GitHub account
git config --global init.defaultBranch main          # new repos start on "main", not "master"
git config --global pull.ff only                     # see below
git config --global core.autocrlf true               # Windows only: handle CRLF/LF line endings
```

**Why `pull.ff only`?** By default, `git pull` silently creates a merge commit when your branch and GitHub's have diverged. That's how MedRational ended up with `Merge branch 'main' of github.com:...` in its history. With `ff only`, git **refuses** and tells you, and you decide what to do (usually `git pull --rebase`). Surprises in git history are bad; this removes one.

### 1.3 Log in to GitHub

```bash
gh auth login --hostname github.com --git-protocol https --web
gh auth setup-git        # lets plain `git push` use gh's login
```

Then add the extra permissions you'll need sooner or later:

```bash
gh auth refresh -h github.com -s workflow,admin:public_key
```

- **`workflow`**: without it, GitHub **rejects** any push that adds or changes files in `.github/workflows/`. The error reads "refusing to allow an OAuth App to create or update workflow ... without `workflow` scope".
- **`admin:public_key`**: lets `gh` manage your SSH keys.

You approve each of these in the browser by entering the one-time code `gh` prints.

**HTTPS or SSH?**
- **HTTPS + `gh`** is the simplest on Windows: no keys to manage.
- **SSH** works too, but if your key has a passphrase, every non-interactive tool (IDE background tasks, scripts, AI agents) fails with `Permission denied (publickey)`, because nothing can type the passphrase. If you use SSH, run an **ssh-agent** so you type the passphrase once per session.

Check your setup at any time:

```bash
gh auth status                       # logged in? which permissions?
git remote -v                        # https://... or git@github.com:... ?
ssh -T git@github.com                # SSH only: should say "Hi <username>!"
```

---

## 2. Create the project and the repository

### Option A: start locally, then create the GitHub repo (recommended)

```bash
mkdir my-project && cd my-project
git init                                       # creates the .git folder; branch is "main"
# ... create the project (Android Studio, Spring Initializr, npm init, etc.) ...
# ... add .gitignore, README.md, LICENSE (see §3) BEFORE the first commit ...
git add .
git status                                     # READ THIS LIST. Anything secret or generated? Stop.
git commit -m "chore: initial project setup"
gh repo create my-project --private --source=. --remote=origin --push
```

`gh repo create` with `--source=.` creates the GitHub repo, links it as `origin`, and pushes, all in one step. Use `--public` instead of `--private` for portfolio projects.

### Option B: create on GitHub first, then clone

```bash
gh repo create my-project --private --clone --gitignore Java --license mit
cd my-project
```

### Public or private?

| | Public | Private (free account) |
|---|---|---|
| Visible to recruiters | ✅ | ❌ (unless you add them) |
| Branch protection / rulesets | ✅ | ❌ **requires GitHub Pro** |
| Secret scanning & push protection | ✅ | ❌ |
| GitHub Actions minutes | Unlimited | 2,000 min/month |

If it's a portfolio project, **make it public**, but only after checking there are no secrets anywhere in its history (§4).

---

## 3. Files every repository should have

```
my-project/
├── .github/                 ← GitHub-specific config (§5)
├── .gitignore               ← what git must never track
├── .gitattributes           ← line-ending rules (optional but recommended)
├── .env.example             ← list of required env vars, no values (backend projects)
├── README.md                ← the front page
├── CHANGELOG.md             ← human-readable history per version
├── LICENSE                  ← legal terms (MIT is the usual choice for portfolios)
└── CLAUDE.md                ← optional: guidance for AI coding assistants
```

### 3.1 `.gitignore`

It tells git which files never to track: build outputs, IDE settings, secrets, local config.

- Start from GitHub's templates: https://github.com/github/gitignore (`Android.gitignore`, `Maven.gitignore`, `Node.gitignore`…), or from https://www.toptal.com/developers/gitignore.
- Always add `.env`, `local.properties`, `*.jks` / `*.keystore`, and your IDE folder (`.idea/`, `.vscode/`).

**Important:** `.gitignore` only affects files that **aren't tracked yet**. If a file is already committed, adding it to `.gitignore` does nothing. You also have to untrack it:

```bash
echo "supabase/" >> .gitignore
git rm -r --cached supabase      # --cached = remove from git, KEEP the file on disk
git commit -m "chore: stop tracking Supabase CLI state"
```

Without `--cached`, `git rm` **deletes the file from your disk** too.

### 3.2 `.gitattributes` (Windows developers: do this)

```gitattributes
* text=auto
*.sh   text eol=lf
gradlew text eol=lf
mvnw    text eol=lf
*.bat  text eol=crlf
*.cmd  text eol=crlf
```

Shell scripts with Windows line endings (CRLF) fail on Linux CI machines with confusing errors like `/bin/sh^M: bad interpreter`. This file prevents that. It's also why CI workflows run `chmod +x gradlew`: Windows doesn't keep the "executable" flag that Linux needs.

### 3.3 `README.md`

This is the first thing people see, recruiters included. Recommended structure:

1. **Title + badges** (CI status, license).
2. **One-paragraph pitch:** what it is and who it's for.
3. **Screenshots or a GIF.** This matters more than anything else for a portfolio. Store them in `docs/screenshots/`.
4. **Features:** a short bullet list.
5. **Tech stack:** a table.
6. **Architecture:** a small diagram (ASCII is fine) of how the parts connect.
7. **Getting started:** requirements, setup, how to run. Someone new should be able to follow it without asking you anything.
8. **Running tests.**
9. **Contributing / workflow:** a short description of the branch → PR process.
10. **License.**

A CI badge looks like this (replace OWNER, REPO and the workflow file name):

```markdown
[![CI](https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/REPO/actions/workflows/ci.yml)
```

### 3.4 `CHANGELOG.md`

This follows [Keep a Changelog](https://keepachangelog.com). New changes go under `[Unreleased]`, and when you release, that section gets a version number and a date.

```markdown
# Changelog

## [Unreleased]
### Added
- Dark mode.

## [0.2.0] - 2026-11-01
### Added
- Favorites screen.
### Fixed
- Crash when logging out offline.
```

Section names: `Added`, `Changed`, `Deprecated`, `Removed`, `Fixed`, `Security`.

### 3.5 `LICENSE`

Without a license, nobody is legally allowed to use your code, even if it's public. MIT is short and permissive. Use `gh repo create --license mit`, or GitHub's "Add file → Create new file → name it LICENSE → Choose a license template".

### 3.6 `.env.example` (backend projects)

It lists every environment variable the app needs, with **empty or placeholder values**. It's committed; the real `.env` is not.

```dotenv
# Copy to .env and fill in. Never commit .env.
DB_HOST=
DB_PASSWORD=
JWT_SECRET=          # at least 32 bytes
```

A new developer (or future you) runs `cp .env.example .env` and fills it in.

---

## 4. Secrets: keep them out of git

**Secrets** are passwords, API keys, JWT signing keys, database URLs that contain passwords, keystores, and `google-services.json` in some setups.

### Rules

1. Secrets go in **environment variables** or `.env` (ignored), never in source code or committed config.
2. Committed config files reference variables instead: `password: ${MAIL_PASSWORD}` in Spring, `BuildConfig` fields filled from `local.properties` in Android.
3. **Never** give a secret a hardcoded fallback value, like `@Value("${jwt.secret:mySuperSecretKey...}")`. If the variable is missing, the app should fail at startup instead of running with a key that's published on GitHub.
4. Before every commit, read `git status` and `git diff --staged`.

### Check a repo before making it public

```bash
git log --all -p | grep -iE "password|secret|api[_-]?key|token" | grep -v '\${'
```

Any line that isn't a `${PLACEHOLDER}` deserves a closer look.

### If you committed a secret

1. **Rotate it immediately** (generate a new password or key at the provider). This is the step that actually matters. Once something is pushed, assume someone has it: bots scan GitHub for keys within minutes.
2. Then remove it from the code and commit.
3. Removing it from **history** (with `git filter-repo` or BFG) is optional cleanup. It does **not** undo the leak.

### GitHub features that help (public repos are free)

**Settings → Code security**:
- **Secret scanning:** alerts you when a known key format appears in the repo.
- **Push protection:** blocks a push that contains a secret, before it reaches GitHub.
- **Dependabot alerts:** warns you about vulnerable dependencies.

---

## 5. The `.github/` folder

```
.github/
├── workflows/
│   └── ci.yml                      ← CI pipeline (§6)
├── dependabot.yml                  ← automatic dependency update PRs
├── pull_request_template.md        ← pre-fills every PR description
├── ISSUE_TEMPLATE/                 ← optional: forms for bug reports / feature requests
│   ├── bug_report.md
│   └── feature_request.md
└── CODEOWNERS                      ← optional: auto-request reviewers per folder (teams)
```

### 5.1 `pull_request_template.md`

```markdown
## What
<!-- What does this PR change? -->

## Why
<!-- Why is it needed? Link the issue: "Closes #12" auto-closes it on merge. -->

## How tested
<!-- Steps you followed to verify it. -->

## Screenshots
<!-- Before / after, for UI changes. Delete otherwise. -->
```

It forces you to explain each change, which is exactly what a reviewer, or future you, needs.

### 5.2 `dependabot.yml`

Dependabot checks for new versions of your libraries every week and opens a PR for each update. CI runs on those PRs, so you can see straight away whether an update breaks anything.

```yaml
version: 2
updates:
  - package-ecosystem: gradle          # or: maven, npm, pip, docker, ...
    directory: /
    schedule:
      interval: weekly
    open-pull-requests-limit: 5        # don't get flooded
  - package-ecosystem: github-actions  # keeps actions/checkout@vX etc. current
    directory: /
    schedule:
      interval: weekly
```

To handle a Dependabot PR: if CI is green, read the release notes briefly and squash-merge. If CI is red, the update breaks something; either fix it on that branch or close the PR.

### 5.3 Issue templates (optional)

Go to **Settings → General → Features → Issues → Set up templates**, or create the files by hand. These pay off once other people start opening issues.

---

## 6. Continuous Integration (CI) with GitHub Actions

**CI** means a fresh machine automatically builds and tests your code on every PR. "It works on my machine" stops being good enough: it has to work on a clean one.

### 6.1 Anatomy of a workflow file

Workflow files live in `.github/workflows/*.yml`, and GitHub picks them up automatically.

```yaml
name: CI                              # shown in the Actions tab and on PRs

on:                                   # WHEN it runs
  pull_request:                       #   every PR (any target branch)
  push:
    branches: [main]                  #   every push/merge to main (keeps the badge fresh)

jobs:                                 # one or more jobs; each gets a FRESH virtual machine
  build:                              # job id: this name is what branch protection refers to
    runs-on: ubuntu-latest            # the machine type (Linux is cheapest and fastest)

    steps:                            # run in order; the first failure stops the job ❌
      - uses: actions/checkout@v7     # "uses" = a published, reusable action
                                      #   checkout = download your repo onto the machine

      - uses: actions/setup-java@v6   # install a tool
        with:                         # "with" = inputs for that action
          distribution: temurin
          java-version: 21

      - name: Build and test          # optional human-readable label
        run: |                        # "run" = your own shell commands
          chmod +x ./mvnw
          ./mvnw -B verify
```

Key ideas:
- **Every run starts from a blank machine.** Nothing from your laptop exists there: no `.env`, no `local.properties`, no database.
- **`uses:` vs `run:`.** `uses:` runs someone else's packaged step; `run:` runs your own shell commands.
- **Pin major versions** (`@v7`, not `@main` or an exact `@v7.0.1`). Dependabot (with `github-actions` enabled) opens a PR when a new major version comes out.
- **A step fails if its command exits with a non-zero code.** Every build tool does that when a test or the compiler fails.

### 6.2 Ready-to-use templates

**Android (Gradle):**
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
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 17 }
      - uses: gradle/actions/setup-gradle@v6        # caches Gradle between runs
      - run: chmod +x gradlew && ./gradlew lint testDebugUnitTest assembleDebug
      - uses: actions/upload-artifact@v6            # keep the lint report, downloadable
        if: always()                                # even if a step above failed
        with:
          name: lint-report
          path: app/build/reports/lint-results-debug.html
```

**Spring Boot (Maven) with a real database:**
```yaml
name: Backend CI
on:
  pull_request:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    services:                                  # extra containers running next to the job
      postgres:
        image: postgres:16
        env: { POSTGRES_PASSWORD: postgres }
        ports: ["5432:5432"]
        options: >-                            # wait until Postgres accepts connections
          --health-cmd pg_isready --health-interval 5s
          --health-timeout 5s --health-retries 10
    env:                                       # dummy values: CI must never see real secrets
      DB_HOST: localhost
      DB_PASSWORD: postgres
      JWT_SECRET: ci-only-dummy-secret-that-is-at-least-32-bytes-long
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with: { distribution: temurin, java-version: 21, cache: maven }
      - run: chmod +x mvnw && ./mvnw -B verify
```

**Node.js:**
```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v5
        with: { node-version: 22, cache: npm }
      - run: npm ci
      - run: npm run lint --if-present
      - run: npm test
      - run: npm run build --if-present
```

**Python:**
```yaml
name: CI
on:
  pull_request:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-python@v6
        with: { python-version: "3.13", cache: pip }
      - run: pip install -r requirements.txt
      - run: pytest
```

The `checkout`, `setup-java`, `setup-gradle`, and `upload-artifact` versions above match what Dependabot proposed in October 2026. For `setup-node` and `setup-python`, check each action's GitHub releases page when you start. Either way, enable Dependabot's `github-actions` updates and let it keep them current; don't trust version numbers in a guide (including this one) over Dependabot.

### 6.3 Real secrets in CI (when you need them)

If CI ever needs a real key (deploying, for example), never put it in the YAML. Store it under **Settings → Secrets and variables → Actions → New repository secret**, then reference it:

```yaml
env:
  API_KEY: ${{ secrets.API_KEY }}
```

GitHub hides it in logs, and it isn't available to PRs opened from forks.

### 6.4 Always test CI locally first

Reproduce the CI environment on your machine before pushing, so the first PR isn't a string of ❌.

```bash
# 1. Start the same database CI uses (port 55432 avoids clashing with a local Postgres)
docker run -d --rm --name ci-pg -e POSTGRES_PASSWORD=postgres -p 55432:5432 postgres:16

# 2. Set the same env vars as the workflow (PowerShell syntax shown)
$env:DB_HOST='localhost'; $env:DB_PORT='55432'; $env:DB_PASSWORD='postgres'; ...

# 3. Run the exact command from the workflow
./mvnw -B verify

# 4. Clean up (--rm above makes stop also delete the container)
docker stop ci-pg
```

For full fidelity there's also [`act`](https://github.com/nektos/act), which runs GitHub Actions workflows locally in Docker.

### 6.5 Where to watch CI

- **On the PR:** the checks box above the merge button (🟡 running, ✅ passed, ❌ failed). Click **Details** for logs.
- **Actions tab:** `https://github.com/OWNER/REPO/actions` has the full history. Click a run, then the job, then a step to read its output.
- **Next to each commit:** a small ✅ / ❌ / 🟡 icon.
- **Email:** GitHub emails you on failures (Settings → Notifications → Actions).
- **Terminal:**
  ```bash
  gh pr checks <PR#>            # current status
  gh pr checks <PR#> --watch    # live until finished
  gh run list                   # recent runs
  gh run view <run-id>          # summary, annotations, artifacts
  gh run view --log-failed      # only the log lines of failed steps
  gh run rerun <run-id>         # retry (e.g. after a flaky network error)
  ```

**Reading a failure:** open the ❌ step and scroll to the **bottom** of its log. The real error is almost always in the last 20–30 lines, above "Process completed with exit code 1".

---

## 7. Repository settings on GitHub

### 7.1 Protect `main`: rulesets

**Settings → Rules → Rulesets → New ruleset → New branch ruleset**

- **Ruleset name:** `main protection`
- **Enforcement status:** Active
- **Target branches:** Add target → *Include default branch*
- **Rules:**
  - ✅ **Restrict deletions:** nobody can delete `main`.
  - ✅ **Require linear history:** no merge commits on `main`, which pairs with squash merging.
  - ✅ **Require a pull request before merging**, with **Required approvals = 0** when you work alone. GitHub never lets you approve your own PR, so 1 would lock you out.
  - ✅ **Require status checks to pass**, then add the job name (e.g. `build`). The check only shows up in the search box **after it has run at least once**, so open your first PR, let CI run, then come back and add it.
  - ✅ **Block force pushes.**

Leave **Bypass list** empty if you want the rules to apply to you too, which is the point.

**Private repos on a free account can't use rulesets or branch protection.** Make the repo public or upgrade to GitHub Pro.

You can do the same from the terminal:

```bash
cat > ruleset.json <<'EOF'
{
  "name": "main protection",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    { "type": "required_linear_history" },
    { "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 0,
        "dismiss_stale_reviews_on_push": false,
        "require_code_owner_review": false,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false } },
    { "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [ { "context": "build" } ] } }
  ]
}
EOF
gh api repos/OWNER/REPO/rulesets -X POST --input ruleset.json
```

### 7.2 Merge settings

**Settings → General → Pull Requests**
- ❌ Allow merge commits
- ✅ **Allow squash merging**, with the default message set to **"Pull request title and description"**
- ❌ Allow rebase merging
- ✅ **Automatically delete head branches**
- ✅ **Always suggest updating pull request branches** (optional)

From the terminal:

```bash
gh repo edit OWNER/REPO --enable-squash-merge --enable-merge-commit=false \
  --enable-rebase-merge=false --delete-branch-on-merge
gh api repos/OWNER/REPO -X PATCH -f squash_merge_commit_title=PR_TITLE \
  -f squash_merge_commit_message=PR_BODY
```

### 7.3 "About" section (repo home page, gear icon ⚙)

- **Description:** one line.
- **Website:** a demo or the related repo.
- **Topics:** `android`, `kotlin`, `jetpack-compose`, `spring-boot`, `postgresql`… These make the repo discoverable.

```bash
gh repo edit OWNER/REPO --description "..." --add-topic android,kotlin,jetpack-compose
```

### 7.4 Code security (Settings → Code security)

Turn on **Dependabot alerts**, **Dependabot security updates**, **Secret scanning**, and **Push protection** (the last two need a public repo or a paid plan).

---

## 8. The daily workflow: branch → PR → merge

### 8.1 Start a change

```bash
git checkout main
git pull                                  # ALWAYS start from the latest main
git checkout -b feat/favorites-screen     # one branch per change
```

**Branch names:** `type/short-description`, lowercase, with dashes.

| Prefix | Use for |
|---|---|
| `feat/` | new functionality |
| `fix/` | bug fixes |
| `refactor/` | restructuring code without changing behavior |
| `docs/` | documentation only |
| `test/` | adding or fixing tests |
| `chore/` | maintenance (configs, cleanup, dependencies) |
| `ci/` | CI workflow changes |

If you use GitHub Issues, include the issue number: `feat/12-favorites-screen`.

### 8.2 Work and commit

```bash
git status                                # what changed?
git diff                                  # see the actual changes
git add path/to/file1 path/to/file2       # stage specific files (avoid "git add ." blindly)
git diff --staged                         # review exactly what will be committed
git commit -m "feat(favorites): add favorites screen"
```

- **Commit small and often.** Each commit should be one logical step.
- `git add -p` lets you stage parts of a file, chunk by chunk.

### 8.3 Push and open the PR

```bash
git push -u origin feat/favorites-screen  # -u links your branch to GitHub's (first push only)
gh pr create --fill                       # or click "Compare & pull request" on GitHub
```

Fill in the template (What / Why / How tested / Screenshots), then watch CI: `gh pr checks --watch`.

### 8.4 If CI fails

Fix the problem **on the same branch**, commit, and push again. The PR updates and CI re-runs automatically. Never open a new PR for the fix.

### 8.5 If `main` moved while you were working

```bash
git fetch origin
git rebase origin/main          # replay your commits on top of the latest main
# if there are conflicts: fix the files, then
git add <fixed files> && git rebase --continue
git push --force-with-lease     # required after a rebase; safe on YOUR OWN branch only
```

Alternatively, click **"Update branch"** on the PR page.

`--force-with-lease` refuses to overwrite the branch if someone else pushed to it in the meantime, which makes it the safe form of force-push. **Never force-push `main`**; the ruleset blocks it anyway.

### 8.6 Merge and clean up

1. On the PR page, look at **Files changed** one last time.
2. Click **Squash and merge**. The PR title becomes the commit message on `main`, so make it a good Conventional Commit.
3. Locally:
   ```bash
   git checkout main
   git pull
   git branch -D feat/favorites-screen   # -D (capital) because squash creates a NEW commit,
                                         # so git can't tell this branch was merged
   git fetch --prune                     # forget remote branches that were deleted on GitHub
   ```

### 8.7 Test a branch locally before merging

```bash
git fetch origin
git checkout chore/repo-setup        # or: gh pr checkout <PR#>
```

**After switching branches in Android Studio or IntelliJ, sync Gradle or Maven** (the elephant icon, or *File → Sync Project with Gradle Files*). If the build files changed between branches, the IDE otherwise shows false errors like `Unresolved reference: BuildConfig` even though the command-line build works.

---

## 9. Commit messages: Conventional Commits

Spec: https://www.conventionalcommits.org

```
type(optional-scope): short imperative summary        ← max ~72 characters

Optional body: WHY the change was made, what it affects.
Wrap at ~72 characters.

Optional footer: Closes #12 / BREAKING CHANGE: ...
```

| Type | Meaning | Example |
|---|---|---|
| `feat` | new feature | `feat(search): add category filter chips` |
| `fix` | bug fix | `fix(favorites): match backend response shape` |
| `refactor` | code change, same behavior | `refactor(api): extract auth interceptor` |
| `perf` | performance | `perf(search): debounce queries by 350ms` |
| `test` | tests only | `test(auth): cover expired OTP` |
| `docs` | documentation | `docs: add setup steps to README` |
| `build` | build system or dependencies | `build: enable R8 for release builds` |
| `ci` | CI configuration | `ci: add Postgres service to backend workflow` |
| `chore` | maintenance | `chore: stop tracking IDE files` |

Rules of thumb:
- **Imperative mood:** "add", not "added" or "adds". Read it as "*This commit will…* add search".
- **Say what and why, not how.** The diff already shows how.
- **With squash merging, the PR title is what ends up on `main`**, so that's the message that matters most.

---

## 10. Versioning and releases

### 10.1 Semantic Versioning (SemVer): `MAJOR.MINOR.PATCH`

| Bump | When | Example |
|---|---|---|
| MAJOR | Breaking change (old clients or APIs stop working) | 1.4.2 → **2.0.0** |
| MINOR | New feature, backwards compatible | 1.4.2 → 1.**5**.0 |
| PATCH | Bug fix only | 1.4.2 → 1.4.**3** |

Stay on **`0.x.y`** while the project is in development. `1.0.0` means "stable, deployed for real".

### 10.2 Release steps

1. On a branch (`chore/release-0.2.0`):
   - Bump the version in the build file (Android: `versionName "0.2.0"` and `versionCode` + 1; Maven: `<version>`; npm: `npm version minor`).
   - In `CHANGELOG.md`, rename `[Unreleased]` to `[0.2.0] - YYYY-MM-DD` and add a fresh empty `[Unreleased]` above it.
2. Open a PR, wait for CI, and squash-merge.
3. Tag `main` and publish:
   ```bash
   git checkout main && git pull
   git tag -a v0.2.0 -m "v0.2.0"
   git push origin v0.2.0
   gh release create v0.2.0 --title "v0.2.0" --notes-file release-notes.md
   # Android: attach the APK
   gh release upload v0.2.0 app/build/outputs/apk/release/app-release.apk
   ```

**Tags never change.** Never move or reuse a tag; release a new version instead.

---

## 11. Troubleshooting: problems we actually hit

| Symptom | Cause | Fix |
|---|---|---|
| `! [rejected] main -> main (fetch first)` / "Updates were rejected… pull first" | GitHub's branch has commits you don't have (e.g. a PR was merged on the website) | `git pull --rebase`, then push. With `pull.ff only` set, git tells you instead of silently merging. |
| `Permission denied (publickey)` | The SSH key isn't on GitHub, **or** it has a passphrase and the tool can't prompt for it | `ssh -vT git@github.com`: if it says "Server accepts key", it's the passphrase; use ssh-agent, or switch to HTTPS with `gh auth setup-git`. |
| `refusing to allow an OAuth App to create or update workflow … without workflow scope` | The `gh` token lacks the `workflow` permission | `gh auth refresh -h github.com -s workflow` |
| `The following paths are ignored by one of your .gitignore files` | You ran `git add` on a path you just ignored | To untrack it, use `git rm -r --cached <path>` instead. |
| `git fetch` silently failed, so later decisions used stale data | Authentication was broken and the error was overlooked | Always check fetch output. `git status -sb` shows `[ahead N, behind M]` relative to the last **successful** fetch. |
| IDE: `Unresolved reference: BuildConfig` (or any generated class) | The IDE didn't re-sync after switching to a branch with different build files | Sync Gradle; if needed, Build → Rebuild; last resort, Invalidate Caches. |
| A merge commit `Merge branch 'main' of github.com:…` appeared | `git pull` with diverged branches and the default pull settings | `git config --global pull.ff only`. Next time, use `git pull --rebase`. |
| `gh: command not found` right after installing | An old terminal session with the old PATH | Open a new terminal. |
| CI: `./gradlew: Permission denied` | The executable bit was lost (Windows) | `chmod +x gradlew` in the workflow, or `git update-index --chmod=+x gradlew` once. |
| CI: `/bin/sh^M: bad interpreter` | CRLF line endings in a shell script | `.gitattributes` with `gradlew text eol=lf` (§3.2). |
| Release build crashes or returns empty data, debug works | R8 renamed classes that Gson or reflection needs | Add `-keep class your.package.model.** { *; }` to `proguard-rules.pro`. |
| R8: `Missing class com.google.errorprone.annotations…` | A library references compile-time-only annotations | Use the rules R8 generates in `app/build/outputs/mapping/release/missing_rules.txt` (here: `-dontwarn com.google.errorprone.annotations.**`). |

### Useful "where am I?" commands

```bash
git status -sb                          # branch, ahead/behind, changed files
git log --oneline --graph --all -20     # picture of all branches
git branch -vv                          # local branches + what they track + ahead/behind
git remote -v                           # where push/pull go
git reflog -10                          # everything HEAD did recently (your undo history)
```

**`git reflog` is your safety net.** Almost nothing in git is truly lost: the reflog lists every commit you've been on, and `git checkout <hash>` takes you back to any of them.

---

## 12. Copy-paste checklist for a new project

```
LOCAL
[ ] git init (branch = main)
[ ] .gitignore from github/gitignore template (+ .env, local.properties, IDE folder)
[ ] .gitattributes (eol=lf for gradlew/mvnw/*.sh)
[ ] README.md (pitch, screenshots, stack, architecture, setup, tests, license)
[ ] LICENSE (MIT)
[ ] CHANGELOG.md with an [Unreleased] section
[ ] .env.example (backend), no real values anywhere in the code
[ ] git status → nothing secret or generated → first commit "chore: initial project setup"

GITHUB
[ ] gh repo create NAME --public --source=. --remote=origin --push
[ ] .github/workflows/ci.yml, tested locally first
[ ] .github/dependabot.yml (your ecosystem + github-actions)
[ ] .github/pull_request_template.md
[ ] Open the first PR with these files and confirm CI goes green

SETTINGS
[ ] Ruleset on main: no deletion, no force push, PR required (0 approvals),
    linear history, status check "build" required (add AFTER the first CI run)
[ ] Squash merge only, PR title + description as message, auto-delete branches
[ ] About: description + topics
[ ] Code security: Dependabot alerts/updates, secret scanning, push protection
[ ] CI badge in README (after the first run on main)

EVERY CHANGE
[ ] git checkout main && git pull
[ ] git checkout -b type/description
[ ] small Conventional Commits
[ ] git push -u origin <branch> → gh pr create
[ ] CI green → Squash and merge → delete the local branch

EVERY RELEASE
[ ] bump version + CHANGELOG on a branch → PR → merge
[ ] git tag -a vX.Y.Z → git push origin vX.Y.Z → gh release create
```

---

## 13. Glossary

| Term | Meaning |
|---|---|
| **Repository (repo)** | The project folder plus its full history (the hidden `.git` folder). |
| **Commit** | A saved snapshot of the files, with a message, author, and parent commit(s). |
| **Branch** | A movable label pointing at a commit. Committing moves it forward. |
| **HEAD** | "Where you are now": usually the branch you have checked out. |
| **Remote / `origin`** | The copy of the repo on GitHub. `origin` is the default name. |
| **`origin/main`** | Your local snapshot of GitHub's `main` as of the last `git fetch`. It can be stale. |
| **Fetch** | Download new commits and branch positions from the remote. **Doesn't change your files.** |
| **Pull** | Fetch, then integrate into your current branch (merge or rebase). |
| **Push** | Upload your commits to the remote. Rejected if the remote has commits you don't have. |
| **Staging area (index)** | The "next commit" draft. `git add` puts changes there; `git commit` saves it. |
| **Fast-forward** | Moving a branch label forward along a straight line of commits. No merge commit is needed. |
| **Merge commit** | A commit with two parents that joins two lines of history. |
| **Rebase** | Re-apply your commits on top of another branch, as if you'd started from there. |
| **Squash merge** | Combine all of a PR's commits into **one** new commit on `main`. |
| **Pull Request (PR)** | A request to merge a branch, with discussion, review, and CI checks. |
| **CI (Continuous Integration)** | Automatically building and testing every change on a clean machine. |
| **Workflow / job / step** | A GitHub Actions YAML file / one machine within it / one command or action on that machine. |
| **Status check** | The ✅/❌ a CI job reports on a commit or PR. |
| **Ruleset / branch protection** | GitHub rules that restrict what can happen to a branch. |
| **Tag** | A permanent label on a commit, used for versions (`v1.2.0`). |
| **Release** | A GitHub page built on a tag, with notes and downloadable files. |
| **Artifact** | A file produced by a CI run (report, APK), downloadable from the run page. |
| **Dependabot** | GitHub's bot that opens PRs to update your dependencies. |
