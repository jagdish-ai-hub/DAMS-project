-- Before V2.6 (2026-09-24) an Accountant's Add Payment stamped receive_document.last_modified_by
-- with the Accountant, which maker-checker (ReviewGuard) then used to lock that same Accountant
-- out of verifying / resending the document. The code no longer does this (an Accountant's
-- review-side touch never moves last_modified_by), but rows written earlier kept the stale value.
--
-- Repair: for receipts still in review, hand last_modified_by back to the maker (created_by)
-- where an ACCOUNTANT is recorded as the last modifier. The Accountant's OVERRIDE and
-- LINE_ADDED rows stay in audit_event, so the trail is untouched.

UPDATE receive_document d
   SET last_modified_by = d.created_by
  FROM app_user u
 WHERE u.id = d.last_modified_by
   AND u.role = 'ACCOUNTANT'
   AND d.last_modified_by <> d.created_by
   AND d.workflow_status IN ('SUBMITTED', 'QUERIED', 'FM_QUERIED');
