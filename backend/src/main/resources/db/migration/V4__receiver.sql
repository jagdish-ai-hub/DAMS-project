-- V4: Receiver — vendor/payee master for expense documents. A full entity, not a text
-- field: a name alone is not a safe key (same reasoning as Customer). See AGENT.md.

CREATE TABLE receiver (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(160) NOT NULL,
    phone      VARCHAR(32),
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT receiver_org_name_uq UNIQUE (org_id, name)
);

CREATE INDEX receiver_org_idx ON receiver (org_id);
