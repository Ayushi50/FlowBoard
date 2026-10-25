package com.flowboard.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowboard.security.AccessService.ProjectAccess;
import com.flowboard.workspace.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectAccessRulesTest {

    private ProjectAccess access(Role role, boolean member) {
        return new ProjectAccess(null, UUID.randomUUID(), role, member);
    }

    @Test
    void adminsAlwaysManage() {
        assertThat(access(Role.ADMIN, false).canManageProject()).isTrue();
    }

    @Test
    void managersManageOnlyProjectsTheyBelongTo() {
        assertThat(access(Role.MANAGER, true).canManageProject()).isTrue();
        assertThat(access(Role.MANAGER, false).canManageProject()).isFalse();
    }

    @Test
    void membersNeverManage() {
        assertThat(access(Role.MEMBER, true).canManageProject()).isFalse();
        assertThat(access(Role.MEMBER, true).canManageMembers()).isFalse();
    }
}
