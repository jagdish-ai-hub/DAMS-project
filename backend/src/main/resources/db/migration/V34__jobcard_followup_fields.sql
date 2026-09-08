-- V34: job-card follow-up fields (FEAT-39 renewals, FEAT-50 WIP board).
--
-- service_due_date: when the vehicle is next due back (AMC expiry, service
-- reminder). Set by the cashier at billing; NULL means "no reminder wanted" —
-- never defaulted, so nobody gets nagged by accident.
-- stuck_reason: why a WIP/hold job card isn't moving. Shown on the floor
-- board; cleared when the status moves on. Free text beats a master list
-- here — reasons are "waiting for Eicher approval", not categories.

ALTER TABLE job_card
    ADD COLUMN service_due_date DATE,
    ADD COLUMN stuck_reason VARCHAR(500);
