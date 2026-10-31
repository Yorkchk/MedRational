# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

MedRational is a Spring Boot 4 (Java 21, Maven) REST backend for a medical study-content platform: admins organize study files into **Categories → Reasonings → StudyFiles**, and students browse, search, rate, favorite, and download them. There is no frontend in this repo; all endpoints live under `/api/v1/...`.

## Commands

Use the Maven wrapper (`./mvnw` in Bash, `.\mvnw.cmd` in PowerShell):

- Build: `./mvnw clean package` (add `-DskipTests` to skip the context-load test)
- Run: `./mvnw spring-boot:run` (serves on `0.0.0.0:8080`)
- All tests: `./mvnw test`
- Single test: `./mvnw test -Dtest=MedRationalApplicationTests` (or `-Dtest=ClassName#method`)

The only existing test is a `@SpringBootTest` context load, so it needs a reachable PostgreSQL plus the env vars below. No linter/formatter is configured.

## Configuration

- **Environments** are Spring profiles: `dev` (default; local Supabase in Docker, `dev` R2 bucket), `uat` and `prod` (Supabase cloud projects over SSL, their own buckets). Choose with `SPRING_PROFILES_ACTIVE`, `-Dspring.profiles.active` or `--spring.profiles.active=`; an unknown value stops startup (`Config/EnvironmentFiles`).
- `MedRationalApplication.main` loads `.env.<profile>` (dev falls back to `.env`) via dotenv-java into **system properties** before Spring starts. Real env vars win over the file, and empty values are skipped so profile defaults apply. Values in these files are only picked up when launched through `main`, not by test runs, so tests need real env vars. Every `.env.*` except `.env.example` is git-ignored.
- Required variables: `SUPABASE_DB_*` (Postgres), `CLOUDFLARE_ACCOUNT_ID` / `CLOUDFLARE_ACCESS_KEY_ID` / `CLOUDFLARE_SECRET_ACCESS_KEY`, `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD`, `JWT_SECRET`, and in prod `R2_BUCKET`. Optional: `R2_BUCKET` and `WORKSHOPS_PUSH_TOPIC` outside prod (profile defaults), `FIREBASE_CREDENTIALS`, `LIBREOFFICE_HOME` (see Previews below).
- Config is split across `application.properties` (datasource without defaults, JPA, multipart limits: 20MB/file, 50MB/request), `application.yaml` (Cloudflare R2, push topic, mail, JWT, server) and `application-{dev,uat,prod}.yaml` (what differs per environment). Only dev has datasource defaults, so uat/prod fail at startup rather than reach the wrong database.
- `Dockerfile` builds one image for every environment (profile, env vars and the Firebase key come at run time; `.dockerignore` keeps `.env*` and keys out). It bundles LibreOffice (`LIBREOFFICE_HOME=/usr/lib/libreoffice`) and sets `TZ=UTC`, because workshop `LocalDateTime`s are compared with the server's default zone and Morocco is on permanent GMT since 2026-09-20 (Java 21.0.12 still has tzdata 2026b, which puts `Africa/Casablanca` at +01, so don't use that zone).
- Workshop pushes go to `push.workshops-topic`: `workshops` (what the app subscribes to) only in prod, `workshops-dev` / `workshops-uat` otherwise, so test pushes never reach real users.
- Schema is managed by Hibernate `ddl-auto=update` — there are no migrations. Entity changes alter the DB schema directly. The `supabase/` directory is only Supabase CLI local state.

## Architecture

Layered packages under `com.example.MedRational` (note the capitalized package names): `Controllers` → `Services/Interfaces` + `Services/Implementations` → `Repositories` (Spring Data JPA) → `Entities`, with request/response objects in `DTOs`. Lombok (`@Builder`, `@RequiredArgsConstructor`, `@Getter/@Setter`) is used throughout.

