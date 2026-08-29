-- V6: Customer + Vehicle. Both org-scoped. A customer is a person/company the dealership
-- deals with; a vehicle belongs to one customer and its number is the natural key
-- (normalised: uppercase, no spaces), unique per org. Neither is branch-scoped —
-- a case (JobCard, V8) is what ties a customer to a branch.

CREATE TABLE customer (
    id         BIGSERIAL    PRIMARY KEY,
    org_id     BIGINT       NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(160) NOT NULL,
    phone      VARCHAR(32),                       -- nullable: advances / walk-ins often have none
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX customer_org_idx       ON customer (org_id);
-- case-insensitive name lookups for universal search
CREATE INDEX customer_org_name_idx  ON customer (org_id, lower(name));

CREATE TABLE vehicle (
    id          BIGSERIAL   PRIMARY KEY,
    org_id      BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    customer_id BIGINT      NOT NULL REFERENCES customer(id)     ON DELETE RESTRICT,
    vehicle_no  VARCHAR(20) NOT NULL,             -- normalised: [A-Z0-9] only
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vehicle_org_no_uq UNIQUE (org_id, vehicle_no)
);

CREATE INDEX vehicle_org_idx      ON vehicle (org_id);
CREATE INDEX vehicle_customer_idx ON vehicle (customer_id);
