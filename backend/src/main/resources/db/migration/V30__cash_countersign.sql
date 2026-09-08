-- V30: large-variance countersign (FEAT-41) — a second pair of eyes on cash.
--
-- The cashier counts and closes their own day; variance is self-declared.
-- With this, a close whose |variance| exceeds the org threshold lands in
-- PENDING_COUNTERSIGN instead of locking clean — an accountant confirms or
-- queries it. NULL threshold = feature off (existing behaviour, zero change).
-- Below-threshold closes are untouched: no new friction on honest days.

ALTER TABLE cash_day_close
    ADD COLUMN countersign_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN countersigned_by BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    ADD COLUMN countersigned_at TIMESTAMPTZ;

ALTER TABLE cash_day_close ADD CONSTRAINT cash_day_close_countersign_chk
    CHECK (countersign_status IN ('NOT_REQUIRED','PENDING','COUNTERSIGNED'));

ALTER TABLE organization
    ADD COLUMN cash_variance_countersign_threshold NUMERIC(14,2);
