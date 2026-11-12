# MedRational API

[![Backend CI](https://github.com/Yorkchk/MedRational/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Yorkchk/MedRational/actions/workflows/backend-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

REST backend of **SWISRA** (*Study With Scome RAbat*), a study app for medical students, built as the MedRational project.

Android client: **[Yorkchk/MedRational-Android](https://github.com/Yorkchk/MedRational-Android)**

## About the project

### Why it exists

SWISRA was built in collaboration with a medical student from **SCOME Rabat**, a student association, to make students' lives easier. The association shares study material and runs workshops for medical students. SWISRA gives them one app for both:

- **All the study material in one place,** organized by subject and by clinical reasoning, searchable, and readable on the phone without downloading anything.
- **No missed workshops:** students get a notification when a workshop is announced, 10 minutes before registration opens and when it opens. This matters most for first-come, first-served workshops.
- **Material that stays up to date:** students are notified of new files, and a "What's new" feed shows what was added since their last visit.
- **Feedback to the association** through suggestions, and **statistics for its team** on what students actually use.
### What it does

SWISRA helps medical students practise **clinical reasoning**. Admins organize study material in three levels:

```
Category  (e.g. Cardiology)
└── Reasoning  (a clinical reasoning, e.g. "Chest pain")
    └── Study files  (PDFs, Word, PowerPoint, Excel, images…)
```

Students browse that tree from the Android app. They can search, preview, download, share, rate and favorite files, get notified when new material is added, and sign up for workshops ("projects"). Admins manage the content and the workshops from the same app, and see usage statistics.

This repository is the API behind the app. It is a Spring Boot service that stores data in PostgreSQL (Supabase), keeps files in Cloudflare R2, sends email through Amazon SES and push notifications through Firebase Cloud Messaging. There is no web frontend here; every endpoint lives under `/api/v1`. The only HTML page is the public account-deletion page required by Google Play (`/delete-account`).

## Features

### For students

- **Accounts:** sign up with an emailed 6-digit code, sign in with email + password, reset a forgotten password. A device stays signed in for up to 60 days of inactivity.
- **Browse and search:** the category → reasoning → file tree, and a paged search across files with filters and hashtags.
- **Preview:** any PDF, Word, PowerPoint or Excel file can be read in the app without downloading it. Office files are converted to PDF on the server the first time and the result is cached.
- **Download:** single files, or a whole reasoning, category or the full library as a ZIP. Recent downloads are kept per user.
- **Share:** send a file, several files, a whole reasoning or a whole category (one ZIP with a folder per reasoning) to email, Google Drive, OneDrive, Quick Share or any other app on the phone, to open them on a laptop or tablet. Shared files also appear in Recent downloads.
- **Favorites and ratings:** favorite files and rate them from 1 to 5. Each file shows its average rating.
- **What's new:** a feed and badge of files added since the student's last visit.
- **Workshops ("projects"):** see upcoming workshops, with their attachments, and register through the external form while registration is open. Two kinds exist: first come, first served, and motivation-based (selected by a committee).
- **Push notifications:** new study files, a new workshop, 10 minutes before registration opens, when it opens, 10 minutes before it closes and when it closes.
- **Suggestions:** send feedback to the team, with an optional attachment.
- **Profile:** edit name and phone number, change password or email (confirmed by code), and delete the account. Accounts can also be deleted from a public web page, without the app.

### For admins

- Create, edit and delete categories, reasonings and files, including uploading several files at once.
- Create and edit workshops, add attachments, and open or close registration by hand when needed.
- Read and delete the suggestions inbox.
- A statistics dashboard: downloads, views, active students, top and trending files, ratings, category demand, storage footprint and content health.

## Technical guide

**Contents**

1. [Architecture](#1-architecture)
2. [Data model (ERD)](#2-data-model-erd)
3. [The modules in action](#3-the-modules-in-action)
4. [Scheduled jobs](#4-scheduled-jobs)
5. [API reference](#5-api-reference)
6. [Tools used](#6-tools-used)
7. [Running it locally](#7-running-it-locally)
8. [Tests](#8-tests)
9. [Deployment and operations](#9-deployment-and-operations)

### 1. Architecture

#### 1.1 Overview

![Architecture: the Android app calls the Spring Boot API through Caddy; the API uses PostgreSQL, Cloudflare R2, Amazon SES, Firebase and LibreOffice](docs/diagrams/architecture.svg)
<sub>Source: [`architecture.mmd`](docs/diagrams/architecture.mmd)</sub>

#### 1.2 Code layout

The code is layered, under `com.example.MedRational`:

| Package | Role |
|---|---|
| `Controllers` | REST endpoints. Validate input, apply rate limits, call a service. |
| `Services/Interfaces`, `Services/Implementations` | Business logic. Services signal errors by throwing exceptions. |
| `Services/Support` | Small shared helpers (one-time codes, name splitting). |
| `Repositories` | Spring Data JPA repositories, plus native Postgres queries for statistics. |
| `Entities`, `Entities/enums` | JPA entities (the tables). |
| `DTOs` | Request and response objects. Entities never leave the API. |
| `Specifications` | JPA `Specification`s for the file search. |
| `Security` | JWT filter, `SecurityConfig` URL rules, rate limiter. |
| `Preview` | Detecting previewable files and converting Office files with LibreOffice. |
| `Cloudflare` | The S3 client configured for R2. |
| `Config` | Environment loading and one-off schema fixes run at startup. |
| `Exceptions` | `GlobalExceptionHandler`: maps exceptions to HTTP statuses. |

#### 1.3 Error handling

Errors are mapped centrally: not found → 404, invalid input → 400 (with a per-field map for validation errors), upload too large → 413, too many attempts → 429. A missing or expired token is 401 and a signed-in user without the right role is 403.

### 2. Data model (ERD)

The schema is created and updated by Hibernate from the entities (`ddl-auto=update`). It is shown in four parts, with the main fields only. `users` and `study_files` appear in several parts, with only the fields that part needs.

#### 2.1 Content

![ERD of categories, reasonings, study files and hashtags](docs/diagrams/erd-content.svg)
<sub>Source: [`erd-content.mmd`](docs/diagrams/erd-content.mmd)</sub>

#### 2.2 Engagement

![ERD of favorites, ratings, recent downloads, download events and view events between users and study files](docs/diagrams/erd-engagement.svg)
<sub>Source: [`erd-engagement.mmd`](docs/diagrams/erd-engagement.mmd)</sub>

#### 2.3 Accounts

![ERD of users, sessions, daily activity and notifications](docs/diagrams/erd-accounts.svg)
<sub>Source: [`erd-accounts.mmd`](docs/diagrams/erd-accounts.mmd)</sub>

#### 2.4 Workshops and suggestions

![ERD of workshops, their attachments, suggestions and their messages](docs/diagrams/erd-workshops-suggestions.svg)
<sub>Source: [`erd-workshops-suggestions.mmd`](docs/diagrams/erd-workshops-suggestions.mmd)</sub>

#### 2.5 Rules worth knowing

- **Unique pairs:** a user has at most one favorite, one rating and one recent-download row per file (`uq_user_file_*`), and one activity row per day.
- **Deleting content cascades:** a category deletes its reasonings, which delete their files. Their R2 objects and cached previews are deleted with them.
- **Deleting an account** removes the user, their sessions, favorites, recent downloads, own notifications and suggestions. Ratings, download and view events, and an admin's workshops and replies are kept with the user set to `null`, so statistics don't change.
- **Events vs history:** `file_download_events` and `file_view_events` keep one row per download or view, for statistics. `user_file_downloads` keeps only the latest download per user and file, for the Recent downloads screen.

### 3. The modules in action

#### 3.1 Authentication and sessions

**How it works.** Sign-up and password reset confirm the email with a 6-digit code (valid 5 minutes). Sign-in needs only the email and password, and only verified accounts may sign in. The access token (JWT) lasts 1 hour. A refresh token, stored hashed in `user_sessions`, renews it for up to 60 days of inactivity.

![Sequence: register, verify the code, log in, then renew the JWT with the refresh token](docs/diagrams/auth-session.svg)
<sub>Source: [`auth-session.mmd`](docs/diagrams/auth-session.mmd)</sub>

**Access rules.**

- `SecurityConfig` holds the URL rules. Reading categories, reasonings, files and downloads is public; writing them and everything under `/admin` needs `ROLE_ADMIN`; the rest needs a signed-in user.
- The API answers **401** for a missing or expired token, so the app renews, and **403** only when a signed-in user lacks the role, so the app signs out.

**Session safety.**

- Changing the password or email, resetting the password and deleting the account end the sessions on every device.
- Login, sending codes and checking codes are rate limited per email and per IP (429 with `Retry-After`).

**Admin accounts.** There is no API to create admins. Promote an existing account in the database: `update users set role = 'ROLE_ADMIN' where email = '…';`

#### 3.2 Content: categories, reasonings, files

Admins upload files as multipart requests: one file to `/files/upload/{reasoningId}`, several to `/files/upload-batch/{reasoningId}` (20 MB per file, 50 MB per request). Each file is stored in R2 under `Categories/{category}/{reasoning}/{8-char-id}-{filename}`, and its row stores that key.

![Sequence: an admin uploads several files; each goes to R2 and the database, then one push is sent](docs/diagrams/file-upload.svg)
<sub>Source: [`file-upload.mmd`](docs/diagrams/file-upload.mmd)</sub>

#### 3.3 Previews

`GET /files/{id}/preview` always answers with an inline PDF. PDFs are streamed as they are. Word, PowerPoint and Excel files are converted by LibreOffice (through JODConverter) on the first request, and the PDF is cached in R2 under `Previews/…`, so later requests are instant. Without LibreOffice the API still runs, but Office previews answer 503. Workshop attachments and suggestion attachments use the same logic.

#### 3.4 Search and hashtags

`GET /search/files` builds a JPA `Specification` from the filters (file name, category name, reasoning title, hashtag) and returns a page. Admins attach hashtags to files through `/files/{fileId}/hashtags`.

#### 3.5 Downloads and history

`/downloads/file/{id}` streams one file from R2 and, for a signed-in user, records a download event and updates their recent downloads. `/downloads/{reasoning|category}/{id}/zip` and `/downloads/all/zip` return a ZIP archive built on request.

#### 3.6 Favorites, ratings and novelties

- **Favorites** live under `/users/{userId}/favorites`. A user can only reach their own.
- **Ratings:** `POST`, `GET` and `DELETE` on `/files/{fileId}/ratings`. Every call returns the file's new average, count and the caller's own score, and the average is stored on the file so lists don't recompute it.
- **Novelties:** a file is new for a user when uploaded after their `novelties_seen_at`. `/novelties/badge-count` feeds the home badge, and `/novelties/mark-seen` moves the baseline.

#### 3.7 Workshops and push notifications

**Lifecycle.** A workshop moves through three statuses. A scheduler runs every minute and changes the status at the registration dates, and sends push alerts to the environment's FCM topic:

![Workshop statuses: UPCOMING, OPEN, CLOSED, moved by the dates or by an admin](docs/diagrams/workshop-status.svg)
<sub>Source: [`workshop-status.mmd`](docs/diagrams/workshop-status.mmd)</sub>

**Rules.**

- Flags on the workshop record which alerts were sent, so a restart never repeats them and an admin's manual status stays until the next date ahead.
- The form link is hidden from students until registration is open. Registration itself happens on the external form.

**Push notifications.**

- Pushes carry a localization key (`push_{event}_{fcfs|motivation}`) that the app translates into French or English, with an English fallback.
- Each environment has its own topic (`workshops-dev`, `workshops-uat`, `workshops`), so tests never reach real students. Without `FIREBASE_CREDENTIALS`, pushes are skipped with a warning.

#### 3.8 Suggestions

Students send a suggestion with an optional attachment (multipart, 10 MB). Admins list the pending ones, open one to see the sender's contact details, preview or download the attachment, and delete it. The bucket is private, so attachments are only served through the API to admins.

#### 3.9 Profile and account deletion

`/users/me` reads and edits the profile. Changing the password or email needs a code: the email change sends it to the **new** address and returns a new JWT, since the token's subject is the email. Account deletion also needs a code, either in the app (`DELETE /users/me`) or from the public `/delete-account` page, which answers the same for unknown emails so it doesn't reveal who has an account.

#### 3.10 Statistics

`GET /admin/analytics/dashboard` returns everything the admin screen shows in one call. It reads `file_download_events`, `file_view_events` (recorded by the app, one view per student per file every 30 minutes) and `user_daily_activity` (written by the JWT filter once a day per user). "Students" are non-admin accounts, and a week is the last 7 days including today.

### 4. Scheduled jobs

| Job | When | What |
|---|---|---|
| Workshop scheduler | every minute | moves statuses, sends workshop pushes |
| Session cleanup | 03:00 daily | deletes expired refresh-token sessions |
| Rate-limit cleanup | every 10 min | drops old in-memory counters |
| Database backup (GitHub Actions) | 02:00 UTC daily | `pg_dump` of prod to a private R2 bucket, kept 30 days |

### 5. API reference

All paths are under `/api/v1`. 🔓 public · 👤 signed in · 🛡️ admin.

| Area | Endpoints |
|---|---|
| Auth 🔓 | `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/user/register`, `/auth/user/verify-registration`, `/auth/forgot-password`, `/auth/reset-password`, `/auth/delete-account/{code,confirm}` |
| Categories | `GET /categories`, `/categories/{id}`, `/categories/search` 🔓 · `POST`, `PUT /{id}`, `DELETE /{id}` 🛡️ |
| Reasonings | `GET /reasonings/category/{categoryId}`, `/reasonings/{id}` 🔓 · `POST`, `PUT /{id}`, `DELETE /{id}` 🛡️ |
| Study files | `GET /files/reasoning/{reasoningId}`, `/files/{id}/preview`, `/files/search` 🔓 · `POST /files/{id}/views` 👤 · `POST /files/upload/{reasoningId}`, `/files/upload-batch/{reasoningId}`, `PUT`/`DELETE /files/{id}` 🛡️ |
| Hashtags | `GET /files/{fileId}/hashtags` · `POST`, `DELETE /{hashtagId}` 🛡️ |
| Ratings 👤 | `GET`, `POST`, `DELETE /files/{fileId}/ratings` |
| Search 👤 | `GET /search/files` |
| Downloads 🔓 | `GET /downloads/file/{id}`, `/downloads/reasoning/{id}/zip`, `/downloads/category/{id}/zip`, `/downloads/all/zip`, `POST /downloads/files/zip` |
| Favorites 👤 | `GET /users/{userId}/favorites`, `POST`/`DELETE /users/{userId}/favorites/{fileId}`, `POST …/toggle`, `GET …/status` |
| Recent downloads 👤 | `GET /users/{userId}/recent-downloads` |
| Novelties 👤 | `GET /novelties`, `/novelties/badge-count`, `POST /novelties/mark-seen` |
| Profile 👤 | `GET`, `PUT`, `DELETE /users/me`, `POST /users/me/{password,email,delete}/code`, `PUT /users/me/{password,email}` |
| Workshops 👤 | `GET /workshops`, `/workshops/{id}`, `/workshops/attachments/{id}/{download,preview}` · `POST`, `PUT /{id}`, `PATCH /{id}/status`, `DELETE /{id}`, `POST /{id}/attachments`, `DELETE /attachments/{id}` 🛡️ |
| Suggestions | `POST /suggestions` 👤 · `GET /suggestions`, `/{id}`, `/{id}/attachment[/preview]`, `DELETE /{id}` 🛡️ |
| Statistics 🛡️ | `GET /admin/analytics/dashboard` (plus older single-figure endpoints under `/admin/analytics`) |
| Health 🔓 | `GET /actuator/health` |

### 6. Tools used

| Area | Tools |
|---|---|
| Language and framework | Java 21, Spring Boot 4.1 (Web MVC, Data JPA, Security, Validation, Mail, Actuator), Lombok |
| Database | PostgreSQL on Supabase (Supabase CLI in Docker for local development), Hibernate |
| Authentication | JWT with jjwt 0.12, BCrypt password hashes, SHA-256-hashed refresh tokens |
| File storage | Cloudflare R2 through the AWS SDK for Java v2 (S3 API) |
| Office previews | LibreOffice + JODConverter 4.4 |
| Email | Amazon SES over SMTP |
| Push notifications | Firebase Admin SDK (Cloud Messaging topics) |
| Configuration | Spring profiles `dev` / `uat` / `prod`, `.env` files loaded with dotenv-java |
| Build and tests | Maven (wrapper included), JUnit 5, Spring Boot Test, MockMvc, JaCoCo coverage |
| Load testing | k6 ([`loadtest/`](loadtest/README.md)) |
| Packaging | Docker (one image for every environment, LibreOffice included) |
| CI/CD | GitHub Actions: tests on every PR, image published to GitHub Container Registry, deploy over SSH, nightly database backup |
| Hosting | Ubuntu server with Docker Compose behind Caddy (automatic HTTPS) |

### 7. Running it locally

This guide starts from a computer with nothing installed and ends with the API running on `http://localhost:8080`, an admin account, and a first uploaded file. Commands are given for **Git Bash, macOS or Linux** (`bash`), with the Windows PowerShell version where it differs. Run every command **from the repository root** unless the step says otherwise.

#### 7.1 What you need, and why

| Piece | Needed to start the API? | Needed for |
|---|---|---|
| JDK 21 | yes | building and running |
| Docker | yes (for the database) | running PostgreSQL locally |
| PostgreSQL | yes | everything |
| A JWT secret | yes | signing tokens |
| An SMTP email account | its variables must be set | **signing up**: the 6-digit code is emailed, and sign-up fails if the email can't be sent |
| A Cloudflare R2 bucket | its variables must be set | uploading, downloading and previewing files |
| LibreOffice | no | previews of Word, PowerPoint and Excel files (PDF previews work without it) |
| A Firebase service-account key | no | push notifications |

The API refuses to start if a required variable is missing, but it doesn't check the email or R2 credentials at startup. Wrong values only fail when an email is sent or a file is stored.

#### 7.2 Install the tools

1. **Git:** <https://git-scm.com/downloads>. On Windows this also installs **Git Bash**, which the commands below use.
2. **JDK 21** (any distribution; Temurin shown):
   - Windows: `winget install EclipseAdoptium.Temurin.21.JDK`
   - macOS: `brew install --cask temurin@21`
   - Ubuntu/Debian: `sudo apt install openjdk-21-jdk`

   Open a **new** terminal and check:
   ```bash
   java -version      # must say version "21..."
   ```
   If it shows another version, set `JAVA_HOME` to the JDK 21 folder (the Maven wrapper uses `JAVA_HOME` when it is set, otherwise the `java` on your `PATH`). You don't need to install Maven: `./mvnw` downloads it.
3. **Docker:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) on Windows and macOS, or Docker Engine on Linux. **Start it** and check:
   ```bash
   docker run --rm hello-world
   ```

#### 7.3 Get the code

```bash
git clone https://github.com/Yorkchk/MedRational.git
cd MedRational
```

#### 7.4 Start the database

The `dev` profile connects to `127.0.0.1:54322`, database `postgres`, user `postgres`, password `postgres`, without SSL. Pick **one** of these two ways to run it. In both, the API creates every table itself the first time it starts, so there's no script to run.

**Option A: a plain PostgreSQL container (lightest, recommended).**

```bash
docker run -d --name medrational-db -e POSTGRES_PASSWORD=postgres -p 54322:5432 postgres:17
```

- Check it's running: `docker ps` lists `medrational-db`.
- Stop it with `docker stop medrational-db` and start it again with `docker start medrational-db`. The data is kept until you remove the container (`docker rm medrational-db`).
- To run SQL: `docker exec -it medrational-db psql -U postgres`

**Option B: the Supabase CLI (what the project uses; adds the Supabase Studio web UI).**

1. Install the CLI: <https://supabase.com/docs/guides/local-development/cli/getting-started>
   - Windows: `scoop bucket add supabase https://github.com/supabase/scoop-bucket.git` then `scoop install supabase` (needs [Scoop](https://scoop.sh)), or prefix every command with `npx` instead of installing.
   - macOS/Linux: `brew install supabase/tap/supabase`
2. From the repository root (it reads `supabase/config.toml`):
   ```bash
   supabase start
   ```
   The first run downloads several GB of Docker images, so check you have the disk space. When it finishes it prints the database URL (`postgresql://postgres:postgres@127.0.0.1:54322/postgres`) and Studio's URL (<http://127.0.0.1:54323>).
3. Stop it with `supabase stop`. To run SQL, use Studio's **SQL Editor**, or `docker exec -it supabase_db_MedRational psql -U postgres`.

#### 7.5 Create the secrets and accounts

**a. JWT secret (required).** Any random text of at least 32 characters. To generate one:

```bash
openssl rand -base64 48
```
```powershell
# PowerShell
[Convert]::ToBase64String([byte[]](1..48 | ForEach-Object { Get-Random -Maximum 256 }))
```

**b. Email (required to sign up).** The API sends mail over SMTP with STARTTLS **required**, so the server must support STARTTLS (usually port 587). Local mail catchers without TLS won't work. Use one of these:

- **Gmail (quickest for local testing):**
  1. Turn on 2-Step Verification on the Google account.
  2. Create an app password at <https://myaccount.google.com/apppasswords>. Google shows 16 letters; write them without spaces.
  3. Use `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME=` and `MAIL_FROM=` your Gmail address, `MAIL_PASSWORD=` the app password.
- **Amazon SES (what production uses):**
  1. In the SES console, pick a region and open **Identities → Create identity**. Verify an email address (or a domain) to send from.
  2. Open **SMTP settings → Create SMTP credentials**. This gives an SMTP username and password, which are **not** your AWS access keys.
  3. Use `MAIL_HOST=email-smtp.<region>.amazonaws.com` (the region of the identity), `MAIL_PORT=587`, the SMTP username and password, and `MAIL_FROM=` the verified identity, e.g. `SWISRA <no-reply@example.com>`.
  4. A new SES account is in the **sandbox**: it can only send to verified addresses. Verify the address you'll sign up with too, or request production access.

**c. Cloudflare R2 (required for files).** All files and previews are stored there.

1. Create a Cloudflare account, open **R2 Object Storage** in the dashboard and enable it (Cloudflare may ask for a payment method, even for the free tier).
2. **Create bucket** and name it `dev` (the `dev` profile's default; another name works if you set `R2_BUCKET`). Leave it private: the API serves the files itself.
3. Copy your **Account ID**, shown on the R2 overview page.
4. Open **Manage API tokens → Create API token**, choose the **Object Read & Write** permission, limit it to the `dev` bucket, and create it. Copy the **Access Key ID** and **Secret Access Key**: the secret is shown only once.

**d. LibreOffice (optional, for Office previews).** Install it from <https://www.libreoffice.org/download/> (Windows: `winget install TheDocumentFoundation.LibreOffice`; Ubuntu: `sudo apt install libreoffice`). It is found automatically in its default location. If you installed it elsewhere, set `LIBREOFFICE_HOME` to its install folder (the one containing `program/`).

**e. Firebase (optional, for push notifications).**

1. In the [Firebase console](https://console.firebase.google.com), open the project the Android app uses → **Project settings → Service accounts → Generate new private key**.
2. Save the JSON file as `firebase-service-account.json` at the repository root (it is git-ignored) and set `FIREBASE_CREDENTIALS=firebase-service-account.json`.
3. Locally, pushes go to the `workshops-dev` topic, which only dev builds of the app subscribe to.

#### 7.6 Write the `.env.dev` file

Copy the template:

```bash
cp .env.example .env.dev
```
```powershell
Copy-Item .env.example .env.dev
```

Then fill it in. A complete `dev` example:

```dotenv
# Database: leave empty to use the local defaults (127.0.0.1:54322, postgres/postgres)
SUPABASE_DB_HOST=
SUPABASE_DB_PORT=
SUPABASE_DB_DATABASE=
SUPABASE_DB_USERNAME=
SUPABASE_DB_PASSWORD=
# uat/prod only
SUPABASE_PROJECT_REF=
DB_POOL_SIZE=

JWT_SECRET=paste-the-generated-secret-here

CLOUDFLARE_ACCOUNT_ID=your-account-id
CLOUDFLARE_ACCESS_KEY_ID=your-access-key-id
CLOUDFLARE_SECRET_ACCESS_KEY=your-secret-access-key
# empty = "dev"
R2_BUCKET=

MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=you@gmail.com
MAIL_PASSWORD=abcdefghijklmnop
MAIL_FROM=you@gmail.com

# optional, e.g. firebase-service-account.json
FIREBASE_CREDENTIALS=
# empty = "workshops-dev"
WORKSHOPS_PUSH_TOPIC=
# optional, empty = auto-detect
LIBREOFFICE_HOME=
```

How the file is read:

- It must be named exactly `.env.dev` (a plain `.env` also works for `dev`) and sit in the folder you start the API from, the repository root.
- Write `KEY=value` with no quotes, and put comments on their own line, never after a value. Empty values are ignored, so the defaults apply.
- Real environment variables win over the file.
- Every `.env.*` file except `.env.example` is git-ignored, so your secrets stay out of Git.

#### 7.7 Run the API

```bash
./mvnw spring-boot:run
```
```powershell
.\mvnw.cmd spring-boot:run
```

The first run downloads Maven and every dependency, which takes a few minutes. The API is ready when the log shows `Started MedRationalApplication`. It listens on port **8080** on every network interface, so a phone on the same Wi-Fi can reach it too.

Two messages are normal when the optional pieces are missing:
- `FIREBASE_CREDENTIALS is not set: push notifications are disabled`
- `LibreOffice could not be started; Office document previews are disabled.`: Office previews then answer 503, and everything else works.

**From IntelliJ IDEA** instead: open the folder, let it import the Maven project, and run `MedRationalApplication`. Keep the run configuration's working directory on the repository root, so `.env.dev` is found.

To stop the API, press `Ctrl+C`.

#### 7.8 Check it works

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

In Windows PowerShell, `curl` is a different command, so type `curl.exe`. For the JSON requests below, Git Bash is easier, because PowerShell changes the quotes.

#### 7.9 Create your first admin and try it

1. **Sign up.** The phone number must be exactly 10 digits and the password at least 6 characters:
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/user/register \
     -H "Content-Type: application/json" \
     -d '{"fullName":"Test Admin","firstName":"Test","lastName":"Admin","email":"you@example.com","password":"secret123","phoneNumber":"0600000000"}'
   ```
   A 6-digit code arrives by email. It is valid for 5 minutes.
2. **Confirm the code:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/user/verify-registration \
     -H "Content-Type: application/json" \
     -d '{"email":"you@example.com","code":"123456"}'
   ```
3. **Make the account an admin.** There is no API for this on purpose. Run this SQL (see [7.4](#74-start-the-database) for how to open `psql` or Studio):
   ```sql
   update users set role = 'ROLE_ADMIN' where email = 'you@example.com';
   ```
4. **Sign in** and copy the `token` from the response:
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"you@example.com","password":"secret123"}'

   TOKEN=paste-the-token-here
   ```
5. **Create a category, a reasoning and upload a file** (the IDs come from each response; `1` on a new database):
   ```bash
   curl -X POST http://localhost:8080/api/v1/categories \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"name":"Cardiology","description":"Heart and vessels"}'

   curl -X POST http://localhost:8080/api/v1/reasonings \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"categoryId":1,"title":"Chest pain"}'

   curl -X POST http://localhost:8080/api/v1/files/upload/1 \
     -H "Authorization: Bearer $TOKEN" \
     -F "file=@/path/to/some.pdf" -F "description=First file"
   ```
6. **Read it back**, without a token, since browsing is public:
   ```bash
   curl http://localhost:8080/api/v1/files/reasoning/1
   curl -o preview.pdf http://localhost:8080/api/v1/files/1/preview
   ```

#### 7.10 Connect the Android app

1. Clone and open [MedRational-Android](https://github.com/Yorkchk/MedRational-Android) and run its **dev** build (`./gradlew installDevDebug`).
2. **On the emulator,** nothing to set: the dev build calls `http://10.0.2.2:8080/`, which is your computer as seen from the emulator.
3. **On a physical phone:**
   1. Connect the phone to the same Wi-Fi as the computer.
   2. Find the computer's local IP (`ipconfig` on Windows, `ipconfig getifaddr en0` on macOS, `hostname -I` on Linux).
   3. In the app's `local.properties`, set `api.devUrl=http://<that IP>:8080/`, then build again.
   4. Allow Java through the firewall for private networks when Windows asks.
4. Sign in with the account from [7.9](#79-create-your-first-admin-and-try-it). The admin buttons appear because the role is read at sign-in. If you promoted the account while already signed in to the app, sign out and in again.

#### 7.11 Other profiles

`uat` and `prod` use Supabase cloud projects over SSL and have no database defaults, so they refuse to start without their `.env.uat` / `.env.prod`:

```bash
SPRING_PROFILES_ACTIVE=uat ./mvnw spring-boot:run      # PowerShell: $env:SPRING_PROFILES_ACTIVE="uat"; .\mvnw.cmd spring-boot:run
java -jar target/MedRational-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

| Profile | Database | R2 bucket | Push topic |
|---|---|---|---|
| `dev` (default) | local Supabase in Docker | `dev` | `workshops-dev` |
| `uat` | uat Supabase cloud project (SSL) | `R2_BUCKET`, default `uat` | `workshops-uat` |
| `prod` | prod Supabase cloud project (SSL) | `R2_BUCKET` (required) | `workshops` |

#### 7.12 Run with Docker

The `Dockerfile` builds one image for every environment, with LibreOffice included. Every merge to `main` that passes CI also publishes it as `ghcr.io/yorkchk/medrational:sha-<commit>` and `:latest`, so you can pull it instead of building. The profile, secrets and Firebase key are passed when the container starts:

```bash
docker build -t medrational .
docker run --rm -p 8080:8080 --env-file .env.uat -e SPRING_PROFILES_ACTIVE=uat \
  -v "$PWD/firebase-service-account.json:/run/secrets/firebase.json:ro" \
  -e FIREBASE_CREDENTIALS=/run/secrets/firebase.json medrational
```

- In PowerShell, use `-v "${PWD}/firebase-service-account.json:/run/secrets/firebase.json:ro"`. With a relative `./` path, Docker mounts an empty directory instead and startup fails with "Couldn't read the Firebase key".
- `--env-file` reads `KEY=VALUE` lines as they are: no quotes and no comments after a value.
- Leave `LIBREOFFICE_HOME` out of env files used with Docker, since a Windows path would replace the image's `/usr/lib/libreoffice`.
- To reach the local database from the container, set `SUPABASE_DB_HOST=host.docker.internal`.
- The container runs in UTC, which is Morocco's time since it moved to permanent GMT on 20 Sep 2026. Workshop dates have no time zone and are compared with the server clock, so it must match the zone admins enter dates in. Override it with `-e TZ=...`.

#### 7.13 Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `Could not resolve placeholder '…'` at startup | A required variable is missing. Check that `.env.dev` is named exactly that, is in the folder you start from, and has a value for that key. |
| `Connection refused` / `Connection to 127.0.0.1:54322 refused` | The database isn't running. `docker ps` (Option A) or `supabase status` (Option B). |
| `Port 8080 was already in use` | Another program uses 8080 (often an older run of the API). Stop it. |
| `release version 21 not supported` / `invalid target release: 21` | Maven runs with an older JDK. Point `JAVA_HOME` to JDK 21 and open a new terminal. |
| Sign-up answers 500 | The email couldn't be sent. Check the `MAIL_*` values; with Gmail use an app password, with SES check that `MAIL_FROM` (and, in the sandbox, the recipient) is verified. |
| Login answers 403 | The account's code was never confirmed. Do step 2 of [7.9](#79-create-your-first-admin-and-try-it). |
| Login answers 401 | Wrong email or password. |
| Admin requests answer 403 | The account isn't `ROLE_ADMIN` yet (step 3 of 7.9). |
| Uploads or downloads fail with an S3 error | Wrong R2 Account ID or keys, or the bucket isn't named `dev` (or `R2_BUCKET`). |
| Office previews answer 503 | LibreOffice isn't installed or found. Install it or set `LIBREOFFICE_HOME`. |
| `RetryTimeoutException` at startup on Windows | A leftover `soffice` process locks LibreOffice's profile folder. End it in Task Manager and start again. |
| The phone can't reach the API | Same Wi-Fi? Right IP in `api.devUrl`? Firewall allowing port 8080? |

### 8. Tests

```bash
./mvnw verify
```

The test suite boots the full Spring context, so it needs a reachable PostgreSQL and the variables from `.env.example` set as **real environment variables** (`.env` files are only loaded by `main`). CI provides both; see `.github/workflows/backend-ci.yml`. A JaCoCo coverage report is written to `target/site/jacoco`. How to write tests is in [CI and tests](docs/CI_AND_TESTS.md).

Load tests (k6, against uat only) are described in [`loadtest/README.md`](loadtest/README.md), with results in [`loadtest/RESULTS.md`](loadtest/RESULTS.md).

### 9. Deployment and operations

![Delivery: pull request, CI, main, Docker image on GHCR, deploy to uat and prod](docs/diagrams/delivery-pipeline.svg)
<sub>Source: [`delivery-pipeline.mmd`](docs/diagrams/delivery-pipeline.mmd)</sub>

- `deploy/setup-server.sh` prepares a fresh Ubuntu server once. The **Deploy** workflow then sends a `sha-` image tag over SSH; the server pulls it, waits for `/actuator/health` and rolls back if it fails. Prod runs the same image that was tested on uat.
- The prod database is backed up every night to a private R2 bucket (30 days kept).
- Details: [Deployment](docs/deployment.md), [uat server](docs/uat-server.md), [Database backups](docs/database-backups.md).

## Contributing

`main` is protected and always releasable. Work happens on short-lived branches (`feat/…`, `fix/…`, `chore/…`, `docs/…`) merged through pull requests once CI passes. Commit messages and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/), and every change ships with tests. User-facing changes are recorded in [CHANGELOG.md](CHANGELOG.md).

## Documentation

- [CI and tests](docs/CI_AND_TESTS.md): what runs on every pull request, how to read failures, and how to add tests.
- [GitHub repository setup](docs/GITHUB_REPO_SETUP.md): the branching, PR, CI and release workflow.
- [Deployment](docs/deployment.md): setting up a server and deploying to uat or prod.
- [uat server](docs/uat-server.md): bringing up the uat environment.
- [Database backups](docs/database-backups.md): nightly backups and how to restore them.
- [Load tests](loadtest/README.md): k6 scripts and results.
- [Diagrams](docs/diagrams/README.md): how to edit and regenerate the README's diagrams.

## License

[MIT](LICENSE)
