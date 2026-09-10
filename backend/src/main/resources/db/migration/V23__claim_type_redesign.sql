-- Claim Type redesign: claim-ness moves off receive_category onto job_card.
--
-- Today a receipt's "Tran. Category" conflates two different things: the actual shop
-- work (Workshop, Breakdown, Advance, ...) and whether the job card is a Warranty / AMC /
-- CGW claim (receive_category.is_claim). This splits them: Category becomes a plain
-- "Transaction Type" (no claim options), and a new job-card-level Claim Type carries the
-- claim fact instead.
--
-- Data migration (Option B — full cleanup, chosen over a minimal keep-as-is patch): every
-- job card currently sitting on a claim category is backfilled with the matching new
-- claim_type row, then its category is reassigned to "Workshop" — there is no record of
-- what shop work was actually done under the old claim categories, so Workshop is the
-- documented fallback. The old claim rows in receive_category are deactivated (never
-- deleted, per the master-data convention) so they drop out of the Transaction Type
-- dropdown going forward.

-- 1. New claim_type master table — same shape as every other Owner-editable master
--    (OrgMaster: org-scoped, name, active, sort_order), no extra flags.
CREATE TABLE claim_type (
    id         BIGSERIAL   PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT claim_type_org_name_uq UNIQUE (org_id, name)
);

-- 2. Seed one claim_type row per org from every claim category that org currently has
--    (covers every org's data, not just the demo dealership).
INSERT INTO claim_type (org_id, name, active, sort_order, created_at)
SELECT org_id, name, active, sort_order, created_at
FROM receive_category
WHERE is_claim = TRUE;

-- 3. job_card gets its own claim_type_id — the claim fact lives on the case, not the
--    category. Nullable: most job cards are not a claim.
ALTER TABLE job_card ADD COLUMN claim_type_id BIGINT REFERENCES claim_type(id) ON DELETE RESTRICT;

-- 4. Backfill every job card currently on a claim category: point it at the matching new
--    claim_type row (same org + name).
UPDATE job_card jc
SET claim_type_id = ct.id
FROM receive_category rc
JOIN claim_type ct ON ct.org_id = rc.org_id AND ct.name = rc.name
WHERE jc.category_id = rc.id
  AND rc.is_claim = TRUE;

-- 5. Reassign those job cards' category to "Workshop" (the org's default, non-claim
--    category) — the shop work behind an old claim job card was never separately
--    recorded, so this is the documented, deliberate fallback.
UPDATE job_card jc
SET category_id = workshop.id
FROM receive_category workshop
WHERE jc.claim_type_id IS NOT NULL
  AND workshop.org_id = jc.org_id
  AND workshop.name = 'Workshop'
  AND jc.category_id <> workshop.id;

-- 6. Deactivate the old claim rows — never delete, so historical documents still resolve
--    the category name they were created under, but they vanish from the dropdown.
UPDATE receive_category SET active = FALSE WHERE is_claim = TRUE;

-- 7. A job-card-level Claim Type change gets its own audit event, alongside category
--    changes — extend the same CHECK constraint the way TRANSFERRED_TO_CLAIM was added.
ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM'
));
