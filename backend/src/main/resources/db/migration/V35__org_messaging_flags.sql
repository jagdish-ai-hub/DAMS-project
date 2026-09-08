-- V35: org messaging flags (FEAT-36 templates per org are seeded in V26;
-- FEAT-42 digest needs an explicit opt-in — pushing WhatsApp to an owner
-- who never asked is spam, not a feature).

ALTER TABLE organization
    ADD COLUMN digest_enabled BOOLEAN NOT NULL DEFAULT FALSE;
