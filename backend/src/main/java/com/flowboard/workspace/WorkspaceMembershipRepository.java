package com.flowboard.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {

    @Query("select m from WorkspaceMembership m join fetch m.user where m.workspace.id = :wsId and m.user.id = :userId")
    Optional<WorkspaceMembership> find(UUID wsId, UUID userId);

    @Query("select m from WorkspaceMembership m join fetch m.workspace where m.user.id = :userId order by m.workspace.name")
    List<WorkspaceMembership> findByUser(UUID userId);

    @Query("select m from WorkspaceMembership m join fetch m.user where m.workspace.id = :wsId order by m.user.displayName")
    List<WorkspaceMembership> findByWorkspace(UUID wsId);

    @Query("select count(m) from WorkspaceMembership m where m.workspace.id = :wsId and m.role = :role")
    long countByRole(UUID wsId, Role role);

    @Query("select count(m) from WorkspaceMembership m where m.workspace.id = :wsId")
    long countByWorkspace(UUID wsId);

    @Query("select count(m) > 0 from WorkspaceMembership m where m.workspace.id = :wsId and m.user.id = :userId")
    boolean isMember(UUID wsId, UUID userId);
}
