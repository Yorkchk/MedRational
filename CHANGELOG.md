# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- Workshop push notifications carry localization keys (`title_loc_key`/`body_loc_key`, with the project title as the argument), so the Android app shows them in French or English. The English text stays as a fallback.
- Profile endpoints for the signed-in user under `/api/v1/users/me`:
  - `GET` and `PUT` (first name, last name, phone) with no code needed.
  - Change the password with a code emailed to the current address (`POST /password/code`, then `PUT /password`).
  - Change the email with a code emailed to the new address (`POST /email/code`, then `PUT /email`, which returns a new JWT).
  - Wrong or expired codes answer 400.
- Users have separate `firstName`/`lastName` columns. Registration accepts them (optional), and accounts that only have a `fullName` are split at the first space.
- Code emails say what the code is for (password change, new email).
- File list responses (reasoning files, search, novelties, recent downloads) include each file's `avgRating` and `totalRatings`.
- `docs/CI_AND_TESTS.md` and `docs/GITHUB_REPO_SETUP.md` guides, linked from the README.
- GitHub Actions CI running the test suite against a PostgreSQL service container, Dependabot, and a PR template.
- `.env.example` documenting every required environment variable.
- Admin statistics in one call, `GET /api/v1/admin/analytics/dashboard`:
  - Students: total, new this week, weekly and daily active students, activated students (at least one file downloaded or opened), plus 12 weeks of sign-ups and active students.
  - Usage: downloads and file views this week against the previous week, and 30 days of daily counts.
  - Content over the last 7 days and all time: most downloaded, viewed and favorited files, activity per category, and hashtags by downloads.
  - Trending files and categories (downloads plus views rising against the previous week).
  - Engagement: favorites, ratings, average rating, highest rated files, open and unread suggestions, workshops by status, and files never downloaded.
- Active students are tracked per day (`user_daily_activity`), recorded at most once a day per user from the JWT filter.
- `POST /api/v1/files/{id}/views` records a student opening a file (`file_view_events`). A file reopened within 30 minutes counts once, and admins aren't counted.
- Analytics file items include the file's `reasoningId`.

### Fixed
- Regular users can rate files: `POST`/`DELETE /api/v1/files/{fileId}/ratings` were admin-only, so rating returned 403.
- Deleting a rated file, reasoning or category no longer fails on the `file_ratings` foreign key.
- Rating stats are read with typed queries instead of an `Object[]` cast that could throw `ClassCastException`.

### Changed
- Analytics file items send `null` instead of `"Unassigned"` when a file has no reasoning or category.

### Security
- `/api/v1/admin/**` is admin only. Before this, any signed-in user could read the analytics.
- `@EnableMethodSecurity` is on, so the existing `@PreAuthorize` admin checks on hashtags, suggestions and workshops are enforced. They used to be ignored.
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
