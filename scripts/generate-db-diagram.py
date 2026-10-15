#!/usr/bin/env python3
"""
Regenerates DATABASE.md (the Mermaid database diagram at the project root) from a live Postgres
that has every Flyway migration applied, so the diagram can never drift from the real schema.

    PGHOST=localhost PGPORT=5432 PGUSER=dams PGDATABASE=dams python3 scripts/generate-db-diagram.py

Run it after adding a migration. Needs `psql` on the PATH.
"""
import os, subprocess, sys, collections

OUT = os.path.join(os.path.dirname(__file__), '..', 'DATABASE.md')

def q(sql):
    r = subprocess.run(['psql', '-At', '-F', '\x1f', '-c', sql], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit(r.stderr)
    return [line.split('\x1f') for line in r.stdout.splitlines() if line]

SKIP = {'flyway_schema_history'}
cols = collections.OrderedDict()   # table -> [(name, type, nullable)]
for t, c, dt, nul in q("""select table_name, column_name, data_type, is_nullable from information_schema.columns
    where table_schema='public' order by table_name, ordinal_position"""):
    if t not in SKIP:
        cols.setdefault(t, []).append((c, dt, nul == 'YES'))

pk = collections.defaultdict(set)
for t, c in q("""select tc.table_name, kcu.column_name from information_schema.table_constraints tc
    join information_schema.key_column_usage kcu on kcu.constraint_name=tc.constraint_name and kcu.table_schema=tc.table_schema
    where tc.table_schema='public' and tc.constraint_type='PRIMARY KEY'"""):
    pk[t].add(c)

uk = collections.defaultdict(set)   # single-column unique indexes only
for t, c in q("""select i.indrelid::regclass::text, a.attname from pg_index i
    join pg_attribute a on a.attrelid=i.indrelid and a.attnum=i.indkey[0]
    where i.indisunique and not i.indisprimary and i.indnatts=1 and i.indpred is null
      and i.indrelid::regclass::text in (select table_name from information_schema.tables where table_schema='public')"""):
    uk[t].add(c)

fks = []   # (child, child_col, parent)
for child, col, parent in q("""select c.conrelid::regclass::text, a.attname, c.confrelid::regclass::text
    from pg_constraint c join pg_attribute a on a.attrelid=c.conrelid and a.attnum=c.conkey[1]
    where c.contype='f' and array_length(c.conkey,1)=1 and c.connamespace='public'::regnamespace"""):
    if child not in SKIP and parent not in SKIP:
        fks.append((child, col, parent))
fkcols = {(t, c) for t, c, _ in fks}
nullable = {(t, c): n for t, cl in cols.items() for c, _, n in cl}

TYPE = {'bigint': 'bigint', 'integer': 'int', 'smallint': 'smallint', 'boolean': 'boolean', 'text': 'text',
        'character varying': 'varchar', 'character': 'char', 'date': 'date', 'numeric': 'numeric',
        'timestamp with time zone': 'timestamptz', 'timestamp without time zone': 'timestamp',
        'jsonb': 'jsonb', 'json': 'json', 'uuid': 'uuid', 'bytea': 'bytea', 'double precision': 'float'}

def entity(t, with_cols=True):
    if not with_cols or t not in cols:
        return f'  {t} {{\n    bigint id PK\n  }}\n' if t in cols else ''
    lines = [f'  {t} {{']
    for c, dt, _ in cols[t]:
        flags = [f for f, on in (('PK', c in pk[t]), ('FK', (t, c) in fkcols), ('UK', c in uk[t])) if on]
        lines.append(f'    {TYPE.get(dt, dt.replace(" ", "_"))} {c}' + (f' {", ".join(flags)}' if flags else ''))
    lines.append('  }')
    return '\n'.join(lines) + '\n'

def rel(child, col, parent):
    card = '}o--o|' if nullable.get((child, col)) else '}o--||'
    return f'  {child} {card} {parent} : "{col}"\n'

def diagram(tables, full):
    members = set(tables)
    rels = [(c, col, p) for c, col, p in fks if c in members or p in members]
    shown = members | {x for c, _, p in rels for x in (c, p)}
    out = 'erDiagram\n'
    for t in sorted(shown):
        out += entity(t, with_cols=(full and t in members))
    for c, col, p in rels:
        out += rel(c, col, p)
    return '```mermaid\n' + out + '```\n'

GROUPS = [
 ('People, branches and the organisation', 'Who can sign in, which branches they see, and the numbering of documents.',
  ['organization', 'platform_setting', 'branch', 'app_user', 'user_role_grant', 'user_branch_access', 'document_sequence']),
 ('Masters (Owner-editable lists)', 'Every dropdown comes from these tables; rows are deactivated, never deleted.',
  ['receive_category', 'receive_business_status', 'receive_business_status_role', 'claim_type', 'settlement_mode', 'bank', 'upi_vpa',
   'expense_category', 'expense_sub_category', 'expense_mode', 'expense_business_status', 'receiver', 'receiver_category']),
 ('Customers, vehicles and job cards', 'The identity of who we deal with, and the job a receipt or expense belongs to.',
  ['customer', 'vehicle', 'job_card', 'claim_close']),
 ('Receipts', 'Money received from a customer: one document per job card, with one line per payment.',
  ['receive_document', 'settlement_line']),
 ('Expenses', 'Money paid out to a vendor (receiver), with one line per payment.',
  ['expense_document', 'expense_line']),
 ('Cash', 'Cash in and out of the drawer, the daily close, and each branch’s opening balance.',
  ['cash_document', 'cash_day_close', 'branch_cash_opening']),
 ('Audit, attachments and AI', 'The trail of who did what, uploaded bills and proofs, and the Ask-DAMS query log.',
  ['audit_event', 'attachment', 'ai_query_log']),
]
covered = {t for _, _, ts in GROUPS for t in ts}
missing = sorted(set(cols) - covered)
if missing:
    GROUPS.append(('Other tables', 'Tables not placed in a group above.', missing))

doc = f'''# DAMS database schema

The whole PostgreSQL schema as Mermaid diagrams (GitHub draws them). It is **generated from the real database** with every
Flyway migration applied — {len(cols)} tables, {len(fks)} foreign keys — so it matches the code. Regenerate it after adding a
migration: `python3 scripts/generate-db-diagram.py` (see the top of that script).

**How to read it:** `PK` primary key · `FK` foreign key · `UK` unique. A solid crow’s-foot line `}}o--||` means *many rows point at
exactly one parent*; `}}o--o|` means the link is optional (the column may be empty). The label on a line is the column that holds
the link. To keep the pictures readable, three kinds of link are not drawn as lines (the column is still listed with `FK`):
links to `organization` (almost every table has an `org_id` — one organisation’s data never mixes with another’s), links to
`app_user` (who created or changed a row), and — in the overview only — links to `branch`.

> Not drawn: `flyway_schema_history` (Flyway’s own bookkeeping).

## 1. The whole picture

Every table and every link, without columns. Start here, then open the group you need below.

'''
# overview: omit organization links for legibility
all_tables = sorted(cols)
overview_rels = [(c, col, p) for c, col, p in fks if p not in ('organization', 'app_user', 'branch')]
linked = sorted({x for c, _, p in overview_rels for x in (c, p)})
alone = [t for t in all_tables if t not in linked]
ov = 'erDiagram\n' + ''.join(f'  {t} {{\n    bigint id PK\n  }}\n' for t in linked) + ''.join(rel(c, col, p) for c, col, p in overview_rels)
doc += '```mermaid\n' + ov + '```\n\n'
doc += ('Tables with no link left to draw here (they only point at `organization`, `branch` or `app_user`, or — for `audit_event` '
        'and `attachment` — at any kind of document by type and id): ' + ', '.join(f'`{t}`' for t in alone) + '.\n\n')

for i, (title, blurb, tables) in enumerate(GROUPS, start=2):
    tables = [t for t in tables if t in cols]
    # group diagrams leave out the organisation and app_user links too
    members = set(tables)
    rels = [(c, col, p) for c, col, p in fks if (c in members or p in members) and p not in ('organization', 'app_user')]
    shown = members | {x for c, _, p in rels for x in (c, p)}
    body = 'erDiagram\n'
    for t in sorted(shown):
        body += entity(t, with_cols=(t in members))
    body += ''.join(rel(c, col, p) for c, col, p in rels)
    doc += f'## {i}. {title}\n\n{blurb}\n\nTables in this group: {", ".join(f"`{t}`" for t in tables)}.\n\n```mermaid\n{body}```\n\n'

open(OUT, 'w').write(doc)
print(f'wrote {os.path.abspath(OUT)} — {len(cols)} tables, {len(fks)} foreign keys, {len(doc)} bytes')
