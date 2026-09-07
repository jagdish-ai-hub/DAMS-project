-- V22: line numbers must never be reused, even after a line is voided — the audit
-- trail references {documentNo}-L{n} permanently, and deletes are physical, so the
-- old max(line_no)+1 reused a deleted line's number (its comment claimed otherwise).
-- A monotonic per-document counter replaces it.
--
-- Backfill proof: every added line writes exactly one LINE_ADDED audit event, and
-- numbers are allocated 1,2,3… with repeats only (never skips), so the highest
-- number ever used is always <= GREATEST(present max(line_no), LINE_ADDED count).
-- Seeded lines have no events but are contiguous from 1, covered by the first term.

ALTER TABLE receive_document ADD COLUMN line_no_seq INTEGER NOT NULL DEFAULT 0;

UPDATE receive_document d SET line_no_seq = GREATEST(
    COALESCE((SELECT MAX(l.line_no) FROM settlement_line l WHERE l.receive_document_id = d.id), 0),
    COALESCE((SELECT COUNT(*)::int FROM audit_event ae
        WHERE ae.org_id = d.org_id AND ae.entity_type = 'ReceiveDocument' AND ae.entity_id = d.id
          AND ae.event_type = 'LINE_ADDED'), 0));

ALTER TABLE expense_document ADD COLUMN line_no_seq INTEGER NOT NULL DEFAULT 0;

UPDATE expense_document d SET line_no_seq = GREATEST(
    COALESCE((SELECT MAX(l.line_no) FROM expense_line l WHERE l.expense_document_id = d.id), 0),
    COALESCE((SELECT COUNT(*)::int FROM audit_event ae
        WHERE ae.org_id = d.org_id AND ae.entity_type = 'ExpenseDocument' AND ae.entity_id = d.id
          AND ae.event_type = 'LINE_ADDED'), 0));
