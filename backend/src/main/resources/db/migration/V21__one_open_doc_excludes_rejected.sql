-- V21: a REJECTED receive document is terminal but keeps settled = false, so the
-- V11 one-open-per-job-card partial index still counted it and the clean new
-- draft after a reject died on INSERT with a unique violation (the BUG-03 finder
-- fix excluded REJECTED rows from the lookup, but not from the index).
-- Narrow the index to genuinely-open documents; queries are unaffected.

DROP INDEX IF EXISTS receive_document_one_open_per_job_card;

CREATE UNIQUE INDEX receive_document_one_open_per_job_card
    ON receive_document (org_id, job_card_id)
    WHERE settled = false AND workflow_status <> 'REJECTED';
