package com.flowboard.task;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findByWorkspaceIdOrderByName(UUID workspaceId);
    List<Label> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);
    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);
}
