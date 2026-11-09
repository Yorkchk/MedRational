# uat server from a snapshot

uat has no permanent server. To test a feature, create a server from a snapshot, deploy the
image to test, and delete the server when done. Hetzner bills by the hour and keeps billing a
server that is only powered off, so **deleting** it is what stops the cost.

Private notes (git-ignored). Prod: `deploy@188.245.30.216`, `https://api.swisra.app`.
uat: `https://uat.swisra.app`, behind Caddy, on a Hetzner Primary IP.

The server runs the files in the backend repo's `deploy/` folder: `compose.yml` (the API, with a
healthcheck and log rotation, behind Caddy), `Caddyfile` (HTTPS) and a small `.env`
(`APP_ENV`, `DOMAIN`, `IMAGE_TAG`, see `deploy/.env.example`). Prod runs the same files, with
`APP_ENV=prod` and `DOMAIN=api.swisra.app`.

## One-time setup

### 1. `base-docker` snapshot
A clean copy of the prod server's setup, with no secrets: hardened SSH (key only, no root),
the `deploy` user, `ufw`, `fail2ban`, automatic updates, Docker, swap and `/opt/medrational`.
To make it from scratch: create a plain Ubuntu 24.04 server, run `deploy/setup-server.sh` with
the uat CI key ([deployment.md](deployment.md#setting-up-a-server), steps 1–2), then snapshot it.

- Hetzner console → the server → **Snapshots** → **Create snapshot** → name it `base-docker`.
- Take it from a server with **no** `.env.*` or Firebase key. If you take it from prod later,
  delete `/opt/medrational/*` on a copy first, never ship prod's secrets to uat.
- Costs about €0.01 per GB per month.

### 2. `uat` firewall
Hetzner → **Firewalls** → `uat`, inbound:

| Protocol | Port | Why |
|---|---|---|
| TCP | 22 | SSH |
| ICMP | | ping |
| TCP | 80 | Let's Encrypt certificate challenge, and the redirect to HTTPS |
| TCP | 443 | HTTPS (Caddy) |
| UDP | 443 | HTTP/3 (optional) |

No 8080: the API is only reachable through Caddy (`expose`, not `ports`, in `compose.yml`), which
also lets the rate limits trust the client IP Caddy forwards.

### 3. Primary IP and DNS
- Hetzner → **Primary IPs** → create an IPv4 in the same location as prod. It survives server
  deletion (about €0.50 a month), so the DNS never changes.
- DNS for `swisra.app`: `A` record `uat` → that IP, **DNS only** (grey cloud on Cloudflare), so
  Caddy can get its certificate.

## Each test

### 0. Before you start
- **Supabase uat**: free projects pause after about a week without use. Open the uat project and
  click **Restore** if it's paused, otherwise `/actuator/health` answers 503.
- **Image tag**: the last green **Publish image** run in GitHub Actions (backend repo) shows
  `sha-<full commit>`, normally the latest commit on `main` (`git log -1 --format=%H origin/main`).

### 1. Create the server
Hetzner → **Snapshots** → `uat-ready` (or `base-docker`) → **Create server**:
- location: the Primary IP's location
- networking: untick the automatic public IPv4 and pick the **Primary IP**
- size: any (bigger for heavy tests)
- firewall: `uat`
- SSH key: your key

On the laptop, since the snapshot's host key or the reused IP makes SSH warn:
```powershell
ssh-keygen -R uat.swisra.app
```

### 2. Copy the secrets (not needed from `uat-ready`)
From the backend folder, with `deploy/.env` set to `APP_ENV=uat` and `DOMAIN=uat.swisra.app`
(`IMAGE_TAG` is set by each deploy):
```powershell
scp deploy/.env .env.uat firebase-service-account.json deploy@uat.swisra.app:/opt/medrational/
```
Then on the server (`ssh deploy@uat.swisra.app`), step 3 of
[deployment.md](deployment.md#setting-up-a-server): `chmod 600 .env.uat`, the Firebase key's
owner, and `docker login ghcr.io` once.

### 3. Deploy
GitHub → backend repo → **Actions** → **Deploy** → **Run workflow** → `uat`, tag empty for the
latest `main` (or a `sha-…`). It sends `compose.yml` and `Caddyfile`, pulls, restarts, and waits
until the API is healthy ([deployment.md](deployment.md#deploying)). With `UAT_AUTO_DEPLOY=true`,
each new image also reaches uat on its own while the server is up.

<details>
<summary>By hand, without the workflow</summary>

```bash
ssh deploy@uat.swisra.app
cd /opt/medrational
rm -f google-services.json                      # old key name from the first setup, if still there
chmod 600 .env.uat
sudo chown 1001:1001 firebase-service-account.json && sudo chmod 400 firebase-service-account.json   # read by the app user
docker compose config --quiet && echo OK
docker compose pull
docker compose up -d --remove-orphans --force-recreate
docker compose ps                               # api: healthy after about 1–2 min
docker compose logs -f api                      # Ctrl+C after "Started MedRationalApplication"
```
- `pull` says "denied": the GHCR package is private, log in once:
  `echo <PAT with read:packages> | docker login ghcr.io -u Yorkchk --password-stdin`
- `--remove-orphans` removes containers of services that no longer exist (the old `uat` one).
- `--force-recreate` applies `compose.yml` changes such as log rotation; logs are capped at
  5 × 10 MB per container.

</details>

### 4. Check
From the laptop:
```powershell
curl.exe https://uat.swisra.app/actuator/health      # {"status":"UP"}
```
The first call after a new certificate can take a few seconds. A certificate error usually means
the DNS doesn't point at this server yet.

### 5. Point the app at uat
- Testers: install the APK from the latest `uat-<n>` pre-release on GitHub (Android repo). CI
  builds it with the `API_UAT_URL` variable (`https://uat.swisra.app/`).
- Locally: `api.uatUrl=https://uat.swisra.app/` in the Android project's `local.properties`,
  then `.\gradlew.bat installUatRelease` (needs the `signing.*` values) or `installUatDebug`.

### 6. Test, then delete the server
Hetzner → the uat server → **Delete**. Billing stops at deletion. The Primary IP stays.

## Faster next time: `uat-ready` snapshot
After a full setup, snapshot the server as `uat-ready` and delete the older one. Next tests then
only need: create from `uat-ready` → **Deploy** workflow → test → delete.

- `uat-ready` contains `.env.uat`, the Firebase key and Caddy's certificate (the `caddy_data`
  volume, so new servers don't ask Let's Encrypt again: it allows 5 certificates per week per
  domain). Recreate it when those secrets or the `deploy/` files change.
- A server made from a snapshot keeps the snapshot's SSH host key, so SSH may warn
  (`ssh-keygen -R uat.swisra.app`).

## Reminders
- Never copy `.env.prod` to a uat server, or `.env.uat` to prod.
- uat pushes go to the `workshops-uat` topic, which only the UAT app listens to, so real users
  never get uat's.
- Workshop times follow the server clock: the image runs in UTC (Morocco time). Override with
  `TZ` in `compose.yml` only if you know why.
- Health: `/actuator/health` includes the database (503 when Supabase is down or paused);
  Docker's healthcheck only uses `/actuator/health/liveness`.
