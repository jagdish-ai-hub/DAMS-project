-- V20: traceability log for the Owner/Admin AI assistant (FEAT-09..FEAT-21).
-- Every /api/v1/ai/ask answer writes one row so a reported answer can be traced to the
-- exact log line via request_id (AGENT.md debuggability rule). Read-only everywhere else —
-- all other AI endpoints aggregate existing tables, no new writes.

CREATE TABLE ai_query_log (
    id BIGSERIAL PRIMARY KEY,
    org_id BIGINT NOT NULL REFERENCES organization(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    question VARCHAR(500) NOT NULL,
    cited_docs VARCHAR(1000) NOT NULL DEFAULT '',
    request_id VARCHAR(36),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ai_query_log_org_time_idx ON ai_query_log (org_id, created_at DESC);
