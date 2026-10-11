# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- File list responses (reasoning files, search, novelties, recent downloads) include each file's `avgRating` and `totalRatings`.
- `docs/CI_AND_TESTS.md` and `docs/GITHUB_REPO_SETUP.md` guides, linked from the README.
- GitHub Actions CI running the test suite against a PostgreSQL service container, Dependabot, and a PR template.
- `.env.example` documenting every required environment variable.

### Fixed
- Regular users can rate files: `POST`/`DELETE /api/v1/files/{fileId}/ratings` were admin-only, so rating returned 403.
- Deleting a rated file, reasoning or category no longer fails on the `file_ratings` foreign key.
- Rating stats are read with typed queries instead of an `Object[]` cast that could throw `ClassCastException`.

### Security
- The rating endpoints take the rater from the JWT; the `userId` request parameter, which let anyone rate as anyone, is gone.

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
