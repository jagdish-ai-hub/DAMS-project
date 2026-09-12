-- V25: Owner-editable UPI IDs (VPAs) an org can collect payments on. Same shape as
-- every other master (OrgMaster: org-scoped, name, active, sort_order) plus the VPA
-- string itself. `name` is the label shown to the Owner AND the payee name a customer's
-- UPI app displays when paying — e.g. "JJ Motors - HDFC". Starts empty for every org;
-- until the Owner adds one, the UPI QR modal has nothing fake to fall back to.
CREATE TABLE upi_vpa (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    vpa        VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT upi_vpa_org_name_uq UNIQUE (org_id, name)
);

CREATE INDEX upi_vpa_org_idx ON upi_vpa (org_id);
