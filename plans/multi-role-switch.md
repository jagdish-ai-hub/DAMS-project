# Multi-role users with a "Switch role" button

## Context
Today each account has exactly one role, and AGENT.md says so outright ("There is no role-switch toggle anywhere", L364-386). Small dealerships need one person to do more than one job. For example, Ajay is an Accountant for OOB/OOR but covers the OOR cash counter.

The goal:
- The Owner can give a user extra roles, each for chosen branches.
- A **Switch role** button next to Help opens a branch picker, then a role picker, and switches the session into that role.
- A banner shows "you are working as X".
- One tap goes back to the user's own role.
- Every entry is still recorded under the real person's name, and the audit trail notes the role they were acting in.

Decisions confirmed by the user:
1. **Owner ticks roles.** Extra roles are assigned per user, each with its own branches, like branch assignment today.
2. **Owner can act as any role at any branch.** The Owner always has the button. AGENT.md's "Owner is read-only" becomes "read-only while acting as Owner".
3. **Scope is the picked branch only.** Acting as Accountant or Cashier at OOR shows and posts OOR only.
4. **Attribution shows name + "as Cashier".** The acting role is stored on audit events and shown in history.

## Step -1: Save this plan in the project
Copy this plan file to `plans/multi-role-switch.md` at the repo root, creating the new `plans/` folder.

## Step 0: Spec first (AGENT.md, then plan.md)
- **AGENT.md, Role hierarchy:** add an "Acting roles" subsection covering:
  - A primary role, plus Owner-granted extra roles (FINANCE_MANAGER org-wide; ACCOUNTANT/CASHIER per branch).
  - The Owner can act as any role at any branch.
  - An acting session is scoped to the one branch picked.
  - Maker-checker is **per person**, not per role. Ajay's cashier entry can never be approved by Ajay acting as Accountant (the existing rule at L57 already keys on user id).
- **AGENT.md, Auth UI notes:** replace "no role-switch toggle anywhere" with: the role switch is server-issued (a new JWT from `/auth/switch-role`), never a client-side choice. The review-close.html demo toggle is still not built.
- **plan.md:**
  - Add a rev 55 entry.
  - Locked Decisions: `UserBranchAccess` stays ACCOUNTANT-primary only; add a `user_role_grant` row.
  - Entity List: add UserRoleGrant.
  - Add migration V33 and the new endpoints.

## Backend

### Migration `V33__user_role_grant_and_actor_role.sql`
Flyway runs through CI, not locally.
- Table `user_role_grant`:
  - Columns: `id BIGSERIAL PK, org_id FK, user_id FK app_user, role VARCHAR(50), branch_id BIGINT NULL FK branch`.
  - `CHECK (role IN ('FINANCE_MANAGER','ACCOUNTANT','CASHIER'))`.
  - `CHECK ((role='FINANCE_MANAGER') = (branch_id IS NULL))`.
  - `UNIQUE NULLS NOT DISTINCT (user_id, role, branch_id)`.
  - Index on `user_id`.
- `audit_event.actor_role VARCHAR(50) NULL`. It is filled **only while acting in a switched role**; null means the user's own role.

### Entity and repository
- `user/entity/UserRoleGrant.java`, carrying `orgFilter` like the other org tables.
- `UserRoleGrantRepository`: `findByUserId`, `deleteByUserId`, and `existsByUserIdAndRoleAndBranchId` (plus an FM variant with a null branch).

### JWT and security context
The key idea: the JWT `role` claim becomes the **acting role**. `@PreAuthorize`, `branchScope.currentRole()` and every frontend `user.role` check then follow the switch with no changes.
- **`auth/util/JwtUtil.java`:** add claims `primaryRole` and `actingBranchId`, a new `generateAccessToken` overload, and getters. If `primaryRole` is absent (tokens issued before this change), it equals `role`.
- **`config/JwtConfig.java:63`:** `auth.setDetails(new ActingDetails(primaryRole, actingBranchId))`. `ActingDetails` is a new record in `common/security`.
- **New `common/security/ActingRoleFilter`** runs after TenantFilter:
  - Only when `role != primaryRole`, it re-checks the grant in the DB. An Owner is always allowed if the branch is in the org.
  - If the grant has been revoked, it returns 401 with "Your acting role was removed — sign in again". This keeps the existing "read fresh from DB" principle; the axios 401 handler already sends the user to login.
  - Register it in `SecurityConfig.java:79-80`.
- **`common/security/BranchScope.java`:**
  - `allowedBranchIds()` switches on `currentRole()` (acting), not `user.getRole()`. When switched, ACCOUNTANT returns `Set.of(actingBranchId)`; CASHIER returns `Set.of(actingBranchId)`, with the org multi-branch toggle still applying.
  - Add `isActing()`, `actingBranchId()`, and `cashierBranchId(AppUser me)`, which returns the acting branch when switched and otherwise `me.getHomeBranchId()`.

### Fix the rule everywhere
Replace every `me.getRole()` check with `branchScope.currentRole()`, and every `me.getHomeBranchId()` with `branchScope.cashierBranchId(me)`:
- `cash/service/CashPostingGuard.java` (38-71)
- `cash/service/CashDocumentService.java:95`
- `cash/service/CashCloseService.java:144`
- `expense/service/ExpensePostingGuard.java` (45-55)
- `expense/service/ExpenseDocumentService.java:192`
- `receive/service/ReceivePaymentGuard.java` (51-77)
- `review/service/ReviewGuard.java` (40, 49)
- `jobcard/service/ClaimCloseService.java:176`
- `jobcard/service/JobCardService.java` (297-301)

