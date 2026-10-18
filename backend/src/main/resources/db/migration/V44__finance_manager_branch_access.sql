-- Preserve existing Finance Manager access while moving it to explicit branch assignments.
-- Existing primary Finance Managers previously had access to every branch in their org.
INSERT INTO user_branch_access (user_id, branch_id)
SELECT u.id, b.id
FROM app_user u
JOIN branch b ON b.org_id = u.org_id
WHERE u.role = 'FINANCE_MANAGER'
ON CONFLICT DO NOTHING;

-- Existing extra Finance Manager grants were org-wide; expand each to all org branches.
INSERT INTO user_role_grant (org_id, user_id, role, branch_id)
SELECT g.org_id, g.user_id, g.role, b.id
FROM user_role_grant g
JOIN branch b ON b.org_id = g.org_id
WHERE g.role = 'FINANCE_MANAGER'
  AND g.branch_id IS NULL
ON CONFLICT DO NOTHING;

DELETE FROM user_role_grant
WHERE role = 'FINANCE_MANAGER'
  AND branch_id IS NULL;

ALTER TABLE user_role_grant DROP CONSTRAINT user_role_grant_branch_chk;
ALTER TABLE user_role_grant ALTER COLUMN branch_id SET NOT NULL;
