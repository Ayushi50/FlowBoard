ALTER TABLE workspace_invitations
    ADD COLUMN email_status VARCHAR(16) NOT NULL DEFAULT 'NOT_SENT'
        CHECK (email_status IN ('NOT_SENT', 'QUEUED', 'SENT', 'FAILED'));
