# Load tests

[k6](https://k6.io) scripts that play many students using the app at once, to find out how many the
API handles before it slows down or fails, and what gives out first.

`browse.js` repeats a student's session: home (categories, novelties badge), a category, a reasoning,
a file (rating, favorite status, hashtags, view record), and sometimes favorites, a search, a preview
or a download, with a few seconds of reading between screens. It never uploads or creates anything,
so no push reaches anyone and nothing is written to R2.

**Only run it against uat**, never prod: it records views and downloads, which would skew the real
statistics, and an overloaded prod is an outage. The script refuses `api.swisra.app`.

## 1. Before the first run

1. **The uat server must look like prod**: same Hetzner type and same Supabase plan, or the numbers
   say nothing about prod. Bring it up as in [docs/uat-server.md](../docs/uat-server.md).
2. **Content to browse**: a few categories, reasonings and files on uat (any PDFs, ideally some of the
   real sizes students download).
3. **Test accounts**: 5 to 10 confirmed **student** (non-admin) accounts on uat, all with the same
   password. Sign them up in the uat app with plus-addresses of an inbox you own
   (`you+lt1@gmail.com`, `you+lt2@gmail.com`, …) so the codes arrive. Each virtual user sticks to one
   account, which is fine: the API doesn't care how many phones share an account.
4. **Install k6**: `winget install k6 --source winget`, or use Docker (below) without installing.
5. **Optional, to see the DB pool**: add `LOGGING_LEVEL_COM_ZAXXER_HIKARI=DEBUG` to the uat server's
   `.env.uat` and recreate the container (`docker compose up -d --force-recreate api`). Hikari then
   logs `total=5, active=5, idle=0, waiting=37` every 30 s. Remove it after testing.

## 2. Running

```powershell
k6 run -e BASE_URL=https://uat.swisra.app `
       -e LT_USERS=you+lt1@gmail.com,you+lt2@gmail.com,you+lt3@gmail.com `
       -e LT_PASSWORD='...' `
       -e PROFILE=smoke loadtest/browse.js
```

With Docker instead of an installed k6 (from the repository root):

```powershell
docker run --rm -v "${PWD}/loadtest:/scripts" grafana/k6:1.0.0 run `
  -e BASE_URL=https://uat.swisra.app -e LT_USERS=... -e LT_PASSWORD='...' -e PROFILE=smoke /scripts/browse.js
```

| `PROFILE` | Students (VUs) | Length | Question it answers |
|---|---|---|---|
| `smoke` | 1 | 1 min | Does the script work? Run it first. |
| `load` | ramp to 150, hold 10 min | 15 min | Does an exam night (~10% of 7,544 students online) stay fast? |
| `stress` | 100 → 250 → 500 → 800 | 22 min | Where is the ceiling, and what gives out first? |
| `spike` | 0 → 500 in 30 s | 3 min | Does a sudden rush (a push to everyone) recover on its own? |
| `soak` | 100 for 30 min | 30 min | Does anything leak (memory, DB connections) over time? |
| `breakpoint` | 400 → 800 → … → 3,000, 1 min each | ≤ 11 min | What is the maximum? Stops by itself at the first step that's too slow. |

Other knobs: `-e DOWNLOAD_RATE=0.3` (share of sessions that download a file, default 0.03) and
`-e PREVIEW_RATE=0.1` (default 0.02), to test a night when everyone downloads the same exam pack.
Keep every run under 1 hour: the access tokens taken at the start expire then.

Order: `smoke`, then `load`. Run `stress` only once `load` passes. Then `spike` and `soak`, and
`breakpoint` to find the maximum.

### Finding the maximum (`breakpoint`)

It adds students in steps (400, 800, 1,200, 1,600, 2,000, 2,400, 3,000), holds each step for
1 minute and judges it **only on that step's requests**, leaving out its first 10 seconds: p95 under 500 ms and under 1% errors.
The first step that fails stops the test (k6 prints `thresholds ... have been crossed` and exits
non-zero, which is expected here). The summary lists, for every step reached:

- `http_req_duration{kind:api,step:N}`: its p95,
- `http_req_failed{step:N}`: its error rate,
- `http_reqs{step:N}`: its request count; divide by 50 (the measured seconds) for requests/s.

**The maximum is the last step with ✓**, and the step with ✗ is where it breaks. To narrow it
down, run again with your own steps, e.g. `-e STEPS=1600,1800,2000,2200`.

Above about 1,500 students, watch your own PC too (Task Manager): if k6 uses most of its CPU, the
laptop is the limit, not the API, and the run should move to a temporary Hetzner server.

### Where to run k6 from

- **Browsing profiles**: your laptop is fine. Its bandwidth isn't what's tested.
- **Download-heavy runs** (a high `DOWNLOAD_RATE`): from a temporary Hetzner Cloud server in the
  same location (the smallest type, a few cents for an hour, deleted after), or your home
  connection becomes the bottleneck instead of the API.
- **Never from the API server itself**: k6 would compete with the API for its single CPU.

## 3. What to watch during a run

On the uat server (`ssh deploy@uat.swisra.app`, in the medrational directory):

```bash
docker stats                                     # CPU % and memory of api and caddy
uptime                                           # load average; above the vCPU count = CPU-bound
free -m                                          # swap in use = out of memory
docker compose logs -f api | grep -iE 'hikari|timeout|exception|outofmemory'
```

In the Supabase dashboard of the uat project: **Reports → Database** (CPU, connections) and
**Query performance** (the slowest queries during the run).

## 4. Reading the results

k6 prints a summary at the end. The lines that matter:

- `http_req_duration{kind:api}`: p95 under 500 ms is the target for browsing.
- `http_req_duration{kind:file}`: downloads and previews, tracked apart since size drives them.
- `http_req_failed`: under 1%. Look at which `name` failed in the checks above it.
- `http_reqs`: the throughput in requests/s. At the `stress` step where the thresholds fail,
  this is the ceiling of one server.

A failed threshold makes k6 exit non-zero and print ✗ next to it.

| What you see | Most likely cause | Fix (in this order) |
|---|---|---|
| `api` CPU near 100% (× vCPUs), load average above the vCPU count | The 1 vCPU server | Resize in Hetzner (CPX22/CPX32): a few minutes of downtime, no code change |
| Memory near the limit, swap used, or the container restarting | 2 GB for the JVM and LibreOffice | Same: a bigger server |
| Hikari `waiting=` above 0, `Connection is not available, request timed out` errors, CPU low | The pool of 5 DB connections | Raise `maximum-pool-size` within the Supabase pooler limit, or use the transaction pooler (port 6543, `prepareThreshold=0`) |
| Only `download`/`preview` slow, browsing fine | Files stream through the API | Virtual threads (`spring.threads.virtual.enabled`), a bigger server; R2 redirects later |
| One `name` (e.g. `search`) slow, the others fine, CPU low | A slow query | Find it in Supabase Query performance, add an index |
| 429 errors | The rate limits | Only `login` is limited, and the script signs in once: check `LT_USERS` |
| 401 errors after an hour | Tokens expired | Keep runs under 1 hour |

After each fix, run `load` again and write the numbers in [RESULTS.md](RESULTS.md), so every
change is measured against the one before.

## 5. When one server isn't enough

Only when a bigger server still can't hold the load, or when downtime during deploys and server
failures matters (e.g. more schools), run several API instances behind a load balancer. The API
needs two changes first: a lock on the scheduled jobs (ShedLock), so workshop pushes aren't sent
once per instance, and rate limits stored in the database instead of each instance's memory.
