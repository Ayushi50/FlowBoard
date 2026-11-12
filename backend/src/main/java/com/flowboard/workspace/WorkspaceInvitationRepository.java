package com.flowboard.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    @Query("select i from WorkspaceInvitation i join fetch i.workspace where i.token = :token")
    Optional<WorkspaceInvitation> findByToken(String token);

    @Query("select i from WorkspaceInvitation i where i.workspace.id = :wsId and i.status = 'PENDING' order by i.createdAt desc")
    List<WorkspaceInvitation> findPending(UUID wsId);

    @Query("select i from WorkspaceInvitation i where i.workspace.id = :wsId and i.status = 'PENDING' and lower(i.email) = lower(:email)")
    Optional<WorkspaceInvitation> findPendingByEmail(UUID wsId, String email);

    @Transactional
    @Modifying
    @Query("update WorkspaceInvitation i set i.emailStatus = :status where i.id = :id")
    void updateEmailStatus(UUID id, WorkspaceInvitation.EmailStatus status);

    /** Marks emails left QUEUED by a previous run (their async task died with the process) as FAILED. */
    @Transactional
    @Modifying
    @Query("update WorkspaceInvitation i set i.emailStatus = 'FAILED' where i.emailStatus = 'QUEUED'")
    int failStaleQueued();
}
