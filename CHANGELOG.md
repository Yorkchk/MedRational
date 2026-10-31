# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- A push notification tells students about new study files. It is sent as soon as a file is uploaded, and a batch upload sends one push for all its files ("3 new files in Heart Failure"). They show on their own Android channel (`new_files`), and are stored as `NEW_FILES` notifications.
- Staying signed in: sign-in returns a refresh token next to the JWT, and `POST /api/v1/auth/refresh` trades it for a new pair, so the app only asks to sign in again after 60 days without use. `POST /api/v1/auth/logout` ends a device's session.
  - Sessions are stored one row per device (`user_sessions`, token hashes only) and end on sign-out, password change or reset, email change and account deletion.
  - The JWT now lasts 1 hour instead of 24.
  - `PUT /api/v1/users/me/password` returns new tokens (`AuthResponse`, still with `message`), since the change signs every other device out.
  - A missing or expired token answers 401 instead of 403; 403 now only means a signed-in user isn't allowed.
- Every merge to `main` that passes CI publishes the API image to GitHub Container Registry as `ghcr.io/yorkchk/medrational:sha-<commit>` and `:latest`.
- Docker image for the API (`Dockerfile`), with LibreOffice and fonts for Office previews. It runs as a non-root user in UTC (Morocco time), and the environment, secrets and Firebase key are passed at run time.
- dev, uat and prod environments as Spring profiles (`SPRING_PROFILES_ACTIVE`, dev by default). Each one loads its own `.env.<profile>` file and has its own database, R2 bucket (`R2_BUCKET`) and workshop push topic (`WORKSHOPS_PUSH_TOPIC`). uat and prod connect to Supabase over SSL and have no database defaults, so a missing variable stops startup. Real environment variables win over the file.
- Workshop push notifications carry localization keys (`title_loc_key`/`body_loc_key`, with the project title as the argument), so the Android app shows them in French or English. The English text stays as a fallback.
- Profile endpoints for the signed-in user under `/api/v1/users/me`:
  - `GET` and `PUT` (first name, last name, phone) with no code needed.
  - Change the password with a code emailed to the current address (`POST /password/code`, then `PUT /password`).
  - Change the email with a code emailed to the new address (`POST /email/code`, then `PUT /email`, which returns a new JWT).
  - Wrong or expired codes answer 400.
- Account deletion, as Google Play requires:
  - From the app: a code emailed to the user (`POST /api/v1/users/me/delete/code`), then `DELETE /api/v1/users/me`.
  - Without the app: a public page at `/delete-account` (the link for the Play Console), backed by `POST /api/v1/auth/delete-account/code` and `/confirm`.
  - Deletes the account, favorites, recent downloads, the user's notifications, and their suggestions with their attachments. Download and view counts, ratings, and an admin's projects and replies are kept with no author.
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
- Running the tests no longer leaves a LibreOffice process behind on Windows, which made the next local start fail to launch LibreOffice (`extensions.pmap` locked). The context test runs with the new `preview.office-enabled=false`.
- Regular users can rate files: `POST`/`DELETE /api/v1/files/{fileId}/ratings` were admin-only, so rating returned 403.
- Deleting a rated file, reasoning or category no longer fails on the `file_ratings` foreign key.
- Rating stats are read with typed queries instead of an `Object[]` cast that could throw `ClassCastException`.

### Changed
- Analytics file items send `null` instead of `"Unassigned"` when a file has no reasoning or category.
- Sign-in no longer emails a code: `POST /api/v1/auth/login` checks the email and password and returns the JWT right away. `/auth/verify-otp` is gone. Codes are still needed for sign-up, forgot password, and changing the password or email or deleting the account.
- Wrong sign-in credentials answer 401 explicitly.

### Security
- `/api/v1/admin/**` is admin only. Before this, any signed-in user could read the analytics.
- `@EnableMethodSecurity` is on, so the existing `@PreAuthorize` admin checks on hashtags, suggestions and workshops are enforced. They used to be ignored.
- The rating endpoints take the rater from the JWT; the `userId` request parameter, which let anyone rate as anyone, is gone.
- Sign-up can no longer take over an existing account. Registering with the email of a verified account answered "code sent" after replacing that account's password and name. It now answers 409 and leaves the account untouched.
- Only accounts whose email was confirmed with a code (`users.email_verified`) can sign in; others get 403. Existing accounts that have signed in before, and admins, are backfilled as verified at startup.

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
