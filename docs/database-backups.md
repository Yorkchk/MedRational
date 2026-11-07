# Database backups

On the Supabase free plan, Supabase's own backups can't be downloaded or restored, so
`.github/workflows/backup-db.yml` makes our own.

| | |
|---|---|
| What | Every table and sequence of the **prod** database's `public` schema (all the app's data) |
| When | Every night at 02:00 UTC (Morocco time), or by hand: Actions → **Backup database** → **Run workflow** |
| Where | Private R2 bucket `medrational-backups`, under `prod/medrational-prod-<date>T<time>Z.dump` |
| Kept | 30 days (an R2 lifecycle rule deletes older dumps) |
| Format | `pg_dump` custom format, compressed, restored with `pg_restore` |
| Alert | GitHub emails you when a scheduled run fails |

**Not backed up:** the files themselves (they're in the R2 buckets, not in the database), the UAT
database, and Supabase's own schemas (`auth`, `storage`…), which the app doesn't use.

The workflow refuses to upload a dump that is empty or has no tables. Its summary page shows the
object name, the size and the number of tables.

## One-time setup

1. **Bucket**: Cloudflare → R2 → Create bucket `medrational-backups`, location hint
   **Western Europe (WEUR)**, jurisdiction **Automatic** (the EU jurisdiction uses another endpoint).
2. **Retention**: the bucket → Settings → Object lifecycle rules → Add rule: prefix `prod/`, delete
   objects 30 days after upload.
3. **Token**: R2 → Manage API tokens → Create: **Object Read & Write**, limited to
   `medrational-backups`. Keep it separate from the app's token, so a leaked app token can't delete
   the backups and the backup token can't touch the app's files.
4. **GitHub** (backend repo → Settings → Secrets and variables → Actions):

   | Name | Kind | Value |
   |---|---|---|
   | `PROD_DATABASE_URL` | secret | Supabase prod → **Connect** → **Session pooler** (port 5432): `postgresql://postgres.<ref>:<password>@aws-0-eu-central-1.pooler.supabase.com:5432/postgres?sslmode=require`. URL-encode the password if it has `& # % / ? @ :` or spaces. |
   | `R2_BACKUP_ACCESS_KEY_ID` | secret | the token's Access Key ID |
   | `R2_BACKUP_SECRET_ACCESS_KEY` | secret | the token's Secret Access Key |
   | `CLOUDFLARE_ACCOUNT_ID` | variable | the account ID (the same as in `.env.prod`) |
   | `R2_BACKUP_BUCKET` | variable | `medrational-backups` |

5. After merging, run the workflow by hand once and do the restore test below.

Scheduled and manual runs only start from the version on `main`, so the workflow can't be tried on
its PR. GitHub turns off scheduled workflows in public repositories after 60 days with no commits;
if that happens, Actions shows a banner to turn it back on.

## Restore

You need Docker Desktop. The commands run `pg_restore` from the `postgres:17` image, so nothing
else needs to be installed. They're written for PowerShell, from the folder that holds the dump.

### 1. Download a dump
Cloudflare → R2 → `medrational-backups` → `prod/` → pick a date → **Download**.

### 2. Test it locally (do this after setup, and now and then)
Restore into a throwaway local Postgres, never into UAT, so real students' data stays off the UAT
project:

```powershell
$dump = "medrational-prod-2026-11-08T0200Z.dump"   # the file you downloaded
docker network create restore-test
docker run -d --rm --name restore-pg --network restore-test -e POSTGRES_PASSWORD=postgres postgres:17
Start-Sleep 5
docker run --rm --network restore-test -v "${PWD}:/in" postgres:17 `
  pg_restore --no-owner --no-privileges --exit-on-error `
  -d postgresql://postgres:postgres@restore-pg:5432/postgres "/in/$dump"
docker exec restore-pg psql -U postgres -c "select count(*) from users;"
```

Compare a few counts (`users`, `study_files`, `categories`) with the Supabase table editor. Then
clean up:

```powershell
docker stop restore-pg; docker network rm restore-test
```

### 3. Restore prod for real
Only after an accident, and preferably with a fresh manual backup taken first (Run workflow):

1. Stop the API, so nothing writes during the restore. On the prod server:
   `cd /opt/medrational && docker compose stop api`
2. From your laptop, replace prod's tables with the dump's (`--clean --if-exists` drops each table
   before recreating it):
   ```powershell
   $prod = "<the PROD_DATABASE_URL value>"
   docker run --rm -v "${PWD}:/in" postgres:17 `
     pg_restore --clean --if-exists --no-owner --no-privileges --exit-on-error `
     -d $prod "/in/$dump"
   ```
3. Start the API again: `docker compose start api`, then check `https://api.swisra.app/actuator/health`.

To restore into a **new** Supabase project instead (if the old one is gone), create it in
eu-central-1, restore without `--clean`, then point `.env.prod` at it: the new project's `SUPABASE_DB_HOST`,
`SUPABASE_PROJECT_REF`, `SUPABASE_DB_USER` and `SUPABASE_DB_PASSWORD`, and the new
`PROD_DATABASE_URL` secret.

Data written between the backup and the restore is lost, and files uploaded after the backup stay
in R2 with no row pointing at them.
