# DAMS database schema

The whole PostgreSQL schema as Mermaid diagrams (GitHub draws them). It is **generated from the real database** with every
Flyway migration applied — 34 tables, 89 foreign keys — so it matches the code. Regenerate it after adding a
migration: `python3 scripts/generate-db-diagram.py` (see the top of that script).

**How to read it:** `PK` primary key · `FK` foreign key · `UK` unique. A solid crow’s-foot line `}o--||` means *many rows point at
exactly one parent*; `}o--o|` means the link is optional (the column may be empty). The label on a line is the column that holds
the link. To keep the pictures readable, three kinds of link are not drawn as lines (the column is still listed with `FK`):
links to `organization` (almost every table has an `org_id` — one organisation’s data never mixes with another’s), links to
`app_user` (who created or changed a row), and — in the overview only — links to `branch`.

> Not drawn: `flyway_schema_history` (Flyway’s own bookkeeping).

## 1. The whole picture

Every table and every link, without columns. Start here, then open the group you need below.

```mermaid
erDiagram
  ai_query_log {
    bigint id PK
  }
  app_user {
    bigint id PK
  }
  attachment {
    bigint id PK
  }
  audit_event {
    bigint id PK
  }
  bank {
    bigint id PK
  }
  branch {
    bigint id PK
  }
  branch_cash_opening {
    bigint id PK
  }
  cash_day_close {
    bigint id PK
  }
  cash_document {
    bigint id PK
  }
  claim_close {
    bigint id PK
  }
  claim_type {
    bigint id PK
  }
  customer {
    bigint id PK
  }
  document_sequence {
    bigint id PK
  }
  expense_business_status {
    bigint id PK
  }
  expense_category {
    bigint id PK
  }
  expense_document {
    bigint id PK
  }
  expense_line {
    bigint id PK
  }
  expense_mode {
    bigint id PK
  }
  expense_sub_category {
    bigint id PK
  }
  job_card {
    bigint id PK
  }
  organization {
    bigint id PK
  }
  platform_setting {
    bigint id PK
  }
  receive_business_status {
    bigint id PK
  }
  receive_business_status_role {
    bigint id PK
  }
  receive_category {
    bigint id PK
  }
  receive_document {
    bigint id PK
  }
  receiver {
    bigint id PK
  }
  receiver_category {
    bigint id PK
  }
  settlement_line {
    bigint id PK
  }
  settlement_mode {
    bigint id PK
  }
  upi_vpa {
    bigint id PK
  }
  user_branch_access {
    bigint id PK
  }
  user_role_grant {
    bigint id PK
  }
  vehicle {
    bigint id PK
  }
  expense_sub_category }o--|| expense_category : "expense_category_id"
  vehicle }o--|| customer : "customer_id"
  job_card }o--o| customer : "customer_id"
  job_card }o--o| vehicle : "vehicle_id"
  job_card }o--|| receive_category : "category_id"
  job_card }o--|| receive_business_status : "business_status_id"
  receive_document }o--|| job_card : "job_card_id"
  settlement_line }o--|| receive_document : "receive_document_id"
  settlement_line }o--|| settlement_mode : "settlement_mode_id"
  settlement_line }o--o| bank : "bank_id"
  claim_close }o--|| job_card : "job_card_id"
  expense_document }o--o| job_card : "job_card_id"
  expense_document }o--|| receiver : "receiver_id"
  expense_document }o--|| expense_category : "expense_category_id"
  expense_document }o--|| expense_business_status : "business_status_id"
  expense_line }o--|| expense_document : "expense_document_id"
  expense_line }o--|| expense_sub_category : "sub_category_id"
  expense_line }o--|| expense_mode : "expense_mode_id"
  expense_line }o--o| bank : "bank_id"
  cash_document }o--o| bank : "bank_id"
  job_card }o--o| claim_type : "claim_type_id"
  receive_business_status_role }o--|| receive_business_status : "status_id"
  expense_document }o--o| customer : "customer_id"
  expense_document }o--o| vehicle : "vehicle_id"
  receiver_category }o--|| receiver : "receiver_id"
  receiver_category }o--|| expense_category : "expense_category_id"
```

## 2. People, branches and the organisation

Who can sign in, which branches they see, and the numbering of documents.

Tables in this group: `organization`, `platform_setting`, `branch`, `app_user`, `user_role_grant`, `user_branch_access`, `document_sequence`.

