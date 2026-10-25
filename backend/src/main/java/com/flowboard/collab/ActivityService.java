package com.flowboard.collab;

import com.flowboard.user.User;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the audit trail inside the caller's transaction so a change and its history commit together. */
@Service
public class ActivityService {

    private final ActivityRepository repo;

    public ActivityService(ActivityRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(UUID workspaceId, UUID projectId, UUID taskId, User actor, ActivityAction action,
                       String summary, String oldValue, String newValue) {
        repo.save(new ActivityLog(workspaceId, projectId, taskId, actor, action, summary, oldValue, newValue));
    }
}
