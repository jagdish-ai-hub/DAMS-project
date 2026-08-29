-- V7: AuditEvent — append-only trail of who changed what. Created now so the Stage 3
-- job-card PATCH can record CATEGORY_CHANGED (before/after category ids); later stages
-- write SUBMITTED / VERIFIED / APPROVED / OVERRIDE / SETTLED / … through the same table.
--
-- actor_type = SYSTEM (with actor_id NULL) covers machine-made changes, e.g. the
-- auto-settle when a job card's pending amount hits zero (Stage 4).
--
-- detail holds a small JSON object ({"before":7,"after":9}). Stored as TEXT for now —
-- nothing queries into it in these stages, and TEXT is safe under hibernate ddl-auto:validate.
-- Upgrading the column to JSONB later is a one-line migration.

CREATE TABLE audit_event (
    id          BIGSERIAL   PRIMARY KEY,
    org_id      BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    entity_type VARCHAR(40) NOT NULL,             -- 'JobCard', 'ReceiveDocument', …
    entity_id   BIGINT      NOT NULL,
    event_type  VARCHAR(30) NOT NULL,
    actor_type  VARCHAR(10) NOT NULL,
    actor_id    BIGINT      REFERENCES app_user(id) ON DELETE SET NULL,  -- NULL when actor_type = SYSTEM
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_event_actor_type_chk CHECK (actor_type IN ('USER', 'SYSTEM')),
    CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
        'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
        'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED'
    ))
);

CREATE INDEX audit_event_org_entity_idx ON audit_event (org_id, entity_type, entity_id);
