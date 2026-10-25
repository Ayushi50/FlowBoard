package com.flowboard.seed;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Bulk data for the load test (enable with SEED_LOAD_TEST=true). Set-based SQL keeps seeding to a few seconds.
 * <p>
 * Shape: 1 workspace, 25 users (loaduser0..24@flowboard.test, password LoadTest123!), 20 projects with every
 * user as a member, 1,500 tasks per project (30,000 total) with labels, ~2 comments on 40% of tasks and one
 * activity row per task. Status/priority/assignee/due dates are pseudo-random with a fixed seed so runs are comparable.
 */
@Component
@ConditionalOnProperty(name = "flowboard.seed.load-test", havingValue = "true")
public class LoadTestSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LoadTestSeeder.class);
    static final int USERS = 25;
    static final int PROJECTS = 20;
    static final int TASKS_PER_PROJECT = 1500;
    public static final String PASSWORD = "LoadTest123!";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder encoder;

    public LoadTestSeeder(JdbcTemplate jdbc, TransactionTemplate tx, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Integer existing = jdbc.queryForObject("select count(*) from workspaces where name = 'Load Test'", Integer.class);
        if (existing != null && existing > 0) {
            log.info("Load-test data already present, skipping");
            return;
        }
        long start = System.currentTimeMillis();
        tx.executeWithoutResult(s -> seed());
        jdbc.execute("analyze");
        log.info("Load-test data created in {} ms", System.currentTimeMillis() - start);
    }

    private void seed() {
                UUID ws = UUID.randomUUID();
        String hash = encoder.encode(PASSWORD);

        jdbc.update("""
                insert into users (id, email, display_name, avatar_color, password_hash, created_at, updated_at)
                select gen_random_uuid(), 'loaduser' || i || '@flowboard.test', 'Load User ' || i, '#6366f1', ?, now(), now()
                from generate_series(0, ?) i
                """, hash, USERS - 1);
        jdbc.update("insert into workspaces (id, name, created_by, created_at, updated_at) select ?, 'Load Test', id, now(), now() from users where email = 'loaduser0@flowboard.test'", ws);
        jdbc.update("""
                insert into workspace_memberships (id, workspace_id, user_id, role, joined_at)
                select gen_random_uuid(), ?, id,
                       case when email = 'loaduser0@flowboard.test' then 'ADMIN'
                            when substring(email from 9 for position('@' in email) - 9)::int < 5 then 'MANAGER' else 'MEMBER' end,
                       now()
                from users where email like 'loaduser%@flowboard.test'
                """, ws);
        jdbc.update("""
                insert into labels (id, workspace_id, name, color, created_at)
                select gen_random_uuid(), ?, n, c, now() from (values ('bug','#ef4444'),('feature','#6366f1'),('design','#ec4899'),
                  ('backend','#0ea5e9'),('frontend','#10b981'),('docs','#f59e0b')) as l(n, c)
                """, ws);
        jdbc.update("""
                insert into projects (id, workspace_id, project_key, name, description, owner_id, status, archived, start_date, target_date, task_seq, version, created_at, updated_at)
                select gen_random_uuid(), ?, 'P' || lpad(i::text, 2, '0'), 'Load Project ' || i, 'Generated for load testing',
                       (select id from users where email = 'loaduser0@flowboard.test'), 'ACTIVE', false,
                       current_date - 60, current_date + 60, ?, 0, now(), now()
                from generate_series(1, ?) i
                """, ws, TASKS_PER_PROJECT, PROJECTS);
        jdbc.update("""
                insert into project_memberships (id, project_id, user_id, added_at)
                select gen_random_uuid(), p.id, u.id, now() from projects p cross join users u where p.workspace_id = ? and u.email like 'loaduser%@flowboard.test'
                """, ws);

        // Per-row pseudo-randomness comes from hashing (project key, task number, salt): deterministic, so every
        // run produces the same distribution, and evaluated per row (a bare random() would be hoisted by the planner).
        jdbc.update("""
                with base as (
                  select p.id pid, p.project_key k, n,
                         (abs(hashtext(p.project_key || ':' || n || ':s')::bigint) %% 1000) / 1000.0 rs,
                         (abs(hashtext(p.project_key || ':' || n || ':p')::bigint) %% 1000) / 1000.0 rp,
                         (abs(hashtext(p.project_key || ':' || n || ':t')::bigint) %% 1000) / 1000.0 rt,
                         (abs(hashtext(p.project_key || ':' || n || ':a')::bigint) %% 1000) / 1000.0 ra,
                         (abs(hashtext(p.project_key || ':' || n || ':b')::bigint) %% 1000) / 1000.0 rb,
                         (abs(hashtext(p.project_key || ':' || n || ':d')::bigint) %% 1000) / 1000.0 rd,
                         (abs(hashtext(p.project_key || ':' || n || ':e')::bigint) %% 1000) / 1000.0 re,
                         (abs(hashtext(p.project_key || ':' || n || ':f')::bigint) %% 1000) / 1000.0 rf
                  from projects p cross join generate_series(1, %d) n where p.workspace_id = ?
                ), typed as (
                  select base.*, (array['BACKLOG','TODO','TODO','IN_PROGRESS','DONE','DONE'])[1 + floor(rs * 6)::int] status from base
                )
                insert into tasks (id, project_id, task_number, title, description, status, priority, task_type, assignee_id, reporter_id,
                                   start_date, due_date, position, version, completed_at, created_at, updated_at)
                select gen_random_uuid(), pid, n,
                       (array['Fix','Implement','Design','Review','Refactor','Document','Investigate','Polish'])[1 + (n %% 8)] || ' ' ||
                       (array['login flow','search index','billing page','email templates','cache layer','onboarding','export job','permissions'])[1 + ((n / 8) %% 8)] || ' #' || n,
                       'Generated task ' || n || ' for load testing. Includes enough text to be a realistic description payload.',
                       status,
                       (array['URGENT','HIGH','MEDIUM','MEDIUM','LOW','NONE'])[1 + floor(rp * 6)::int],
                       (array['TASK','TASK','BUG','FEATURE','IMPROVEMENT'])[1 + floor(rt * 5)::int],
                       case when ra < 0.85 then (select id from users u where u.email = 'loaduser' || floor(rb * %d)::int || '@flowboard.test') end,
                       (select id from users where email = 'loaduser0@flowboard.test'),
                       -- always earlier than the earliest possible due date (today - 20), so start <= due
                       case when rd < 0.5 then current_date - 25 - floor(rd * 30)::int end,
                       case when re < 0.7 then current_date + (floor(rf * 60) - 20)::int end,
                       n * 1024.0, 0,
                       case when status = 'DONE' then now() - (rf * interval '14 days') end,
                       now() - (rd * interval '60 days'), now()
                from typed
                """.formatted(TASKS_PER_PROJECT, USERS), ws);
        jdbc.update("""
                insert into task_labels (task_id, label_id)
                select t.id, l.id
                from tasks t
                join projects p on p.id = t.project_id
                join labels l on l.workspace_id = p.workspace_id
                where p.workspace_id = ? and abs(hashtext(t.id::text || l.name)::bigint) % 5 = 0
                """, ws);
        jdbc.update("""
                insert into comments (id, task_id, author_id, body, created_at, updated_at)
                select gen_random_uuid(), t.id, t.reporter_id, 'Comment ' || c || ' on ' || t.task_number, now(), now()
                from tasks t join projects p on p.id = t.project_id cross join generate_series(1, 2) c
                where p.workspace_id = ? and abs(hashtext(t.id::text || c)::bigint) % 5 < 2
                """, ws);
        jdbc.update("""
                insert into activity_logs (id, workspace_id, project_id, task_id, actor_id, action, summary, created_at)
                select gen_random_uuid(), ?, t.project_id, t.id, t.reporter_id, 'TASK_CREATED', 'created task ' || t.task_number, t.created_at
                from tasks t join projects p on p.id = t.project_id where p.workspace_id = ?
                """, ws, ws);
    }
}
