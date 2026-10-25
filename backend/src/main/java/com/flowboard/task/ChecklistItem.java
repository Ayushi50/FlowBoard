package com.flowboard.task;

import com.flowboard.common.BaseEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checklist_items")
public class ChecklistItem extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ChecklistItem() {
    }

    public ChecklistItem(UUID taskId, String text, int position) {
        this.taskId = taskId;
        this.text = text;
        this.position = position;
    }

    public UUID getTaskId() { return taskId; }
    public String getText() { return text; }
    public boolean isDone() { return done; }
    public int getPosition() { return position; }
    public void setText(String text) { this.text = text; }
    public void setDone(boolean done) { this.done = done; }
}
