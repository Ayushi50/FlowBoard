# Performance measurements

Everything below was measured, not estimated. Raw k6 output: `loadtest/results/k6-output.txt` and `summary.json` (git-ignored, regenerate with `loadtest/README.md`).

## Environment (single machine - read the caveat)

| | |
|---|---|
| Hardware | Apple Silicon Mac (arm64), macOS 27 |
| Backend | `java -jar` of the packaged Spring Boot jar, OpenJDK 27 (compiled for Java 21), default JVM flags, Hikari pool 20 |
| Database | PostgreSQL 16 (Homebrew), default configuration, same machine |
| Load generator | k6 2.3.0, same machine |

**Caveat:** application, database and load generator share one machine, so the generator competes with the system under test and there is no network latency. Treat the numbers as a baseline for relative comparison between commits, not as production capacity. Docker was not used.

## Workload

Dataset (`SEED_LOAD_TEST=true`): 25 users, 20 projects (every user a member), **30,000 tasks** (about 33% To Do, 33% Done, 16% In Progress, 17% Backlog), about 24,000 comments, 36,000 label links, 30,000 activity rows.

Three scenarios run concurrently for about 2.5 minutes (details in `loadtest/README.md`):
- **browse**: ramp to 50 virtual users, 0.2-0.8 s think time. Mix: 30% full board (500 cards), 20% sorted list page, 15% filtered list, 10% task detail, 10% dashboard, 5% My Tasks, 5% search, 5% notifications.
- **writers**: 10 VUs, each iteration reads a task, moves it, edits it, comments.
- **contention**: 20 VUs x 25 iterations all update **one** task using the version they last read.

## Results (second run, after fixing the seed data)

| Metric | Result |
|---|---|
| Requests | 31,324 in about 150 s = **208 req/s** (peak 60 concurrent VUs) |
| All requests latency | p50 2.6 ms, p95 12.4 ms, p99 39.1 ms, max 326 ms |
| Browse scenario latency | p50 3.2 ms, **p95 20.1 ms**, p99 43.0 ms |
| Error rate | **0.00%** failed (browse: 0 of 14,694); 0 unexpected statuses of 30,576 checked responses |
| Data transferred | 2.5 GB received (the 500-card board response is large) |

Server-side per-endpoint timings (Micrometer histogram from `/actuator/prometheus`; percentiles are the upper bound of the histogram bucket, so they are rounded up):

| Endpoint | Calls | avg | p50 | p95 |
|---|---|---|---|---|
| `GET /projects/{id}/tasks` (board 500 / list pages / filters) | 13,789 | 4.5 ms | <= 3.1 | <= 9.8 |
| `POST /tasks/{id}/move` | 3,883 | 2.5 ms | <= 2.4 | <= 3.8 |
| `POST /tasks/{id}/comments` | 3,883 | 1.5 ms | <= 1.4 | <= 2.4 |
| `GET /tasks/{id}` | 1,781 | 2.5 ms | <= 1.4 | <= 7.0 |
| `GET /workspaces/{id}/dashboard` | 1,340 | 12.4 ms | <= 12.6 | <= 15.4 |
| `GET /workspaces/{id}/tasks` (My Tasks) | 681 | 2.9 ms | <= 2.8 | <= 5.6 |
| `GET /workspaces/{id}/search` | 671 | **35.0 ms** | <= 39.1 | <= 50.3 |
| `POST /auth/login` (BCrypt cost 10) | 82 | 105.6 ms | <= 89.5 | <= 246 |

### Concurrent updates to one task

20 VUs, 500 update attempts against one task, each sending the version it read:

- **54 updates applied (HTTP 200), 446 rejected (HTTP 409 `CONCURRENT_MODIFICATION`)**, no other status.
- The task's version moved from 0 to **54**: exactly the number of applied updates, so no accepted edit was lost or applied twice. (The same property is asserted in `ConcurrencyTest`.)
- 409 responses average 1.9 ms; applied updates 6.2 ms.

### Query performance (`EXPLAIN ANALYZE`, 30,000-task database, warm cache)

- Board query for one project (about 1,700 tasks in the project, ordered by `position`, limit 500): **0.5 ms** execution.
- Filtered list (status in TODO/IN_PROGRESS, title contains "login", limit 25): **0.4 ms**, using `ix_tasks_board (project_id, status, position)`.

## Findings and honest notes

- **First run failed a threshold, and the cause was my test data, not the server.** 103 of 30,605 responses (0.33%) were HTTP 400 on task edits: the seeder had generated 1,228 tasks whose due date was before their start date, which the API correctly rejects. The same run's 2,699 move 400s came from the script choosing neighbours in other columns. Both were fixed in the seeder/script; the second run above is the clean one. The 409s on `PUT /tasks/{id}` (about 4,300) during the run are expected: the writers scenario moves a task and then edits it with the stale version.
- **Search is the slowest endpoint (about 35 ms)** at 30,000 tasks. The trigram indexes in the schema are on `title` / `description`, while the query matches `lower(title) LIKE '%term%'`, so I expect the planner not to use them. I did not verify this or add `lower(...)` expression indexes; that is the first optimisation to try.
- The dashboard (about 12 ms) runs several count queries; it was not optimised.
- Not measured: a long soak test, multiple backend instances, network latency, cold-cache behaviour, and anything under Docker.
