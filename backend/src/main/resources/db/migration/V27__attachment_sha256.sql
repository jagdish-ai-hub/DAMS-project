-- V27: duplicate bill detection (FEAT-37) — content hash on every attachment.
--
-- The cheapest fraud is one bill photo attached to two claims. Storing a
-- SHA-256 at upload time makes exact duplicates a one-index lookup, with
-- zero false positives. Backfill is intentionally absent: old rows stay NULL
-- and are simply never matched (no retroactive accusations from a new rule).

ALTER TABLE attachment ADD COLUMN sha256 VARCHAR(64);

CREATE INDEX attachment_org_sha_idx ON attachment (org_id, sha256)
    WHERE sha256 IS NOT NULL;
