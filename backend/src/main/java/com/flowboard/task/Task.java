package com.flowboard.task;

import com.flowboard.common.AuditedEntity;
import com.flowboard.project.Project;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasks")
public class Task extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", updatable = false)
    private Project project;

    @Column(name = "task_number", nullable = false, updatable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority = TaskPriority.NONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_type", nullable = false)
    private TaskType type = TaskType.TASK;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", updatable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Task parent;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(nullable = false)
    private double position;

    /** Optimistic lock: incremented by Hibernate on every update, checked against client-supplied versions. */
    @Version
    private long version;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToMany
    @JoinTable(name = "task_labels", joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    private Set<Label> labels = new HashSet<>();

    protected Task() {
    }

    public Task(Project project, int number, String title, User reporter) {
        this.project = project;
        this.number = number;
        this.title = title;
        this.reporter = reporter;
    }

    public String identifier() {
        return project.getKey() + "-" + number;
    }

    public Project getProject() { return project; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public TaskType getType() { return type; }
    public User getAssignee() { return assignee; }
    public User getReporter() { return reporter; }
    public Task getParent() { return parent; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public double getPosition() { return position; }
    public long getVersion() { return version; }
    public Instant getCompletedAt() { return completedAt; }
    public Set<Label> getLabels() { return labels; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public void setType(TaskType type) { this.type = type; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public void setParent(Task parent) { this.parent = parent; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public void setPosition(double position) { this.position = position; }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.completedAt = status == TaskStatus.DONE ? Instant.now() : null;
    }

    public boolean isOverdue(LocalDate today) {
        return dueDate != null && dueDate.isBefore(today) && status != TaskStatus.DONE;
    }
}