```mermaid
erDiagram
  app_user {
    bigint id PK
    bigint org_id FK
    bigint home_branch_id FK
    varchar name
    varchar email UK
    varchar password_hash
    varchar role
    boolean active
    varchar invite_token
    timestamptz invite_expires_at
    timestamptz created_at
  }
  audit_event {
    bigint id PK
  }
  branch {
    bigint id PK
    bigint org_id FK
    varchar name
    varchar code
    boolean active
    timestamptz created_at
  }
  branch_cash_opening {
    bigint id PK
  }
  cash_day_close {
    bigint id PK
  }
  cash_document {
    bigint id PK
  }
  customer {
    bigint id PK
  }
  document_sequence {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    varchar month_key
    varchar doc_type
    int last_seq
  }
  expense_document {
    bigint id PK
  }
  job_card {
    bigint id PK
  }
  organization {
    bigint id PK
    varchar name
    boolean multi_branch_cashier_access
    boolean active
    timestamptz created_at
    boolean accountant_direct_approve_cash
  }
  platform_setting {
    varchar key PK
    varchar value
    timestamptz updated_at
    bigint updated_by FK
  }
  receive_document {
    bigint id PK
  }
  user_branch_access {
    bigint user_id PK, FK
    bigint branch_id PK, FK
  }
  user_role_grant {
    bigint id PK
    bigint org_id FK
    bigint user_id FK
    varchar role
    bigint branch_id FK
    timestamptz created_at
  }
  document_sequence }o--|| branch : "branch_id"
  app_user }o--o| branch : "home_branch_id"
  user_branch_access }o--|| branch : "branch_id"
  job_card }o--|| branch : "branch_id"
  receive_document }o--|| branch : "branch_id"
  expense_document }o--|| branch : "branch_id"
  cash_document }o--|| branch : "branch_id"
  branch_cash_opening }o--|| branch : "branch_id"
  cash_day_close }o--|| branch : "branch_id"
  audit_event }o--o| branch : "branch_id"
  user_role_grant }o--o| branch : "branch_id"
  customer }o--o| branch : "created_branch_id"
```

## 3. Masters (Owner-editable lists)

Every dropdown comes from these tables; rows are deactivated, never deleted.

Tables in this group: `receive_category`, `receive_business_status`, `receive_business_status_role`, `claim_type`, `settlement_mode`, `bank`, `upi_vpa`, `expense_category`, `expense_sub_category`, `expense_mode`, `expense_business_status`, `receiver`, `receiver_category`.

```mermaid
erDiagram
  bank {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean active
    int sort_order
    timestamptz created_at
  }
  cash_document {
    bigint id PK
  }
  claim_type {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean active
    int sort_order
    timestamptz created_at
  }
  expense_business_status {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean active
    int sort_order
    timestamptz created_at
    boolean triggers_claim
    boolean requires_fm_approval
  }
  expense_category {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean active
    int sort_order
    timestamptz created_at
  }
  expense_document {
    bigint id PK
  }
  expense_line {
    bigint id PK
  }
  expense_mode {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean active
    int sort_order
    timestamptz created_at
    boolean requires_bank
    boolean requires_ref
    boolean is_cash
  }
  expense_sub_category {
    bigint id PK
    bigint org_id FK
    bigint expense_category_id FK
    varchar name
    numeric limit_amount
    boolean active
    int sort_order
    timestamptz created_at
  }
  job_card {
    bigint id PK
  }
  receive_business_status {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean active
    int sort_order
    timestamptz created_at
    boolean deprecated
  }
  receive_business_status_role {
    bigint id PK
    bigint org_id FK
    bigint status_id FK
    varchar role
    timestamptz created_at
  }
  receive_category {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean is_claim
    boolean active
    int sort_order
    timestamptz created_at
  }
  receiver {
    bigint id PK
    bigint org_id FK
    varchar name
    varchar phone
    boolean active
    timestamptz created_at
  }
  receiver_category {
    bigint receiver_id PK, FK
    bigint expense_category_id PK, FK
  }
  settlement_line {
    bigint id PK
  }
  settlement_mode {
    bigint id PK
    bigint org_id FK
    varchar name
    boolean requires_bank
    boolean requires_ref
    boolean active
    int sort_order
    timestamptz created_at
    boolean is_cash
  }
  upi_vpa {
    bigint id PK
    bigint org_id FK
    varchar name
    varchar vpa
    boolean active
    int sort_order
    timestamptz created_at
  }
  expense_sub_category }o--|| expense_category : "expense_category_id"
  job_card }o--|| receive_category : "category_id"
  job_card }o--|| receive_business_status : "business_status_id"
  settlement_line }o--|| settlement_mode : "settlement_mode_id"
  settlement_line }o--o| bank : "bank_id"
  expense_document }o--|| receiver : "receiver_id"
  expense_document }o--|| expense_category : "expense_category_id"
  expense_document }o--|| expense_business_status : "business_status_id"
  expense_line }o--|| expense_sub_category : "sub_category_id"
  expense_line }o--|| expense_mode : "expense_mode_id"
  expense_line }o--o| bank : "bank_id"
  cash_document }o--o| bank : "bank_id"
  job_card }o--o| claim_type : "claim_type_id"
  receive_business_status_role }o--|| receive_business_status : "status_id"
  receiver_category }o--|| receiver : "receiver_id"
  receiver_category }o--|| expense_category : "expense_category_id"
```

