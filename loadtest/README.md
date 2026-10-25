# Load test

k6 scenario + generated data. Results are recorded in [`docs/PERFORMANCE.md`](../docs/PERFORMANCE.md).

```bash
# 1. empty database + backend (packaged jar, realistic JVM) with 30,000 generated tasks
createdb flowboard_load
DB_URL=jdbc:postgresql://localhost:5432/flowboard_load SEED_LOAD_TEST=true java -jar backend/target/flowboard-backend-1.0.0.jar

# 2. run (about 3 minutes)
mkdir -p loadtest/results
k6 run --summary-export=loadtest/results/summary.json loadtest/flowboard.k6.js
```

Tunables: `BASE_URL`, `BROWSE_VUS` (default 50), `WRITE_VUS` (default 10), `HOLD` (default `2m`).

Scenarios (run concurrently):

| Scenario | Load | What it does |
|---|---|---|
| `browse` | ramp to 50 VUs, hold 2 min | 30% full board (500 cards), 20% sorted list page, 15% filtered list+search, 10% task detail, 10% dashboard, 5% my tasks, 5% global search, 5% notifications |
| `writers` | 10 VUs, 2 min | read a random task, move it, edit it, comment on it |
| `contention` | 20 VUs x 25 iterations | all VUs update **one** task with the version they read; the teardown verifies the task's version advanced by exactly the number of applied updates |
