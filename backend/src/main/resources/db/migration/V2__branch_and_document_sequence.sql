-- V2: Branch + DocumentSequence, and the FKs deferred from V1.
-- Branch is the first org-scoped entity. Every row from here down carries org_id.

CREATE TABLE branch (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    code       VARCHAR(5)  NOT NULL,          -- 2-5 chars, e.g. OOR; unique per org, NOT globally
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT branch_code_len_chk CHECK (char_length(code) BETWEEN 2 AND 5),
    CONSTRAINT branch_org_code_uq  UNIQUE (org_id, code)
);

CREATE INDEX branch_org_idx ON branch (org_id);

-- Gap-free per-branch/per-month/per-type counter. Created now; first used for document
-- numbering in Stage 4. last_seq is bumped under SELECT ... FOR UPDATE inside the submit txn.
CREATE TABLE document_sequence (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id  BIGINT      NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    month_key  VARCHAR(6)  NOT NULL,          -- e.g. JUL26
    doc_type   VARCHAR(4)  NOT NULL,          -- R (receive) / E (expense) / C (cash)
    last_seq   INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT document_sequence_type_chk CHECK (doc_type IN ('R', 'E', 'C')),
    CONSTRAINT document_sequence_uq       UNIQUE (org_id, branch_id, month_key, doc_type)
);

-- FKs deferred from V1 (the branch table did not exist yet).
ALTER TABLE app_user
    ADD CONSTRAINT app_user_home_branch_fk
    FOREIGN KEY (home_branch_id) REFERENCES branch(id) ON DELETE RESTRICT;

ALTER TABLE user_branch_access
    ADD CONSTRAINT user_branch_access_branch_fk
    FOREIGN KEY (branch_id) REFERENCES branch(id) ON DELETE CASCADE;
