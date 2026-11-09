# Deployment

Every merge to `main` that passes CI publishes `ghcr.io/yorkchk/medrational:sha-<commit>`
([`publish-image.yml`](../.github/workflows/publish-image.yml)). A server runs that image with the
files in [`deploy/`](../deploy): `compose.yml` (the API behind Caddy), `Caddyfile` (HTTPS) and a
small `.env` (`APP_ENV`, `DOMAIN`, `IMAGE_TAG`). The secrets stay on the server: `.env.<APP_ENV>`
and `firebase-service-account.json`.

| Script | Where it runs | What it does |
|---|---|---|
| [`deploy/setup-server.sh`](../deploy/setup-server.sh) | once, as root, on a fresh Ubuntu 24.04 server | the `deploy` user, Docker, swap, ufw, fail2ban, automatic updates, SSH hardening, and installs the next script |
| [`deploy/deploy.sh`](../deploy/deploy.sh) | on the server, as `medrational-deploy` | puts one image tag live: new `compose.yml`/`Caddyfile`, pull, restart, wait until healthy, roll back if it isn't |
| [`deploy.yml`](../.github/workflows/deploy.yml) | GitHub Actions, **Deploy** button | sends a tag and that commit's `compose.yml`/`Caddyfile` to `medrational-deploy` over SSH |

The uat server only exists while testing: see [uat-server.md](uat-server.md) for the Hetzner side
(snapshots, Primary IP, firewall).

## Once: the GitHub deploy key

One key pair per server, so a uat key can never reach prod. On the laptop:
```powershell
ssh-keygen -t ed25519 -N '""' -C github-deploy-prod -f github-deploy-prod
ssh-keygen -t ed25519 -N '""' -C github-deploy-uat  -f github-deploy-uat
```
Keep the private keys out of the repo (they go into GitHub secrets and can then be deleted from
the laptop). On the server, the public key is limited by `setup-server.sh` to
`restrict,command="/usr/local/bin/medrational-deploy"`: whatever the client sends, it can only run
the deploy script, which accepts nothing but `sha-<40 hex>`.

## Setting up a server

1. **Create it** in the Hetzner console: Ubuntu 24.04, your SSH key, the firewall (22, 80, 443
   TCP and 443 UDP), and for uat the Primary IP ([uat-server.md](uat-server.md)). Point the
   domain's `A` record at it (DNS only, no Cloudflare proxy).
2. **Harden it**, from the backend folder:
   ```powershell
   scp deploy/setup-server.sh deploy/deploy.sh root@<ip>:/root/
   ssh root@<ip> "bash /root/setup-server.sh --ci-key '$(Get-Content github-deploy-prod.pub)'"
   ```
   (`github-deploy-uat.pub` for uat.) Root's SSH login is off afterwards; use `deploy@<ip>`.
   Running it again is safe: it's how to add or change the CI key later.
3. **Copy the config and secrets** (never `.env.prod` to uat, or `.env.uat` to prod). Set
   `deploy/.env` from `deploy/.env.example` first; its `IMAGE_TAG` is replaced at each deploy.
   ```powershell
   scp deploy/.env .env.prod firebase-service-account.json deploy@<ip>:/opt/medrational/
   ```
   On the server:
   ```bash
   cd /opt/medrational
   chmod 600 .env.*
   sudo chown 1001:1001 firebase-service-account.json && sudo chmod 400 firebase-service-account.json
   echo <PAT with read:packages> | docker login ghcr.io -u Yorkchk --password-stdin
   ```
4. **GitHub environment** (backend repo → Settings → Environments → `prod` or `uat`):

   | Kind | Name | Value |
   |---|---|---|
   | Secret | `DEPLOY_SSH_KEY` | the private key file's content (`github-deploy-prod`) |
   | Secret | `DEPLOY_KNOWN_HOSTS` | the output of `ssh-keyscan api.swisra.app` (required for prod; for uat, see below) |
   | Variable | `DEPLOY_HOST` | `api.swisra.app` or `uat.swisra.app` |

   For uat, leave `DEPLOY_KNOWN_HOSTS` empty if you build the server without a snapshot each time
   (its host key changes): the workflow then trusts the key it finds, with a warning. A server
   made from a snapshot keeps the snapshot's host key, so it can be pinned.
5. **Deploy** (next section). The first deploy also sends `compose.yml` and `Caddyfile`.
6. Optional: snapshot the server now (Hetzner → Snapshots) as `base-docker`, before any secret
   is on it, so the next uat server starts already hardened.

## Deploying

GitHub → backend repo → **Actions** → **Deploy** → **Run workflow**:
- **Server**: `uat` or `prod` (prod only from `main`).
- **Image tag**: empty takes the latest commit on `main`; otherwise `sha-<full commit>` from a
  green **Publish image** run. Test a tag on uat, then deploy the same tag to prod.

The run checks the image exists, checks out that commit's `deploy/` files, and runs
`medrational-deploy` on the server, which:
1. waits if another deploy is running,
2. writes the new `compose.yml`/`Caddyfile` (reloading Caddy if it changed) and `IMAGE_TAG`,
3. `docker compose pull` and `up -d`,
4. waits up to 4 minutes for the API's healthcheck, then checks
   `https://<domain>/actuator/health/liveness` through Caddy,
5. on success, deletes images unused for a week (so the disk doesn't fill up); on failure, prints
   the API's last logs, puts the previous files and tag back, restarts them, and fails the run.

**Rolling back** is running Deploy again with the previous tag (shown at the start of each run's
log: "replacing sha-…").

**Automatic uat deploys**: set the repository variable `UAT_AUTO_DEPLOY` to `true` (Settings →
Secrets and variables → Actions → Variables). Each new image then goes to uat when the uat server
is up; when it isn't (deleted between tests), the run ends with a notice instead of failing.
Prod is never deployed automatically.

**By hand**, as `deploy` on the server (keeps the current `compose.yml` and `Caddyfile`):
```bash
medrational-deploy sha-<full commit>
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| "doesn't answer on port 22" | The server is off or deleted, or the Hetzner firewall blocks SSH. |
| `Host key verification failed` | The server was rebuilt: update `DEPLOY_KNOWN_HOSTS` with `ssh-keyscan <domain>`. |
| `Permission denied (publickey)` | The CI key isn't on the server: rerun `setup-server.sh --ci-key …`. |
| "couldn't pull the image" | `docker login ghcr.io` as `deploy` (step 3), or the PAT expired. |
| "didn't become healthy" | Read the API logs printed in the run. A paused Supabase project (uat) doesn't fail the deploy, since only liveness is checked, but `/actuator/health` answers 503. |
| "isn't an image tag" | Use `sha-` plus the **full** 40-character commit hash, never `latest`. |