- **Mapping**: entity→DTO conversion is partly centralized in `MappingServiceImpl` (Category/Reasoning/StudyFile responses) and partly done in private `mapToDTO` methods inside individual services. Services frequently inject concrete `*ServiceImpl` classes rather than the interfaces.
- **File storage**: binaries go to Cloudflare R2 via the AWS S3 SDK (`Cloudflare/CloudflareR2Config` builds an `S3Client` with path-style access and chunked encoding disabled — both required for R2). `R2StorageServiceImpl` handles upload/download/delete; keys are `Categories/{category}/{reasoning}/{8-char-uuid}-{filename}`. DB rows store `storageKey` and `publicUrl`; deleting an entity must also delete its R2 object(s).
- **Previews**: `GET /api/v1/files/{id}/preview` always returns an inline PDF. `Preview/PreviewKind.detect` (file extension first, then content type) is the single source of truth for which files are previewable (pdf/docx/pptx/xlsx) and feeds the `previewable` flag on file DTOs. PDFs are streamed as-is; Office files are converted by LibreOffice through JODConverter (`Preview/OfficeConverter`) on first request and cached in R2 at `Previews/{storageKey}.pdf`, with the key stored in `StudyFile.previewStorageKey`. Anything that deletes a `StudyFile` must also delete its preview key. LibreOffice is a runtime dependency: if it is missing the app still starts, but Office previews return 503. `preview.office-enabled=false` skips starting it; `MedRationalApplicationTests` sets it, because on Windows the test JVM can exit without stopping `soffice`, which then locks its profile folder and makes the next local run fail with `RetryTimeoutException`.
- **Search**: `Specifications/StudyFileSpecifications` builds a JPA `Specification` from `FileSearchFilterDTO` for paged, filtered file search.
- **Workshops**: `WorkshopProject` has a `ProjectType` (`MOTIVATION` vs `FIRST_COME_FIRST_SERVED`) that drives different flows in `WorkshopProjectServiceImpl`, with attachments stored in R2 and notifications via `WorkshopNotificationServiceImpl`. Pushes carry a `PushMessage`: the English title/body as a fallback, plus a localization key (`push_{event}_{fcfs|motivation}`, sent as `title_loc_key`/`body_loc_key` with the project title as the argument) that the Android app translates into the user's language. The app has a string for every key, so a new event needs one there too. The bucket is private, so an attachment's `fileUrl` can't be opened: signed-in users get files through `GET /api/v1/workshops/attachments/{id}/download` and `.../preview`. The preview uses the same `FilePreviewServiceImpl` logic as study files, caching converted Office files under `previewStorageKey`.
- **New-file pushes**: uploads are saved with `StudyFile.pushPending = true` (null on files from before, so they're never announced), and `NewFilesNotificationServiceImpl` (second 30 of every minute) announces them in one push once no upload came in the last 2 minutes, or once the oldest has waited 15 minutes. One file sends `push_new_file` (file name, reasoning title) with `fileId`/`reasoningId` data, several send `push_new_files_reasoning` or `push_new_files` (count first). They go to the same topic as workshop pushes, on the app's `new_files` channel (`PushMessage.channelId`), so students can mute them and keep workshop alerts.
- **Profile**: `UserProfileController` (`/api/v1/users/me`) passes `CurrentUserGuard.requireCurrentUser()` to `UserProfileServiceImpl`.
  - Names and phone change directly, and `fullName` is kept as "first last". `Services/Support/UserNames` derives first/last for accounts created before the split.
  - A password change needs a code sent to the current email. An email change stores `User.pendingEmail` and sends the code to the new address. Confirming it returns a new JWT, because the token's subject is the email.
  - `Services/Support/OtpCodes` issues and checks those codes and throws `IllegalArgumentException` (400), never `BadCredentialsException`. The Android app signs out on any 401/403.
- **Account deletion** (`AccountDeletionServiceImpl`, required by Google Play): a code emailed to the account's address, then the delete. Two entry points:
  - In the app: `POST /api/v1/users/me/delete/code`, then `DELETE /api/v1/users/me {code}` (204).
  - Without the app: the public page `/delete-account` (`resources/static/delete-account.html`, the link given to Google Play) calls `POST /api/v1/auth/delete-account/code {email}` and `/confirm {email, code}`. Both answer the same for unknown emails, so the page doesn't reveal who has an account.
  - Deleted: the user row, favorites, `user_file_downloads`, the user's own notifications (never the broadcasts), and their suggestions with their R2 attachments. `user_daily_activity` goes through its `ON DELETE CASCADE`.
  - Also deleted: the user's sessions (`user_sessions`), which signs every device out.
  - Kept with the user set to null, so counts and averages don't move: `file_download_events`, `file_view_events`, `file_ratings`, and an admin's `workshop_projects` and `suggestion_messages`. Those author/rater columns are nullable (`Config/AccountDeletionSchemaMigration` drops the old NOT NULL), so code reading them must handle null.
  - A new table that points to `users` must be added to this service, either deleted or anonymised.
- **Statistics**: `GET /api/v1/admin/analytics/dashboard` (`AnalyticsServiceImpl.getStatistics`) returns everything the app's admin screen shows in one `AdminStatisticsDTO`. "Students" means `ROLE_USER`, and weeks are rolling 7-day windows ending today, from the injected `Clock`.
  - Sources: `file_download_events` (one row per download), `file_view_events` (`POST /api/v1/files/{id}/views`, students only, deduplicated over 30 minutes) and `user_daily_activity` (one row per user and day, written by `ActivityTrackingServiceImpl` from `JwtAuthenticationFilter`, with an in-memory cache so it costs one insert a day).
  - Time series use native Postgres queries that return a bucket index counted back from today (`StatisticsRows.BucketCount`), and `series(...)` fills the empty periods.
  - The new tables' foreign keys are `ON DELETE CASCADE` (`@OnDelete`), so deleting a file needs no cleanup for them. Deleting an account anonymises `file_view_events` first, so the views still count.
- **Errors**: `Exceptions/GlobalExceptionHandler` maps `EntityNotFoundException` → 404, `IllegalArgumentException` → 400, validation errors → 400 with per-field map, upload size → 413. Services signal errors by throwing these rather than building responses.

## Security

- Stateless JWT (jjwt). `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, extracts the email, re-loads the user from the DB, and grants a single authority equal to `Role` enum name (`ROLE_USER` / `ROLE_ADMIN`).
- **Sessions (staying signed in):** the JWT lasts 1 hour (`jwt.expiration`). `/auth/login` also returns a `refreshToken`, which the app trades at `POST /auth/refresh` for a new JWT and refresh token; `POST /auth/logout` ends it.
  - `user_sessions` (`UserSession`, `UserSessionServiceImpl`) holds one row per signed-in device. It stores only the SHA-256 of the token, and a renewal replaces the token on the same row and pushes `expiresAt` 60 days ahead (`jwt.refresh-expiration-days`). A device unused for 60 days signs in again; a nightly job deletes expired rows.
  - A replaced, unknown or expired refresh token answers 401. There is no reuse detection (on purpose); it can be added with a `previousTokenHash` column.
  - Sessions end on: sign-out (that device), password change and email change (all devices; the response carries new tokens for the current one, so `PUT /users/me/password` returns an `AuthResponse`), password reset (all), and account deletion (all). Sign-up verification starts no session, because the app signs in afterwards.
  - `SecurityConfig` answers **401** for a missing or expired token (so the app renews) and **403** only when a signed-in user isn't allowed. The app signs out on 403, never on 401.
- Login is email + password only: `/auth/login` returns the JWT, with no code. Only accounts with `emailVerified` may sign in; it is set when the sign-up code (`user/verify-registration`) or a forgot-password code is confirmed, and `EmailVerificationSchemaMigration` backfills it for existing accounts. Registering with the email of a verified account answers 409 and never touches it; an unverified (abandoned) sign-up can be redone. Sign-up, forgot-password and the profile flows (password, email change, account deletion) still use the 6-digit OTP (5-minute expiry, stored on `User`).
- URL-level rules live in `Security/SecurityConfig`: `/api/v1/auth/**` and the `/delete-account` page are public; GETs on categories/reasonings/files/downloads are public; writes to those are `ROLE_ADMIN`; `/api/v1/admin/**` is `ROLE_ADMIN`; everything else requires authentication. When adding endpoints, update this matcher list.
- `@EnableMethodSecurity` is on, so `@PreAuthorize` is enforced. Write admin checks as `hasAuthority('ROLE_ADMIN')`, since the authority is the raw enum name.

## Git workflow

`main` is protected: changes go through short-lived branches (`feat/…`, `fix/…`, `chore/…`, `docs/…`) and pull requests that must pass `.github/workflows/backend-ci.yml` (`./mvnw -B verify` against a PostgreSQL service container with dummy env values). PRs are squash-merged, so PR titles use Conventional Commits (`feat(favorites): …`). Record user-facing changes under `[Unreleased]` in `CHANGELOG.md`. When adding a new required env var, also add it to `.env.example` and the CI workflow's `env` block.

After CI passes on `main`, `.github/workflows/publish-image.yml` builds the `Dockerfile` from that commit and pushes `ghcr.io/yorkchk/medrational:sha-<commit>` and `:latest`. Deploy by the `sha-` tag, so prod runs the image tested in uat. The workflow uses `workflow_run`, so it only runs from `main` and can't be tried on a PR.

## Testing policy

Every new feature or behavior change ships with tests in the same PR, and every bug fix includes a regression test that fails without the fix. Untested features are not considered done. See `docs/CI_AND_TESTS.md` §10 for test patterns and examples.
