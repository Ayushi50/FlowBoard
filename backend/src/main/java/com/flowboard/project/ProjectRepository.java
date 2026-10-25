package com.flowboard.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("select p from Project p join fetch p.owner where p.id = :id")
    Optional<Project> findWithOwner(UUID id);

    /** Projects visible to a user: all of them for admins, otherwise only those they belong to. */
    @Query("""
            select p from Project p join fetch p.owner
            where p.workspaceId = :wsId
              and (:includeArchived = true or p.archived = false)
              and (:all = true or p.id in (select pm.project.id from ProjectMembership pm where pm.user.id = :userId))
              and (:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.projectKey) like lower(concat('%', :q, '%')))
            order by p.name
            """)
    List<Project> findVisible(UUID wsId, UUID userId, boolean all, boolean includeArchived, String q);

    boolean existsByWorkspaceIdAndProjectKey(UUID workspaceId, String key);
}
