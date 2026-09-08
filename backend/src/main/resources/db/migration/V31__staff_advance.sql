-- V31: staff advances (FEAT-44) — given vs recovered, per staff, no notebook.
--
-- Staff salary advances and their recovery from wages are a constant source of
-- counter disputes. Separate from customer and vendor flows by design: staff
-- are neither customers nor receivers, so they get their own small master
-- plus an append-only entry ledger (ADVANCE out, RECOVERY in). Outstanding
-- per staff is derived (advances − recoveries), never stored.

CREATE TABLE staff_member (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name        VARCHAR(200)  NOT NULL,
    phone       VARCHAR(20),
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT staff_member_org_name_uq UNIQUE (org_id, name)
);

CREATE TABLE staff_advance_entry (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organization(id)   ON DELETE RESTRICT,
    staff_id    BIGINT        NOT NULL REFERENCES staff_member(id)   ON DELETE RESTRICT,
    kind        VARCHAR(10)   NOT NULL,
    amount      NUMERIC(14,2) NOT NULL,
    txn_date    DATE          NOT NULL,
    note        VARCHAR(500),
    created_by  BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT staff_advance_kind_chk CHECK (kind IN ('ADVANCE','RECOVERY')),
    CONSTRAINT staff_advance_amount_chk CHECK (amount > 0)
);

CREATE INDEX staff_advance_staff_idx ON staff_advance_entry (org_id, staff_id);