Leave `UserService` alone, because it reads the *stored* role on purpose. `created_by` stays as the user id, so Ajay's name shows.

### Switch endpoints (`AuthController` / `AuthService`)
- `GET /api/v1/auth/switch-options` returns `{ primaryRole, branches: [{branchId, code, name, roles:[...]}] }`:
  - An Owner gets every active branch × {FINANCE_MANAGER, ACCOUNTANT, CASHIER}.
  - Everyone else gets their grants.
  - FINANCE_MANAGER is listed under every branch and labelled org-wide.
- `POST /api/v1/auth/switch-role {role, branchId}`:
  - Validates the grant.
  - Issues a new token through the refactored `buildLoginResponse`.
  - Records the audit event `ROLE_SWITCHED` (new value in `EventType`) with from/to role and branch.
  - `role == primaryRole` means "switch back" and restores the normal primary token.
- `LoginResponse` gains `primaryRole`, `actingBranchId` and `canSwitchRole`. `canSwitchRole` is true for an Owner, or when the user has at least one grant.

### Audit
- `audit/service/AuditService.java`: `recordUserEvent` sets `actorRole = branchScope.isActing() ? currentRole() : null`.
- `DocumentHistoryService` and `OverrideAuditService` return `actorRole` in their DTOs.

### User management
- `UserRequest` / `UserResponse` gain `roleGrants: [{role, branchIds[]}]`. The response also gains a label such as "+ Cashier (OOR)".
- `UserService.create/update` gets a new `resolveRoleGrants` that rebuilds the rows after the existing branch logic:
  - Grants are not allowed for OWNER or SUPER_ADMIN.
  - A grant can't be for the user's primary role.
  - ACCOUNTANT and CASHIER grants need at least one branch; FINANCE_MANAGER needs none.
  - Every branch must be in the org.
- Changing the primary role drops any grant for the new primary role.

## Frontend
- **`auth/AuthContext.tsx`:** `AuthUser` adds `primaryRole`, `actingBranchId`, `canSwitchRole` and derived `isActing`, parsed from the JWT. `role` keeps meaning "acting role", so `NAV_BY_ROLE`, `HelpButton` and the page gates switch automatically.
- **`api/auth.ts`:** `switchOptions()` and `switchRole(role, branchId)`.
- **New `shell/RoleSwitchButton.tsx` and `shell/RoleSwitchModal.tsx`:**
  - The button sits beside "? Help" in `shell/AppShell.tsx` (206-216) and in the mobile drawer (247-287), and is shown when `canSwitchRole`.
  - When acting, the modal leads with "Return to Accountant (your role)".
  - Step 1 picks a branch; step 2 picks a role at that branch.
  - On success it calls `login(token, name)` and then `navigate('/app')`, which lands on the new role's home page. The existing role→home logic is in AppShell 120-130.
  - It reuses `Modal` and `primaryBtn`/`ghostBtn` from `shell/ui.tsx`.
- **New `shell/ActingRoleBanner.tsx`:**
  - Rendered just before `<main>` (AppShell ~L299).
  - An amber strip using the `approvalBanner` tone from `NewExpensePage.tsx:958`, with the `dams-anim-notice` animation.
  - Text: "You're working as **Cashier · OOR** — entries are recorded under Ajay Kumar", followed by a **Switch back to Accountant** button.
- **`shell/AccountMenu.tsx`:** the role line reads "Accountant · acting as Cashier (OOR)". Move the duplicated `ROLE_LABEL` into one shared export used by `AccountMenu.tsx` and `SettingsPage.tsx`.
- **`owner/TeamAndBranchesPage.tsx` `UserModal`:**
  - An "Allow role switching" checkbox reveals one row for each role other than the primary.
  - Each row has its own checkbox; ACCOUNTANT and CASHIER rows get a branch checkbox list (the same pattern as L302-320). The FM row shows an "all branches" note.
  - For a primary Owner it shows "Owners can always switch".
  - The users table shows extra-role badges.
  - Styling matches the `.roleopt` / `.branchcheck` look in `owner-dashboard.html`.
- **History and Override Audit views:** show "Ajay Kumar · as Cashier" when `actorRole` is set.

## Verification
1. **Backend tests**, using the bundled Maven path from memory:
   - BranchScope scoping while acting.
   - Guards accept an acting cashier at the picked branch and reject other branches.
   - Switch rejected without a grant.
   - Owner can switch into any role.
   - Maker-checker still blocks Ajay approving his own entry.
   - A revoked grant returns 401.
2. **Frontend:** run `npm run build`, then grep `dist/assets/*.js` for "Switch role" and "entries are recorded under". Check the button is not hidden by an inline style on mobile.
3. **After CI applies V33,** walk the flow on the live app:
   1. The Owner grants accountant@jjmotors.demo the Cashier role at OOR.
   2. Log in as the accountant, then Switch role → OOR → Cashier. The banner shows and the nav switches to cashier.
   3. Create a cash receipt.
   4. Neon `run_sql`: the document has `created_by` = the accountant's id, and `audit_event.actor_role = 'CASHIER'`.
   5. Switch back. That receipt is in the review queue but cannot be approved by the same person.
   6. The Owner revokes the grant while the accountant is acting; the next request returns 401 and sends them to login.
