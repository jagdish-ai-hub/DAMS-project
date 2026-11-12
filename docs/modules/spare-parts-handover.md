# Spare Parts: handover (read this first on the other PC)

Branch: `feat/spare-parts-module`. Spec: [spare-parts.md](spare-parts.md). Plan entry: plan.md rev 91.
Everything below is **built and tested locally** except the three steps at the bottom, which need the Neon connection
and/or Docker that this PC did not have.

## What is already done

| Area | State |
|---|---|
| AGENT.md "Modules and module roles", `docs/modules/spare-parts.md`, plan.md rev 91 | written |
| Migration `V47__spare_parts_module.sql` (add-only) | written, **never run against a database** |
| Backend `com.dams.spareparts` (entities, repos, services, controller), role/switch changes | compiles; 512 backend tests pass |
| Frontend (screens, shell menu, Super Admin switch, Team roles, Masters grid, help articles) | `tsc`, `lint`, 129 tests pass; production build checked |
| Screenshots | `Desktop\Spare parts screenshots\index.html` (API was mocked with the owner's sheet data) |

## NOT done: do these in order

### 1. Run the Docker-based Postgres test (this is the proof the migration is right)

`MigrationV47Test` boots the whole app on real Postgres (Flyway runs V1..V47, then Hibernate `ddl-auto: validate` checks every new
entity against the new tables) and checks the things H2 cannot: `INSERT ... ON CONFLICT DO NOTHING` (the stock-row creation) and the
table constraints. It is skipped without Docker. Run it, or let CI run it on the PR:

```
cd backend
mvn.cmd -o test -Dtest=MigrationV47Test      # needs the Docker daemon running
```
(Maven path: see memory `build-and-run-commands`: the IntelliJ-bundled `mvn.cmd`.) **If it fails, fix before anything else.**
Do not skip it: a mapping mismatch would stop the live backend from starting.

### 2. Practice run of V47 on a Neon COPY of production (not on production)

1. Create a Neon branch of production for this (NOT with "automatically delete after 1 day" ticked off by default: untick it, or
   delete it yourself afterwards). Do **not** point the app at the production branch to "see if it works".
2. Run the backend against that branch (`application-local.yml`, `.env`), let Flyway apply V47, check it starts and `/actuator/health` is UP.
3. Check: `select count(*) from organization where spare_parts_enabled` is **0**; the two new CHECK lists exist; existing rows unchanged.
4. Memory note `no-local-migrations`: CI/CD applies migrations to production. Never run against production just to migrate.

### 3. Deploy with the switch OFF, then turn it on for the demo organization only

1. Merge via PR (CI must be green, including the Docker test). Deploy as usual. Everyone sees **no change** (switch is off).
2. Super Admin -> Organizations -> Spare Parts switch **on for `OORIBA MOTORS DEMO` only** (a confirm dialog appears).
3. In the demo org, as Owner: Team & Branches -> add an **Inventory Manager** (all branches) and a **Store Keeper** (one branch).
4. As the Inventory Manager: Spare Parts -> Upload & add -> upload `C:\Users\pattn\Desktop\Spare parts.xlsx` (try **Add** first).
   The sheet has 23 parts in OOJ/OOB/OOR. Note: the demo org's branch codes must match (`OOJ`, `OOB`, `OOR`), else rows are refused
   with the list of valid codes. This is correct behaviour.
5. Walk the flow: search -> bill (job card `4010615068`) -> ask another branch -> Sent -> Received -> Owner overview -> Masters grid.
6. Only after the client has tried it in the demo org: switch on for `OORIBA MOTORS`.

Then run `scripts/generate-db-diagram.py` against the migrated database to refresh `DATABASE.md` (CLAUDE.md asks for this after
every migration).

## Things worth knowing (decisions and traps)

- **Roles are branch-assigned like an Accountant** (`usesBranchAccessRows()`); the Owner is **view-only** and acts through Switch role.
  For the first all-branch upload someone must hold Inventory Manager at all three branches (an upload may only touch the
  uploader's own branches).
- **Quantity is `CLOSING QTY`** of the sheet (falls back to `OPENING QTY`). `BILLED QTY` / `JOB CARD` columns are kept in the ledger
  note only. The sheet's job card numbers are numbers in Excel (`4.01E9`) and are restored to plain digits.
- **Claim check** reads `job_card.dbm_id` (Eicher's number) read-only through `InvJobCardLookupRepository`; a number DAMS does not know is accepted.
- **No audit row for the Super Admin switch** (audit rows need an organization context): it is written to the server log
  (`Org Spare Parts module switched`). The Owner's grid changes ARE audited (`SPARE_PERMISSIONS_CHANGED`).
- **Organization delete** is refused once an organization has any stock history (like money data).
- **JVM footprint:** no new library. `.xlsx` is read with the JDK (zip + StAX, XXE off, size capped). Rev 90's Metaspace cap is why POI was not used.
- **Frontend traps hit while building:** an inline `width` beats a responsive class (the Overview layout bug: now a CSS grid); a function
  returned from `beforeEach` is run as cleanup by Vitest (a test bug); jsdom has no `scrollIntoView` (stub it in tests that show an `ErrorBanner`).
- **Phones:** the stock table scrolls sideways for the branch columns (part name stays fixed). A card layout for phones is a possible next step.

## Open questions for the client (also in the spec)

1. The full parts list (the sample has 23 parts in 3 branches; the BRD says 5 branches).
2. High-value purchase approval: they mentioned it; it is **not in the BRD**. Local-vendor purchases already have the DAMS expense
   approval limit; ordering from Eicher is out of BRD scope. Ask which they mean before building anything.
3. Whether "Sent" needs a proof/reference number.
