# MedRational API

[![Backend CI](https://github.com/Yorkchk/MedRational/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Yorkchk/MedRational/actions/workflows/backend-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

REST backend for **MedRational**, a study platform for clinical reasoning. Admins organize study material into **categories → clinical reasonings → study files**; students browse, search, rate, favorite, and download it.

Android client: **[Yorkchk/MedRational-Android](https://github.com/Yorkchk/MedRational-Android)**

## Features

- **Authentication:** email + password login confirmed by a one-time code sent by email (5-minute expiry), user registration, password reset. Stateless JWT sessions with `ROLE_USER` / `ROLE_ADMIN`.
- **Content management:** CRUD for categories, reasonings, and study files, with file storage on Cloudflare R2.
- **Downloads:** single files and ZIP exports per reasoning, per category, or for everything.
- **Search:** paged, filtered file search (JPA Specifications) with hashtags.
- **Engagement:** favorites, ratings, recently consulted files, and a "what's new" badge.
- **Workshops & suggestions:** workshop projects with two participation flows (motivation-based and first-come-first-served), attachments, and notifications; user suggestions with status tracking.
- **Admin analytics:** top downloads, most favorited, highest rated, category demand, storage footprint, trending hashtags, content health.

## Tech stack

| Area | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4 |
| Web & security | Spring Web MVC, Spring Security, JWT (jjwt) |
| Persistence | Spring Data JPA, Hibernate, PostgreSQL (Supabase) |
| File storage | Cloudflare R2 via the AWS S3 SDK |
| Email | Spring Mail (SMTP) |
| CI | GitHub Actions with a PostgreSQL service container |

## Architecture

```
Android app ──HTTPS/JWT──► Controllers ──► Services ──► Repositories (JPA) ──► PostgreSQL
                                              │
                                              ├──► Cloudflare R2 (file binaries)
                                              └──► SMTP (one-time codes, notifications)
```

Layered packages under `com.example.MedRational`: `Controllers` → `Services` (interfaces + implementations) → `Repositories` → `Entities`, with request/response objects in `DTOs`. Errors are mapped to HTTP statuses centrally in `GlobalExceptionHandler`. Per-user endpoints (`/api/v1/users/{userId}/…`) only allow access to the authenticated user's own data.

## API overview

All endpoints are under `/api/v1`.

| Area | Base path |
|---|---|
| Auth | `/auth` (login, verify-otp, user/register, forgot-password, reset-password) |
| Categories | `/categories` |
| Reasonings | `/reasonings` |
| Study files | `/files`, `/files/{fileId}/ratings`, `/files/{fileId}/hashtags` |
| Search | `/search/files` |
| Downloads | `/downloads/file/{id}`, `/downloads/{reasoning\|category}/{id}/zip` |
| Favorites | `/users/{userId}/favorites` |
| Download history | `/users/{userId}/recent-downloads` |
| Novelties | `/novelties` |
| Suggestions | `/suggestions` |
| Workshops | `/workshops` |
| Admin analytics | `/admin/analytics` (admin only) |

## Getting started

**Requirements:** JDK 21 and a PostgreSQL database (a Supabase project or a local Postgres). File uploads need a Cloudflare R2 bucket, and login needs an SMTP account.

1. Copy the environment template and fill it in:
   ```bash
   cp .env.example .env
   ```
   `.env` is git-ignored and loaded automatically at startup.
2. Run the API (listens on port 8080):
   ```bash
   ./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
   ```
   Hibernate creates and updates the schema on startup (`ddl-auto=update`).

### Tests

```bash
./mvnw verify
```

The test suite boots the full Spring context, so it needs a reachable PostgreSQL and the variables from `.env.example` set as **real environment variables** (`.env` is only loaded by `main`). CI provides both; see `.github/workflows/backend-ci.yml`.

## Contributing

`main` is protected and always releasable. Work happens on short-lived branches (`feat/…`, `fix/…`, `chore/…`) merged through pull requests once CI passes. Commit messages and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/). See [CHANGELOG.md](CHANGELOG.md) for release notes.

## License

[MIT](LICENSE)
