# uat server from a snapshot

uat has no permanent server. To test a feature, create a server from a snapshot, deploy the
image to test, and delete the server when done. Hetzner bills by the hour and keeps billing a
server that is only powered off, so **deleting** it is what stops the cost.

Private notes (git-ignored). Prod: `deploy@188.245.30.216`, `https://api.swisra.app`.

## One-time setup

### 1. `base-docker` snapshot
A clean copy of the prod server's setup, with no secrets: hardened SSH (key only, no root),
the `deploy` user, `ufw`, `fail2ban`, automatic updates, Docker, swap and `/opt/medrational`.

- Hetzner console → the server → **Snapshots** → **Create snapshot** → name it `base-docker`.
- Take it from a server with **no** `.env.*` or Firebase key. If you take it from prod later,
  delete `/opt/medrational/*` on a copy first, never ship prod's secrets to uat.
- Costs about €0.01 per GB per month.

### 2. `uat` firewall
Hetzner → **Firewalls** → **Create firewall** `uat`, inbound:

| Protocol | Port | Why |
|---|---|---|
| TCP | 22 | SSH |
| ICMP | | ping |
| TCP | 8080 | the app calls uat directly at `http://<ip>:8080` (no domain) |

Plain HTTP is fine for short tests with test accounts. Never use real passwords on uat.

## Each test

### 1. Create the server
Hetzner → **Snapshots** → `base-docker` (or `uat-ready`) → **Create server**:
- location: same as prod
- size: any (bigger for heavy tests)
- firewall: `uat`
- SSH key: your key

Note the new IP. If SSH warns that the host key changed (an IP you used before), run
`ssh-keygen -R <uat-ip>`.

### 2. Copy uat's config
From the backend folder on the laptop:
```powershell
scp .env.uat firebase-service-account.json deploy@<uat-ip>:/opt/medrational/
```
On the uat server:
```bash
cd /opt/medrational
sudo chown deploy:deploy .env.uat && sudo chmod 600 .env.uat     # read by docker compose
sudo chown 1001:1001 firebase-service-account.json && sudo chmod 400 firebase-service-account.json   # read by the app
```
If the GHCR package is private:
```bash
echo <PAT with read:packages> | docker login ghcr.io -u Yorkchk --password-stdin
```

### 3. `compose.yml`
`/opt/medrational/compose.yml`:
```yaml
services:
  uat:
    image: ghcr.io/yorkchk/medrational:${UAT_TAG:-latest}
    env_file: .env.uat
    environment:
      SPRING_PROFILES_ACTIVE: uat
      FIREBASE_CREDENTIALS: /run/secrets/firebase.json
    volumes:
      - ./firebase-service-account.json:/run/secrets/firebase.json:ro
    ports: ["8080:8080"]   # public on purpose: the uat firewall allows 8080
    restart: unless-stopped
```

### 4. Start the version to test
Image tags are on the **Publish image** run summaries in GitHub Actions, or on the package page.
```bash
cd /opt/medrational
echo "UAT_TAG=sha-<commit>" > .env
docker compose pull && docker compose up -d
docker compose logs -f uat     # wait for "Started MedRationalApplication"
```
Check from the laptop: `curl http://<uat-ip>:8080/api/v1/categories`.

### 5. Point the app at uat
In the Android project's `local.properties`:
```properties
api.baseUrl=http://<uat-ip>:8080/
```
Then `.\gradlew.bat installDebug`. Put the prod URL back afterwards.

### 6. Test, then delete the server
Hetzner → the uat server → **Delete**. Billing stops at deletion.

## Faster next time: `uat-ready` snapshot
After steps 1–3 once, snapshot that server as `uat-ready`. Next tests then only need: create
from `uat-ready` → step 4 → step 5 → test → delete.

- `uat-ready` contains `.env.uat` and the Firebase key: delete and recreate it if those
  secrets change.
- A server made from a snapshot keeps the snapshot's SSH host key, so SSH may warn when the IP
  is reused (`ssh-keygen -R <uat-ip>`).

## Reminders
- Never copy `.env.prod` to a uat server, or `.env.uat` to prod.
- uat pushes go to the `workshops-uat` topic, which the app doesn't listen to, so testers don't
  get workshop notifications (and real users never get uat's).
- Workshop times follow the server clock: the image runs in UTC (Morocco time). Override with
  `TZ` in `compose.yml` only if you know why.
