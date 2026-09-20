-- Job-card business status becomes role-mapped.
--
-- Until now any role that could touch a job card could set any business status, and in
-- practice only the Cashier ever did — no Accountant/Finance screen exposed the field at
-- all. The dealership's actual process splits the list: the Cashier works three statuses,
-- the Accountant adds "Transfer to Claim", and Finance owns the claim-settlement end
-- (Closed / Claim Received / Claim Pending). That split is data, not code, so the Owner
-- can remap it from Masters without a release.
--
-- The previous statuses are NOT removed. They stay selectable by every role but are
-- flagged `deprecated` and sorted below the live ones, so existing job cards keep working
-- and staff can still find the label they used to use. AMC / CG / Warranty are already
-- redundant (V23 moved claim-ness onto job_card.claim_type_id) but dropping them is a
-- separate decision from this one.

-- 1. Deprecated is deliberately separate from `active`: an inactive row disappears from
--    the dropdown entirely, a deprecated one still works but is visibly on its way out.
ALTER TABLE receive_business_status ADD COLUMN deprecated BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Which roles may SET which status. Absence of a row means "this role cannot pick it".
--    Role is a plain string for the same reason user.role is (see Role.java) — adding a
--    role should never require DDL.
CREATE TABLE receive_business_status_role (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    status_id  BIGINT      NOT NULL REFERENCES receive_business_status(id) ON DELETE CASCADE,
    role       VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT receive_business_status_role_chk CHECK (role IN ('CASHIER', 'ACCOUNTANT', 'FINANCE_MANAGER')),
    CONSTRAINT receive_business_status_role_uq UNIQUE (status_id, role)
);

CREATE INDEX receive_business_status_role_org_idx ON receive_business_status_role (org_id);

-- 3. Every status that already exists — the seeded six, Credit, and any an org added
--    itself — is mapped to all three roles first. Without this an org's custom status
--    would silently become unpickable by everyone the moment this migration ran.
INSERT INTO receive_business_status_role (org_id, status_id, role)
SELECT s.org_id, s.id, r.role
FROM receive_business_status s
CROSS JOIN (VALUES ('CASHIER'), ('ACCOUNTANT'), ('FINANCE_MANAGER')) AS r(role);

-- 4. The new statuses, for every org. Credit already exists from the original seed, so it
--    is not re-inserted — step 6 just moves it up into the live block.
INSERT INTO receive_business_status (org_id, name, active, sort_order, deprecated)
SELECT o.id, v.name, TRUE, v.sort_order, FALSE
FROM organization o
CROSS JOIN (VALUES
    ('Received',          1),
    ('Waiting for Claim', 2),
    ('Transfer to Claim', 4),
    ('Closed',            5),
    ('Claim Received',    6),
    ('Claim Pending',     7)
) AS v(name, sort_order)
ON CONFLICT (org_id, name) DO NOTHING;

-- 5. Role mapping for the new statuses. Cumulative: the Accountant sees everything the
--    Cashier does plus Transfer to Claim; Finance sees all seven. ON CONFLICT covers the
--    case where an org already had a status under one of these names (step 3 mapped it).
INSERT INTO receive_business_status_role (org_id, status_id, role)
SELECT s.org_id, s.id, v.role
FROM receive_business_status s
JOIN (VALUES
    ('Received',          'CASHIER'),
    ('Received',          'ACCOUNTANT'),
    ('Received',          'FINANCE_MANAGER'),
    ('Waiting for Claim', 'CASHIER'),
    ('Waiting for Claim', 'ACCOUNTANT'),
    ('Waiting for Claim', 'FINANCE_MANAGER'),
    ('Transfer to Claim', 'ACCOUNTANT'),
    ('Transfer to Claim', 'FINANCE_MANAGER'),
    ('Closed',            'FINANCE_MANAGER'),
    ('Claim Received',    'FINANCE_MANAGER'),
    ('Claim Pending',     'FINANCE_MANAGER')
) AS v(name, role) ON v.name = s.name
ON CONFLICT (status_id, role) DO NOTHING;

-- 6. Credit keeps its existing row (and therefore every job card pointing at it) and joins
--    the live block at position 3, stays available to all three roles from step 3.
UPDATE receive_business_status
SET sort_order = 3, deprecated = FALSE
WHERE name = 'Credit';

-- 7. Retire the originals: still pickable, but marked and pushed below the live list.
UPDATE receive_business_status
SET deprecated = TRUE, sort_order = 90 + sort_order
WHERE name IN ('Hold', 'AMC', 'CG', 'WIP', 'Warranty', 'Close');
