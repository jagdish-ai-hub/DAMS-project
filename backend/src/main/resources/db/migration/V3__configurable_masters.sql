-- V3: the 8 Owner-editable master tables. Every dropdown the app shows comes from these —
-- nothing hard-coded (AGENT.md locked decision #3). All carry org_id; rows are deactivated,
-- never deleted. sort_order controls dropdown order.

-- ---- Receive side ----

CREATE TABLE receive_category (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    is_claim   BOOLEAN     NOT NULL DEFAULT FALSE,   -- Warranty / AMC / CG etc. -> FM closes with ClaimClose
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT receive_category_org_name_uq UNIQUE (org_id, name)
);

CREATE TABLE receive_business_status (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT receive_business_status_org_name_uq UNIQUE (org_id, name)
);

CREATE TABLE settlement_mode (
    id            BIGSERIAL   PRIMARY KEY,
    org_id        BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name          VARCHAR(120) NOT NULL,
    requires_bank BOOLEAN     NOT NULL DEFAULT FALSE,
    requires_ref  BOOLEAN     NOT NULL DEFAULT FALSE,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT settlement_mode_org_name_uq UNIQUE (org_id, name)
);

-- ---- Expense side ----

CREATE TABLE expense_category (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT expense_category_org_name_uq UNIQUE (org_id, name)
);

CREATE TABLE expense_sub_category (
    id                  BIGSERIAL   PRIMARY KEY,
    org_id              BIGINT      NOT NULL REFERENCES organization(id)     ON DELETE RESTRICT,
    expense_category_id BIGINT      NOT NULL REFERENCES expense_category(id) ON DELETE RESTRICT,
    name                VARCHAR(120) NOT NULL,
    limit_amount        NUMERIC(14,2),                 -- NULL = no per-line limit
    active              BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order          INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT expense_sub_category_uq UNIQUE (org_id, expense_category_id, name)
);

CREATE INDEX expense_sub_category_cat_idx ON expense_sub_category (expense_category_id);

CREATE TABLE expense_mode (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT expense_mode_org_name_uq UNIQUE (org_id, name)
);

CREATE TABLE expense_business_status (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT expense_business_status_org_name_uq UNIQUE (org_id, name)
);

-- ---- Shared ----

CREATE TABLE bank (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT bank_org_name_uq UNIQUE (org_id, name)
);

-- org_id lookup indexes (every read is org-scoped)
CREATE INDEX receive_category_org_idx        ON receive_category (org_id);
CREATE INDEX receive_business_status_org_idx ON receive_business_status (org_id);
CREATE INDEX settlement_mode_org_idx         ON settlement_mode (org_id);
CREATE INDEX expense_category_org_idx        ON expense_category (org_id);
CREATE INDEX expense_sub_category_org_idx    ON expense_sub_category (org_id);
CREATE INDEX expense_mode_org_idx            ON expense_mode (org_id);
CREATE INDEX expense_business_status_org_idx ON expense_business_status (org_id);
CREATE INDEX bank_org_idx                    ON bank (org_id);
