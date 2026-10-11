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

- `MedRationalApplication.main` loads `.env` (via dotenv-java) into **system properties** before Spring starts. Values in `.env` are therefore only picked up when launched through `main` — not by test runs or tooling that bypass it — so tests need these set as real env vars or system properties.
- Required variables: `SUPABASE_DB_*` (Postgres; `.env` has dev/prod blocks, toggled by commenting), `CLOUDFLARE_ACCOUNT_ID` / `CLOUDFLARE_ACCESS_KEY_ID` / `CLOUDFLARE_SECRET_ACCESS_KEY`, `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD`, `JWT_SECRET`. Optional: `LIBREOFFICE_HOME` (see Previews below).
- Config is split across two files: `application.properties` (datasource, JPA, multipart limits: 20MB/file, 50MB/request) and `application.yaml` (Cloudflare R2, mail, JWT, server).
- Schema is managed by Hibernate `ddl-auto=update` — there are no migrations. Entity changes alter the DB schema directly. The `supabase/` directory is only Supabase CLI local state.

## Architecture

Layered packages under `com.example.MedRational` (note the capitalized package names): `Controllers` → `Services/Interfaces` + `Services/Implementations` → `Repositories` (Spring Data JPA) → `Entities`, with request/response objects in `DTOs`. Lombok (`@Builder`, `@RequiredArgsConstructor`, `@Getter/@Setter`) is used throughout.

- **Mapping**: entity→DTO conversion is partly centralized in `MappingServiceImpl` (Category/Reasoning/StudyFile responses) and partly done in private `mapToDTO` methods inside individual services. Services frequently inject concrete `*ServiceImpl` classes rather than the interfaces.
- **File storage**: binaries go to Cloudflare R2 via the AWS S3 SDK (`Cloudflare/CloudflareR2Config` builds an `S3Client` with path-style access and chunked encoding disabled — both required for R2). `R2StorageServiceImpl` handles upload/download/delete; keys are `Categories/{category}/{reasoning}/{8-char-uuid}-{filename}`. DB rows store `storageKey` and `publicUrl`; deleting an entity must also delete its R2 object(s).
- **Previews**: `GET /api/v1/files/{id}/preview` always returns an inline PDF. `Preview/PreviewKind.detect` (file extension first, then content type) is the single source of truth for which files are previewable (pdf/docx/pptx/xlsx) and feeds the `previewable` flag on file DTOs. PDFs are streamed as-is; Office files are converted by LibreOffice through JODConverter (`Preview/OfficeConverter`) on first request and cached in R2 at `Previews/{storageKey}.pdf`, with the key stored in `StudyFile.previewStorageKey`. Anything that deletes a `StudyFile` must also delete its preview key. LibreOffice is a runtime dependency: if it is missing the app still starts, but Office previews return 503.
- **Search**: `Specifications/StudyFileSpecifications` builds a JPA `Specification` from `FileSearchFilterDTO` for paged, filtered file search.
- **Workshops**: `WorkshopProject` has a `ProjectType` (`MOTIVATION` vs `FIRST_COME_FIRST_SERVED`) that drives different flows in `WorkshopProjectServiceImpl`, with attachments stored in R2 and notifications via `WorkshopNotificationServiceImpl`.
- **Errors**: `Exceptions/GlobalExceptionHandler` maps `EntityNotFoundException` → 404, `IllegalArgumentException` → 400, validation errors → 400 with per-field map, upload size → 413. Services signal errors by throwing these rather than building responses.

## Security

- Stateless JWT (jjwt). `JwtAuthenticationFilter` reads `Authorization: Bearer <token>`, extracts the email, re-loads the user from the DB, and grants a single authority equal to `Role` enum name (`ROLE_USER` / `ROLE_ADMIN`).
- Login is two-step: credentials → 6-digit OTP emailed (5-minute expiry, stored on `User`) → `verify-otp` returns the JWT. User registration and forgot-password use the same OTP mechanism.
- URL-level rules live in `Security/SecurityConfig`: `/api/v1/auth/**` is public; GETs on categories/reasonings/files/downloads are public; writes to those are `ROLE_ADMIN`; everything else requires authentication. When adding endpoints, update this matcher list.
- Some controllers (`AnalyticsController`, `HashtagController`, `SuggestionController`) use `@PreAuthorize`, but **`@EnableMethodSecurity` is not declared anywhere**, so those annotations are currently not enforced.

## Git workflow

`main` is protected: changes go through short-lived branches (`feat/…`, `fix/…`, `chore/…`, `docs/…`) and pull requests that must pass `.github/workflows/backend-ci.yml` (`./mvnw -B verify` against a PostgreSQL service container with dummy env values). PRs are squash-merged, so PR titles use Conventional Commits (`feat(favorites): …`). Record user-facing changes under `[Unreleased]` in `CHANGELOG.md`. When adding a new required env var, also add it to `.env.example` and the CI workflow's `env` block.
