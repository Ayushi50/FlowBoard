package com.flowboard.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    @Query("select i from WorkspaceInvitation i join fetch i.workspace where i.token = :token")
    Optional<WorkspaceInvitation> findByToken(String token);

    @Query("select i from WorkspaceInvitation i where i.workspace.id = :wsId and i.status = 'PENDING' order by i.createdAt desc")
    List<WorkspaceInvitation> findPending(UUID wsId);

    @Query("select i from WorkspaceInvitation i where i.workspace.id = :wsId and i.status = 'PENDING' and lower(i.email) = lower(:email)")
    Optional<WorkspaceInvitation> findPendingByEmail(UUID wsId, String email);
}
