package com.flowboard.task;

import com.flowboard.task.TaskDtos.CreateLabelRequest;
import com.flowboard.task.TaskDtos.LabelDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Labels")
public class LabelController {

    private final LabelService labels;

    public LabelController(LabelService labels) {
        this.labels = labels;
    }

    @GetMapping("/workspaces/{workspaceId}/labels")
    public List<LabelDto> list(@PathVariable UUID workspaceId) {
        return labels.list(workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelDto create(@PathVariable UUID workspaceId, @Valid @RequestBody CreateLabelRequest req) {
        return labels.create(workspaceId, req);
    }

    @DeleteMapping("/labels/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        labels.delete(id);
    }
}