## 4. Customers, vehicles and job cards

The identity of who we deal with, and the job a receipt or expense belongs to.

Tables in this group: `customer`, `vehicle`, `job_card`, `claim_close`.

```mermaid
erDiagram
  branch {
    bigint id PK
  }
  claim_close {
    bigint id PK
    bigint org_id FK
    bigint job_card_id FK
    numeric final_amount
    boolean overridden
    varchar override_reason
    bigint closed_by FK
    timestamptz closed_at
  }
  claim_type {
    bigint id PK
  }
  customer {
    bigint id PK
    bigint org_id FK
    varchar name
    varchar phone
    timestamptz created_at
    bigint created_branch_id FK
    varchar address
  }
  expense_document {
    bigint id PK
  }
  job_card {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    bigint customer_id FK
    bigint vehicle_id FK
    varchar dbm_id
    varchar invoice_no
    numeric invoice_amount
    bigint category_id FK
    bigint business_status_id FK
    timestamptz created_at
    boolean is_b2b
    varchar gst_no
    bigint claim_type_id FK
    varchar vehicle_no_text
    varchar contact_phone
    varchar chassis_no
  }
  receive_business_status {
    bigint id PK
  }
  receive_category {
    bigint id PK
  }
  receive_document {
    bigint id PK
  }
  vehicle {
    bigint id PK
    bigint org_id FK
    bigint customer_id FK
    varchar vehicle_no
    timestamptz created_at
  }
  vehicle }o--|| customer : "customer_id"
  job_card }o--|| branch : "branch_id"
  job_card }o--o| customer : "customer_id"
  job_card }o--o| vehicle : "vehicle_id"
  job_card }o--|| receive_category : "category_id"
  job_card }o--|| receive_business_status : "business_status_id"
  receive_document }o--|| job_card : "job_card_id"
  claim_close }o--|| job_card : "job_card_id"
  expense_document }o--o| job_card : "job_card_id"
  job_card }o--o| claim_type : "claim_type_id"
  expense_document }o--o| customer : "customer_id"
  expense_document }o--o| vehicle : "vehicle_id"
  customer }o--o| branch : "created_branch_id"
```

## 5. Receipts

Money received from a customer: one document per job card, with one line per payment.

Tables in this group: `receive_document`, `settlement_line`.

```mermaid
erDiagram
  bank {
    bigint id PK
  }
  branch {
    bigint id PK
  }
  job_card {
    bigint id PK
  }
  receive_document {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    bigint job_card_id FK
    varchar document_no
    varchar workflow_status
    boolean settled
    bigint created_by FK
    bigint last_modified_by FK
    timestamptz created_at
    timestamptz submitted_at
    int line_no_seq
  }
  settlement_line {
    bigint id PK
    bigint org_id FK
    bigint receive_document_id FK
    int line_no
    varchar line_id
    date transaction_date
    bigint settlement_mode_id FK
    numeric amount
    numeric original_amount
    bigint bank_id FK
    varchar transaction_ref
    varchar remark
    bigint overridden_by FK
    varchar override_reason
    timestamptz overridden_at
    bigint created_by FK
    timestamptz created_at
  }
  settlement_mode {
    bigint id PK
  }
  receive_document }o--|| branch : "branch_id"
  receive_document }o--|| job_card : "job_card_id"
  settlement_line }o--|| receive_document : "receive_document_id"
  settlement_line }o--|| settlement_mode : "settlement_mode_id"
  settlement_line }o--o| bank : "bank_id"
```

## 6. Expenses

Money paid out to a vendor (receiver), with one line per payment.

Tables in this group: `expense_document`, `expense_line`.

