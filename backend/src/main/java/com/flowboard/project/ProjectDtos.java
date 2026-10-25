package com.flowboard.project;

import com.flowboard.task.TaskDtos.TaskResponse;
import com.flowboard.task.TaskStatus;
import com.flowboard.user.UserDtos.UserSummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectResponse(
            UUID id, UUID workspaceId, String key, String name, String description, UserSummary owner,
            ProjectStatus status, boolean archived, LocalDate startDate, LocalDate targetDate,
            long taskCount, long doneCount, int completionPercent, boolean favorite,
            List<UserSummary> members, boolean canManage, long version, Instant createdAt, Instant updatedAt) {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9]{1,9}$", message = "2-10 uppercase letters or digits, starting with a letter") String key,
            @Size(max = 5000) String description,
            ProjectStatus status, LocalDate startDate, LocalDate targetDate, Set<UUID> memberIds) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 5000) String description,
            @NotNull ProjectStatus status, LocalDate startDate, LocalDate targetDate) {
    }

    public record AddMemberRequest(@NotNull UUID userId) {
    }

    public record MilestoneRequest(@NotBlank @Size(max = 120) String name, @NotNull LocalDate dueDate) {
    }

    public record MilestoneResponse(UUID id, String name, LocalDate dueDate) {
        public static MilestoneResponse of(Milestone m) {
            return new MilestoneResponse(m.getId(), m.getName(), m.getDueDate());
        }
    }

    public record ProjectOverview(
            ProjectResponse project, Map<TaskStatus, Long> tasksByStatus, long overdueCount,
            long completedThisWeek, List<TaskResponse> upcomingDeadlines, List<MilestoneResponse> milestones) {
    }
}
