package com.flowboard.task;

import com.flowboard.common.BaseEntity;
import com.flowboard.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Metadata only: binary storage is intentionally out of scope (see README). */
@Entity
@Table(name = "attachment_metadata")
public class AttachmentMetadata extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected AttachmentMetadata() {
    }

    public AttachmentMetadata(UUID taskId, String fileName, String contentType, long sizeBytes, User uploadedBy) {
        this.taskId = taskId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }

    public UUID getTaskId() { return taskId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public User getUploadedBy() { return uploadedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
