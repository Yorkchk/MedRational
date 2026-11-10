# Load test results

One row per run, newest last, so each change is measured against the one before.
p95 is `http_req_duration{kind:api}`. Server CPU and RAM are the peak of `docker stats` during the run.

| Date | Profile | Commit / change | Server | VUs | req/s | p95 (ms) | p99 (ms) | Errors | Peak CPU / RAM | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| 2026-11-10 | smoke | baseline | uat CPX12 (1 vCPU, 2 GB) | 1 | 0.3 | 364 | 455 | 0% | – | Script works. ~300 ms per request is network distance, not the server (DB adds nothing measurable). |
| 2026-11-10 | load | baseline | uat CPX12 (1 vCPU, 2 GB) | 150 | 40 | 246 | 360 | 0.14% | not recorded | Passed. All 53 errors were connection-level, in 3 bursts: 63–70 s (timeouts, cause unconfirmed: client network or a server stall), 905 s (connections aborted locally), 918 s (local DNS failure). Files p95 1.27 s. |
| 2026-11-10 | stress | baseline | uat CPX12 (1 vCPU, 2 GB) | 100→800 | 104 avg | 2750 | 3500 | 0% | not recorded | Failed the time threshold, with no errors: requests queued instead of failing (median 192 ms, p90 2.2 s, max 6 s). No per-stage data; from the stage lengths the slowdown starts around 500–650 VUs (~130–170 req/s). Files p95 3.99 s. |
| 2026-11-10 | stress | resize only | uat CPX22 (2 vCPU, 4 GB) | 100→800 | – | 1460 | 2100 | 0.06% | not recorded | Still fails, but p95 almost halved (2.75 → 1.46 s) and 18,024 iterations vs 16,606 (+8.5%). Doubling the CPU didn't double capacity, so the CPU isn't the only limit. Next suspect: the 5 DB connections (every authenticated request also runs findByEmail in JwtAuthenticationFilter). |
| 2026-11-10 | stress | DB pool 5 → 12 (env override) | uat CPX22 (2 vCPU, 4 GB) | 100→800 | – | 155 | 171 | 0% | not recorded | **Passed.** The DB pool was the bottleneck: p95 1.46 s → 155 ms at 800 VUs. Files p95 1.41 s. Made the default in application-uat/prod.yaml (`DB_POOL_SIZE`, default 12). |
| 2026-11-10 | stress | DB pool 12 | uat CPX12 (1 vCPU, 2 GB) | 100→800 | 104 avg | 2520 | 3230 | 0% | not recorded | Failed, barely better than pool 5 (2.75 s). On 1 vCPU the CPU is the limit; the pool only helps on CPX22. **Prod needs CPX22 + pool 12.** |
| 2026-11-10 | breakpoint (200–700) | DB pool 12 | uat CPX12 (1 vCPU, 2 GB) | 200→600 | 66 / 102 / 135 / 167 per step (count ÷ 50 s) | 155 / 160 / 190 / 217, then 734 at 600 | – | 0% | not recorded | **Max ≈ 500 students at once, ~167 req/s.** Passed 200–500; at 600 the median tripled (150 → 450 ms) and the test stopped. Decision: keep prod on CPX12, alert at 80% CPU (ntfy), resize when real usage nears it. |