```mermaid
erDiagram
  bank {
    bigint id PK
  }
  branch {
    bigint id PK
  }
  customer {
    bigint id PK
  }
  expense_business_status {
    bigint id PK
  }
  expense_category {
    bigint id PK
  }
  expense_document {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    bigint job_card_id FK
    bigint receiver_id FK
    bigint expense_category_id FK
    bigint business_status_id FK
    varchar document_no
    varchar workflow_status
    boolean over_limit
    bigint created_by FK
    bigint last_modified_by FK
    timestamptz created_at
    timestamptz submitted_at
    int line_no_seq
    varchar customer_name
    varchar vehicle_no
    varchar invoice_no
    varchar dbm_id
    varchar pre_approval_status
    numeric pre_approved_amount
    bigint pre_approved_by FK
    timestamptz pre_approved_at
    timestamptz approval_requested_at
    bigint customer_id FK
    bigint vehicle_id FK
    numeric claim_final_amount
    numeric claim_computed_total
    boolean claim_overridden
    varchar claim_override_reason
    bigint claim_closed_by FK
    timestamptz claim_closed_at
    varchar approval_reason
    varchar approval_comment
  }
  expense_line {
    bigint id PK
    bigint org_id FK
    bigint expense_document_id FK
    int line_no
    varchar line_id
    date transaction_date
    bigint sub_category_id FK
    bigint expense_mode_id FK
    numeric amount
    numeric original_amount
    bigint bank_id FK
    varchar transaction_ref
    varchar remark
    bigint overridden_by FK
    varchar override_reason
    timestamptz overridden_at
    bigint created_by FK
    timestamptz created_at
  }
  expense_mode {
    bigint id PK
  }
  expense_sub_category {
    bigint id PK
  }
  job_card {
    bigint id PK
  }
  receiver {
    bigint id PK
  }
  vehicle {
    bigint id PK
  }
  expense_document }o--|| branch : "branch_id"
  expense_document }o--o| job_card : "job_card_id"
  expense_document }o--|| receiver : "receiver_id"
  expense_document }o--|| expense_category : "expense_category_id"
  expense_document }o--|| expense_business_status : "business_status_id"
  expense_line }o--|| expense_document : "expense_document_id"
  expense_line }o--|| expense_sub_category : "sub_category_id"
  expense_line }o--|| expense_mode : "expense_mode_id"
  expense_line }o--o| bank : "bank_id"
  expense_document }o--o| customer : "customer_id"
  expense_document }o--o| vehicle : "vehicle_id"
```

## 7. Cash

Cash in and out of the drawer, the daily close, and each branch’s opening balance.

Tables in this group: `cash_document`, `cash_day_close`, `branch_cash_opening`.

```mermaid
erDiagram
  bank {
    bigint id PK
  }
  branch {
    bigint id PK
  }
  branch_cash_opening {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    date opening_date
    numeric amount
    bigint set_by FK
    timestamptz created_at
  }
  cash_day_close {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    date close_date
    numeric opening_amount
    numeric computed_closing
    numeric counted_amount
    numeric variance
    varchar variance_remark
    bigint closed_by FK
    timestamptz closed_at
  }
  cash_document {
    bigint id PK
    bigint org_id FK
    bigint branch_id FK
    varchar document_no
    varchar direction
    date transaction_date
    numeric amount
    bigint bank_id FK
    varchar transaction_ref
    varchar remark
    varchar workflow_status
    bigint created_by FK
    bigint last_modified_by FK
    timestamptz created_at
    timestamptz submitted_at
  }
  cash_document }o--|| branch : "branch_id"
  cash_document }o--o| bank : "bank_id"
  branch_cash_opening }o--|| branch : "branch_id"
  cash_day_close }o--|| branch : "branch_id"
```

## 8. Audit, attachments and AI

The trail of who did what, uploaded bills and proofs, and the Ask-DAMS query log.

Tables in this group: `audit_event`, `attachment`, `ai_query_log`.

```mermaid
erDiagram
  ai_query_log {
    bigint id PK
    bigint org_id FK
    bigint user_id FK
    varchar question
    varchar cited_docs
    varchar request_id
    timestamptz created_at
  }
  attachment {
    bigint id PK
    bigint org_id FK
    varchar parent_type
    bigint parent_id
    varchar r2_object_key
    varchar filename
    varchar content_type
    bigint size_bytes
    boolean frozen
    bigint uploaded_by FK
    timestamptz uploaded_at
    varchar comment
  }
  audit_event {
    bigint id PK
    bigint org_id FK
    varchar entity_type
    bigint entity_id
    varchar event_type
    varchar actor_type
    bigint actor_id FK
    text detail
    timestamptz created_at
    bigint branch_id FK
    varchar actor_role
  }
  branch {
    bigint id PK
  }
  audit_event }o--o| branch : "branch_id"
```

