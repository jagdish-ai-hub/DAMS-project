-- rev 72: a receipt's documents used to freeze the moment the receipt was fully paid ("settled"),
-- even while it was still in review. When the Accountant then queried it for a missing or wrong
-- bill, the Cashier could neither add nor replace one (live receipts OOR-OCT26-R-011 / R-012).
-- Files now freeze only at approval. Un-freeze the ones frozen too early: files on a receipt that
-- is not APPROVED / REJECTED now AND was never approved (a receipt re-opened from APPROVED by an
-- added payment keeps the freeze its original files earned at approval).
UPDATE attachment a
   SET frozen = FALSE
 WHERE a.frozen = TRUE
   AND (
        (a.parent_type = 'RECEIVE_DOCUMENT' AND EXISTS (
            SELECT 1 FROM receive_document d
             WHERE d.id = a.parent_id
               AND d.workflow_status NOT IN ('APPROVED', 'REJECTED')
               AND NOT EXISTS (SELECT 1 FROM audit_event e
                                WHERE e.entity_type = 'ReceiveDocument' AND e.entity_id = d.id
                                  AND e.event_type = 'APPROVED')))
     OR (a.parent_type = 'SETTLEMENT_LINE' AND EXISTS (
            SELECT 1 FROM settlement_line l
              JOIN receive_document d ON d.id = l.receive_document_id
             WHERE l.id = a.parent_id
               AND d.workflow_status NOT IN ('APPROVED', 'REJECTED')
               AND NOT EXISTS (SELECT 1 FROM audit_event e
                                WHERE e.entity_type = 'ReceiveDocument' AND e.entity_id = d.id
                                  AND e.event_type = 'APPROVED')))
   );
