CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------- identity
CREATE TABLE users (
    id            UUID PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    display_name  VARCHAR(120) NOT NULL,
    job_title     VARCHAR(120),
    avatar_color  VARCHAR(16)  NOT NULL DEFAULT '#6366f1',
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

CREATE TABLE workspaces (
    id         UUID PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    created_by UUID         NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL
);

CREATE TABLE workspace_memberships (
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         VARCHAR(16) NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'MEMBER')),
    joined_at    TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workspace_member UNIQUE (workspace_id, user_id)
);
CREATE INDEX ix_wsm_user ON workspace_memberships (user_id);

CREATE TABLE workspace_invitations (
    id           UUID PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    role         VARCHAR(16)  NOT NULL CHECK (role IN ('ADMIN', 'MANAGER', 'MEMBER')),
    token        VARCHAR(64)  NOT NULL,
    status       VARCHAR(16)  NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED')),
    invited_by   UUID         NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_invitation_token UNIQUE (token)
);
CREATE INDEX ix_invitation_ws ON workspace_invitations (workspace_id, status);
CREATE INDEX ix_invitation_email ON workspace_invitations (lower(email));

-- ---------------------------------------------------------------- projects
CREATE TABLE projects (
    id           UUID PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_key  VARCHAR(10)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    description  TEXT,
    owner_id     UUID         NOT NULL REFERENCES users (id),
    status       VARCHAR(16)  NOT NULL CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED')),
    archived     BOOLEAN      NOT NULL DEFAULT FALSE,
    start_date   DATE,
    target_date  DATE,
    task_seq     INTEGER      NOT NULL DEFAULT 0,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_project_key UNIQUE (workspace_id, project_key)
);
CREATE INDEX ix_projects_ws ON projects (workspace_id, archived);

CREATE TABLE project_memberships (
    id         UUID PRIMARY KEY,
    project_id UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_project_member UNIQUE (project_id, user_id)
);
CREATE INDEX ix_pm_user ON project_memberships (user_id);

CREATE TABLE project_favorites (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, project_id)
);

CREATE TABLE project_visits (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    project_id UUID        NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    visited_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, project_id)
);

CREATE TABLE milestones (
    id         UUID PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    due_date   DATE         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_milestones_project ON milestones (project_id);

-- ---------------------------------------------------------------- tasks
CREATE TABLE tasks (
    id           UUID PRIMARY KEY,
    project_id   UUID             NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    task_number  INTEGER          NOT NULL,
    title        VARCHAR(255)     NOT NULL,
    description  TEXT,
    status       VARCHAR(16)      NOT NULL CHECK (status IN ('BACKLOG', 'TODO', 'IN_PROGRESS', 'DONE')),
    priority     VARCHAR(16)      NOT NULL CHECK (priority IN ('URGENT', 'HIGH', 'MEDIUM', 'LOW', 'NONE')),
    task_type    VARCHAR(16)      NOT NULL CHECK (task_type IN ('TASK', 'BUG', 'FEATURE', 'IMPROVEMENT')),
    assignee_id  UUID REFERENCES users (id) ON DELETE SET NULL,
    reporter_id  UUID             NOT NULL REFERENCES users (id),
    parent_id    UUID REFERENCES tasks (id) ON DELETE CASCADE,
    start_date   DATE,
    due_date     DATE,
    position     DOUBLE PRECISION NOT NULL,
    version      BIGINT           NOT NULL DEFAULT 0,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ      NOT NULL,
    updated_at   TIMESTAMPTZ      NOT NULL,
    CONSTRAINT uq_task_number UNIQUE (project_id, task_number)
);
-- board query: one project, one column, ordered by position
CREATE INDEX ix_tasks_board ON tasks (project_id, status, position);
CREATE INDEX ix_tasks_assignee ON tasks (assignee_id, status, due_date);
CREATE INDEX ix_tasks_due ON tasks (project_id, due_date) WHERE due_date IS NOT NULL;
CREATE INDEX ix_tasks_parent ON tasks (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX ix_tasks_completed ON tasks (project_id, completed_at) WHERE completed_at IS NOT NULL;
CREATE INDEX ix_tasks_title_trgm ON tasks USING gin (title gin_trgm_ops);
CREATE INDEX ix_tasks_desc_trgm ON tasks USING gin (description gin_trgm_ops);
CREATE INDEX ix_projects_name_trgm ON projects USING gin (name gin_trgm_ops);

CREATE TABLE labels (
    id           UUID PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         VARCHAR(40) NOT NULL,
    color        VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_label_name UNIQUE (workspace_id, name)
);

CREATE TABLE task_labels (
    task_id  UUID NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    label_id UUID NOT NULL REFERENCES labels (id) ON DELETE CASCADE,
    PRIMARY KEY (task_id, label_id)
);
CREATE INDEX ix_task_labels_label ON task_labels (label_id);

CREATE TABLE checklist_items (
    id         UUID PRIMARY KEY,
    task_id    UUID         NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    text       VARCHAR(255) NOT NULL,
    done       BOOLEAN      NOT NULL DEFAULT FALSE,
    position   INTEGER      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_checklist_task ON checklist_items (task_id, position);

CREATE TABLE comments (
    id         UUID PRIMARY KEY,
    task_id    UUID        NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_comments_task ON comments (task_id, created_at);

CREATE TABLE attachment_metadata (
    id           UUID PRIMARY KEY,
    task_id      UUID         NOT NULL REFERENCES tasks (id) ON DELETE CASCADE,
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    size_bytes   BIGINT       NOT NULL CHECK (size_bytes >= 0),
    uploaded_by  UUID         NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_attachments_task ON attachment_metadata (task_id);

-- ---------------------------------------------------------------- collaboration
CREATE TABLE notifications (
    id           UUID PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type         VARCHAR(24)  NOT NULL,
    actor_id     UUID         REFERENCES users (id) ON DELETE SET NULL,
    task_id      UUID         REFERENCES tasks (id) ON DELETE CASCADE,
    project_id   UUID         REFERENCES projects (id) ON DELETE CASCADE,
    message      VARCHAR(500) NOT NULL,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_notifications_inbox ON notifications (user_id, workspace_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id, workspace_id) WHERE read_at IS NULL;

CREATE TABLE activity_logs (
    id           UUID PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    project_id   UUID         REFERENCES projects (id) ON DELETE CASCADE,
    task_id      UUID         REFERENCES tasks (id) ON DELETE CASCADE,
    actor_id     UUID         NOT NULL REFERENCES users (id),
    action       VARCHAR(32)  NOT NULL,
    summary      VARCHAR(500) NOT NULL,
    old_value    VARCHAR(255),
    new_value    VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_activity_ws ON activity_logs (workspace_id, created_at DESC);
CREATE INDEX ix_activity_project ON activity_logs (project_id, created_at DESC);
CREATE INDEX ix_activity_task ON activity_logs (task_id, created_at DESC);

-- Makes POST /tasks safe to retry: same (user, key) returns the original task.
CREATE TABLE idempotency_keys (
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    idem_key   VARCHAR(80) NOT NULL,
    task_id    UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, idem_key)
);
