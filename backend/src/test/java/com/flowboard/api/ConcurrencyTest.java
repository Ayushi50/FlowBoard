package com.flowboard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.support.AbstractIntegrationTest;
import com.flowboard.support.Api.Result;
import com.flowboard.support.Api.Session;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class ConcurrencyTest extends AbstractIntegrationTest {

    private <T> List<T> runTogether(int threads, java.util.function.IntFunction<Callable<T>> job) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<T> task = job.apply(i);
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return task.call();
            }));
        }
        ready.await();
        go.countDown();
        List<T> results = new ArrayList<>();
        for (Future<T> f : futures) {
            results.add(f.get());
        }
        pool.shutdown();
        return results;
    }

    @Test
    void ofManyConcurrentEditsWithTheSameVersionExactlyOneWins() throws Exception {
        Session admin = api.register("Admin", "Race Co");
        UUID ws = api.firstWorkspace(admin);
        UUID project = api.createProject(admin, ws, "RACE").id();
        UUID task = api.createTask(admin, project, "Contended", Map.of()).id();

        List<Result> results = runTogether(8, i -> () -> api.put(admin, "/api/v1/tasks/" + task,
                Map.of("title", "writer-" + i, "priority", "HIGH", "type", "TASK", "version", 0)));

        long ok = results.stream().filter(r -> r.status() == 200).count();
        long conflicts = results.stream().filter(r -> r.status() == 409).count();
        assertThat(ok).isEqualTo(1);
        assertThat(conflicts).isEqualTo(7);
        assertThat(results.stream().filter(r -> r.status() == 409).map(r -> r.text("code"))).containsOnly("CONCURRENT_MODIFICATION");

        // the stored title is exactly the winner's, never a blend or a loser's
        String winner = results.stream().filter(r -> r.status() == 200).findFirst().orElseThrow().text("title");
        assertThat(api.get(admin, "/api/v1/tasks/" + task).body().path("task").path("title").asText()).isEqualTo(winner);
        assertThat(api.get(admin, "/api/v1/tasks/" + task).body().path("task").path("version").asLong()).isEqualTo(1);
    }

    @Test
    void concurrentMovesOfOneTaskFromTheSameVersionHaveASingleWinner() throws Exception {
        Session admin = api.register("Admin", "Race Co");
        UUID project = api.createProject(admin, api.firstWorkspace(admin), "MOVE").id();
        UUID task = api.createTask(admin, project, "Drag me", Map.of()).id();
        String[] targets = {"BACKLOG", "IN_PROGRESS", "DONE", "IN_PROGRESS", "DONE", "BACKLOG"};

        List<Result> results = runTogether(targets.length, i -> () -> {
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("status", targets[i]);
            body.put("version", 0);
            return api.post(admin, "/api/v1/tasks/" + task + "/move", body);
        });

        assertThat(results.stream().filter(r -> r.status() == 200).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 409).count()).isEqualTo(targets.length - 1);
        String winnerStatus = results.stream().filter(r -> r.status() == 200).findFirst().orElseThrow().text("status");
        assertThat(api.get(admin, "/api/v1/tasks/" + task).body().path("task").path("status").asText()).isEqualTo(winnerStatus);
        // exactly one STATUS_CHANGED entry (or none if the winner kept TODO): history never records the losers
        long history = api.get(admin, "/api/v1/tasks/" + task + "/activity").body().path("items").findValuesAsText("action")
                .stream().filter("STATUS_CHANGED"::equals).count();
        assertThat(history).isEqualTo("TODO".equals(winnerStatus) ? 0 : 1);
    }

    @Test
    void concurrentTaskCreationNeverProducesDuplicateIdentifiers() throws Exception {
        Session admin = api.register("Admin", "Race Co");
        UUID project = api.createProject(admin, api.firstWorkspace(admin), "SEQ").id();

        List<Result> results = runTogether(12, i -> () -> api.createTask(admin, project, "Task " + i, Map.of()));

        assertThat(results).allMatch(r -> r.status() == 201);
        Set<String> ids = new HashSet<>();
        results.forEach(r -> ids.add(r.text("identifier")));
        assertThat(ids).hasSize(12);
        assertThat(ids).contains("SEQ-1", "SEQ-12");
    }
}
