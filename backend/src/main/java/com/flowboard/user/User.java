package com.flowboard.user;

import com.flowboard.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends AuditedEntity {

    @Column(nullable = false)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "avatar_color", nullable = false)
    private String avatarColor;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    protected User() {
    }

    public User(String email, String displayName, String passwordHash, String avatarColor) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.avatarColor = avatarColor;
    }

    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getJobTitle() { return jobTitle; }
    public String getAvatarColor() { return avatarColor; }
    public String getPasswordHash() { return passwordHash; }

    public void updateProfile(String displayName, String jobTitle, String avatarColor) {
        this.displayName = displayName;
        this.jobTitle = jobTitle;
        if (avatarColor != null) {
            this.avatarColor = avatarColor;
        }
    }
}
