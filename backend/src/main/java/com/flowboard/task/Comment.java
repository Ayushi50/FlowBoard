package com.flowboard.task;

import com.flowboard.common.AuditedEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "comments")
public class Comment extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", updatable = false)
    private User author;

    @Column(nullable = false)
    private String body;

    protected Comment() {
    }

    public Comment(Task task, User author, String body) {
        this.task = task;
        this.author = author;
        this.body = body;
    }

    public Task getTask() { return task; }
    public User getAuthor() { return author; }
    public String getBody() { return body; }
}
