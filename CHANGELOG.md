# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- `docs/CI_AND_TESTS.md` and `docs/GITHUB_REPO_SETUP.md` guides, linked from the README.
- GitHub Actions CI running the test suite against a PostgreSQL service container, Dependabot, and a PR template.
- `.env.example` documenting every required environment variable.

## [0.1.0] - 2026-10-08

### Added
- JWT authentication with emailed one-time codes for login, registration, and password reset; admin account creation.
- Categories, clinical reasonings, and study files with Cloudflare R2 storage.
- File, reasoning, category, and full ZIP downloads.
- Paged file search with hashtags.
- Favorites, ratings, recently consulted files, and novelties badge.
- Workshops (motivation-based and first-come-first-served) with attachments and notifications.
- Suggestions with status tracking.
- Admin analytics dashboard endpoints.

### Security
- Per-user endpoints reject requests for another user's data (403).
