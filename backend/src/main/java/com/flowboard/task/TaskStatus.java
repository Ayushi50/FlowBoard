package com.flowboard.task;

/** Workflow columns, declared in board order (ordinal is used for sorting). */
public enum TaskStatus {
    BACKLOG, TODO, IN_PROGRESS, DONE;

    public String label() {
        return switch (this) {
            case BACKLOG -> "Backlog";
            case TODO -> "To Do";
            case IN_PROGRESS -> "In Progress";
            case DONE -> "Done";
        };
    }
}
