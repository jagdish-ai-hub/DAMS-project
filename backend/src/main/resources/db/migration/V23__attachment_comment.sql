-- V23: an optional free-text note per attachment (e.g. "bill copy 2", "customer's
-- insurance photo"), settable when staging a file for upload and editable any time after
-- — including once the owning document is frozen, since a comment is a note, not a
-- financial change.

ALTER TABLE attachment ADD COLUMN comment VARCHAR(500);
