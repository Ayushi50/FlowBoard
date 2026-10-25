package com.flowboard.task;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Translates a {@link TaskFilter} into a single SQL statement (filtering, ordering, paging all in the database). */
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<Task> from(TaskFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(root.get("project").get("id").in(f.projectIds()));
            if (!f.includeSubtasks()) {
                p.add(cb.isNull(root.get("parent")));
            }
            if (f.assigneeId() != null) {
                p.add(cb.equal(root.get("assignee").get("id"), f.assigneeId()));
            }
            if (f.statuses() != null && !f.statuses().isEmpty()) {
                p.add(root.get("status").in(f.statuses()));
            }
            if (f.priorities() != null && !f.priorities().isEmpty()) {
                p.add(root.get("priority").in(f.priorities()));
            }
            if (f.labelIds() != null && !f.labelIds().isEmpty()) {
                Subquery<UUID> sq = query.subquery(UUID.class);
                Root<Task> t2 = sq.from(Task.class);
                sq.select(t2.get("id")).where(cb.equal(t2.get("id"), root.get("id")),
                        t2.join("labels").get("id").in(f.labelIds()));
                p.add(cb.exists(sq));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + escape(f.q().trim().toLowerCase(Locale.ROOT)) + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like, '\\'),
                        cb.like(cb.lower(cb.coalesce(root.<String>get("description"), "")), like, '\\')));
            }
            if (f.dueFrom() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("dueDate"), f.dueFrom()));
            }
            if (f.dueTo() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("dueDate"), f.dueTo()));
            }
            if (f.overdueOnly()) {
                p.add(cb.lessThan(root.get("dueDate"), f.today()));
                p.add(cb.notEqual(root.get("status"), TaskStatus.DONE));
            }
            if (f.completedSince() != null) {
                p.add(cb.equal(root.get("status"), TaskStatus.DONE));
                p.add(cb.greaterThanOrEqualTo(root.get("completedAt"), f.completedSince()));
            }
            // Count queries must not carry an ORDER BY.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(orderBy(f, root, cb));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static List<Order> orderBy(TaskFilter f, Root<Task> root, CriteriaBuilder cb) {
        String sort = f.sort() == null ? "position" : f.sort();
        List<Order> orders = new ArrayList<>();
        switch (sort) {
            case "title" -> orders.add(dir(f, cb.lower(root.get("title")), cb));
            case "status" -> orders.add(dir(f, enumRank(root.get("status"), TaskStatus.values(), cb), cb));
            case "priority" -> orders.add(dir(f, enumRank(root.get("priority"), TaskPriority.values(), cb), cb));
            case "assignee" -> {
                var assignee = root.join("assignee", jakarta.persistence.criteria.JoinType.LEFT);
                orders.add(cb.asc(cb.selectCase().when(cb.isNull(assignee.get("id")), 1).otherwise(0)));
                orders.add(dir(f, cb.lower(assignee.get("displayName")), cb));
            }
            case "dueDate" -> {
                orders.add(cb.asc(cb.selectCase().when(cb.isNull(root.get("dueDate")), 1).otherwise(0)));
                orders.add(dir(f, root.get("dueDate"), cb));
            }
            case "updatedAt" -> orders.add(dir(f, root.get("updatedAt"), cb));
            case "createdAt" -> orders.add(dir(f, root.get("createdAt"), cb));
            case "key" -> {
                orders.add(dir(f, root.get("project").get("projectKey"), cb));
                orders.add(dir(f, root.get("number"), cb));
            }
            default -> orders.add(dir(f, root.get("position"), cb));
        }
        orders.add(cb.asc(root.get("id")));
        return orders;
    }

    private static <E extends Enum<E>> Expression<Integer> enumRank(Path<E> path, E[] values, CriteriaBuilder cb) {
        CriteriaBuilder.Case<Integer> c = cb.selectCase();
        for (E v : values) {
            c = c.when(cb.equal(path, v), v.ordinal());
        }
        return c.otherwise(values.length);
    }

    private static Order dir(TaskFilter f, Expression<?> e, CriteriaBuilder cb) {
        return f.desc() ? cb.desc(e) : cb.asc(e);
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
