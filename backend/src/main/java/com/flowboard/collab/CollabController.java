package com.flowboard.collab;

import com.flowboard.collab.CollabDtos.*;
import com.flowboard.collab.DashboardService.Dashboard;
import com.flowboard.collab.DashboardService.SearchResults;
import com.flowboard.common.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dashboard, search, activity & notifications")
public class CollabController {

    private final DashboardService dashboard;
    private final ActivityQueryService activity;
    private final NotificationService notifications;

    public CollabController(DashboardService dashboard, ActivityQueryService activity, NotificationService notifications) {
        this.dashboard = dashboard;
        this.activity = activity;
        this.notifications = notifications;
    }

    @GetMapping("/workspaces/{wsId}/dashboard")
    public Dashboard dashboard(@PathVariable UUID wsId) {
        return dashboard.dashboard(wsId);
    }

    @GetMapping("/workspaces/{wsId}/search")
    public SearchResults search(@PathVariable UUID wsId, @RequestParam String q) {
        return dashboard.search(wsId, q);
    }

    @GetMapping("/workspaces/{wsId}/activity")
    public PageResponse<ActivityResponse> activity(@PathVariable UUID wsId, @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return activity.forWorkspace(wsId, page, size);
    }

    @GetMapping("/workspaces/{wsId}/notifications")
    public PageResponse<NotificationResponse> inbox(@PathVariable UUID wsId,
                                                    @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return notifications.inbox(wsId, unreadOnly, page, size);
    }

    @GetMapping("/workspaces/{wsId}/notifications/unread-count")
    public UnreadCount unread(@PathVariable UUID wsId) {
        return new UnreadCount(notifications.unreadCount(wsId));
    }

    @PostMapping("/workspaces/{wsId}/notifications/read-all")
    public UnreadCount readAll(@PathVariable UUID wsId) {
        notifications.markAllRead(wsId);
        return new UnreadCount(0);
    }

    @PostMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.OK)
    public NotificationResponse markRead(@PathVariable UUID id) {
        return notifications.markRead(id);
    }
}
