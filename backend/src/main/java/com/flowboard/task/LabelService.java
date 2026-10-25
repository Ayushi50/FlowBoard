package com.flowboard.task;

import com.flowboard.common.ApiException;
import com.flowboard.security.AccessService;
import com.flowboard.task.TaskDtos.CreateLabelRequest;
import com.flowboard.task.TaskDtos.LabelDto;
import com.flowboard.workspace.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabelService {

    private final LabelRepository labels;
    private final AccessService access;

    public LabelService(LabelRepository labels, AccessService access) {
        this.labels = labels;
        this.access = access;
    }

    @Transactional
    public void createDefaults(UUID workspaceId) {
        labels.saveAll(List.of(
                new Label(workspaceId, "bug", "#ef4444"),
                new Label(workspaceId, "feature", "#6366f1"),
                new Label(workspaceId, "design", "#ec4899"),
                new Label(workspaceId, "backend", "#0ea5e9"),
                new Label(workspaceId, "frontend", "#10b981"),
                new Label(workspaceId, "docs", "#f59e0b")));
    }

    @Transactional(readOnly = true)
    public List<LabelDto> list(UUID workspaceId) {
        access.membership(workspaceId);
        return labels.findByWorkspaceIdOrderByName(workspaceId).stream().map(LabelDto::of).toList();
    }

    @Transactional
    public LabelDto create(UUID workspaceId, CreateLabelRequest req) {
        access.requireRole(workspaceId, Role.ADMIN, Role.MANAGER);
        String name = req.name().trim();
        if (labels.existsByWorkspaceIdAndNameIgnoreCase(workspaceId, name)) {
            throw ApiException.conflict("LABEL_EXISTS", "A label named '" + name + "' already exists");
        }
        return LabelDto.of(labels.save(new Label(workspaceId, name, req.color())));
    }

    @Transactional
    public void delete(UUID labelId) {
        Label label = labels.findById(labelId).orElseThrow(() -> ApiException.notFound("Label"));
        access.requireRole(label.getWorkspaceId(), Role.ADMIN, Role.MANAGER);
        labels.delete(label);
    }
}
