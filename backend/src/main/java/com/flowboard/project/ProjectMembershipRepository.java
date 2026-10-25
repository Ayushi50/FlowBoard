package com.flowboard.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProjectMembershipRepository extends JpaRepository<ProjectMembership, UUID> {

    @Query("select m from ProjectMembership m join fetch m.user where m.project.id in :projectIds order by m.user.displayName")
    List<ProjectMembership> findByProjects(List<UUID> projectIds);

    @Query("select m from ProjectMembership m where m.project.id = :projectId and m.user.id = :userId")
    Optional<ProjectMembership> find(UUID projectId, UUID userId);

    @Query("select count(m) > 0 from ProjectMembership m where m.project.id = :projectId and m.user.id = :userId")
    boolean isMember(UUID projectId, UUID userId);

    @Modifying
    @Query("delete from ProjectMembership m where m.user.id = :userId and m.project.id in (select p.id from Project p where p.workspaceId = :wsId)")
    int deleteByUserInWorkspace(UUID userId, UUID wsId);
}
