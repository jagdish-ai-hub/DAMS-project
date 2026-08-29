-- V8: JobCard — the case. It anchors a customer (and usually a vehicle) to a branch and
-- carries the case-level money fields: invoice_no / invoice_amount (external references,
-- never DAMS-generated) plus category_id and business_status_id (both editable via
-- PATCH /job-cards/{id} until a ClaimClose row exists — that table arrives in Stage 8).
--
-- is_claim is NOT stored here — it is read from receive_category.is_claim via category_id,
-- so the flag can never drift from the category.
--
-- The user-facing reference shown on screens is "{branchCode}-JC-{id}", derived at read
-- time. dbm_id (Eicher's external job-card number) is a separate, nullable, manual field.

CREATE TABLE job_card (
    id                 BIGSERIAL     PRIMARY KEY,
    org_id             BIGINT        NOT NULL REFERENCES organization(id)           ON DELETE RESTRICT,
    branch_id          BIGINT        NOT NULL REFERENCES branch(id)                 ON DELETE RESTRICT,
    customer_id        BIGINT        NOT NULL REFERENCES customer(id)               ON DELETE RESTRICT,
    vehicle_id         BIGINT        REFERENCES vehicle(id)                         ON DELETE RESTRICT,  -- nullable
    dbm_id             VARCHAR(40),                     -- Eicher external job-card no. — nullable, manual
    invoice_no         VARCHAR(60),                     -- external reference — nullable, manual
    invoice_amount     NUMERIC(14,2),                   -- nullable until an invoice is raised
    category_id        BIGINT        NOT NULL REFERENCES receive_category(id)       ON DELETE RESTRICT,
    business_status_id BIGINT        NOT NULL REFERENCES receive_business_status(id) ON DELETE RESTRICT,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX job_card_org_idx        ON job_card (org_id);
CREATE INDEX job_card_branch_idx     ON job_card (org_id, branch_id);
CREATE INDEX job_card_customer_idx   ON job_card (customer_id);
CREATE INDEX job_card_vehicle_idx    ON job_card (vehicle_id);
-- universal search matches on invoice_no and dbm_id
CREATE INDEX job_card_org_invoice_idx ON job_card (org_id, lower(invoice_no));
CREATE INDEX job_card_org_dbm_idx     ON job_card (org_id, lower(dbm_id));
