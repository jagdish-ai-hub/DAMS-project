-- V23: monthly spend caps per expense category (Owner-set budgets).
--
--   expense_budget — one cap per (org, expense_category, month). month_key is YYYYMM
--   (e.g. 202608), matching the document-number month key convention. The Owner sets the
--   cap; spend-vs-cap comparison happens at read time (dashboard / review), so this table
--   never blocks a submission — it only informs.

CREATE TABLE expense_budget (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id)     ON DELETE RESTRICT,
    category_id   BIGINT        NOT NULL REFERENCES expense_category(id) ON DELETE RESTRICT,
    month_key     VARCHAR(6)    NOT NULL,   -- YYYYMM, e.g. 202608
    cap_amount    NUMERIC(14,2) NOT NULL,

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT expense_budget_cap_chk CHECK (cap_amount > 0),
    CONSTRAINT expense_budget_month_chk CHECK (month_key ~ '^[0-9]{6}$'),
    CONSTRAINT expense_budget_org_cat_month_uq UNIQUE (org_id, category_id, month_key)
);

CREATE INDEX expense_budget_org_month_idx ON expense_budget (org_id, month_key);
