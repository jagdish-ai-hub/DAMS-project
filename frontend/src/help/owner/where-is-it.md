# Where is it — and who is holding it?

You don’t move documents along — the cashier, accountant and finance manager do. But you can always see **where each one is and who is holding it**.

**How to read the pictures:** a **yellow** box is a step you can watch (none are yours to press). Green is finished. Dashed lines are queries and rejections.

## Receipts

Money received from a customer.

```mermaid
flowchart TD
  A["Draft<br/>Cashier"] -->|"Submit"| B["Submitted<br/>Accountant checks"]
  B -->|"Verify"| C["Verified<br/>Finance Manager decides"]
  C -->|"Approve"| D["Approved ✓"]
  C -->|"Warranty / AMC / CG claim:<br/>Close Claim + final amount"| D
  B -.->|"Query"| Q["Queried<br/>back to the Cashier"]
  Q -->|"Fix and resubmit"| B
  C -.->|"Query"| F["Queried by Finance<br/>back to the Accountant"]
  F -->|"Fix and resend"| C
  B -.->|"Reject"| X["Rejected"]
  C -.->|"Reject"| X
  classDef here fill:#FDE68A,stroke:#B45309,stroke-width:3px,color:#1F3864
  classDef done fill:#DCFCE7,stroke:#1E7F4F,color:#14532D
  classDef bad fill:#FEE2E2,stroke:#B91C1C,color:#7F1D1D
  class D done
  class X bad
```

## Expenses

Money paid out.

### Getting it submitted

```mermaid
flowchart TD
  A["Draft<br/>Cashier"] -->|"Within limit: Submit"| B["Submitted<br/>Accountant checks"]
  A -->|"Over limit or needs approval:<br/>Send for Review + reason"| R["Waiting for<br/>Finance Manager approval"]
  R -->|"Approve (+ optional comment)"| A2["Approved to submit<br/>Cashier submits"]
  A2 -->|"Submit"| B
  R -.->|"Query"| A
  classDef here fill:#FDE68A,stroke:#B45309,stroke-width:3px,color:#1F3864
  classDef done fill:#DCFCE7,stroke:#1E7F4F,color:#14532D
  classDef bad fill:#FEE2E2,stroke:#B91C1C,color:#7F1D1D
```

### After it is submitted

```mermaid
flowchart TD
  B["Submitted<br/>Accountant checks"] -->|"Verify:<br/>in limit"| Z["Closed ✓"]
  B -->|"Verify:<br/>needs Finance"| C["Verified<br/>Finance Manager approves"]
  C -->|"Approve"| Z
  B -->|"Verify:<br/>Transfer to Claim"| K["Verified<br/>Finance Manager closes the claim"]
  K -->|"Close Claim"| Z
  classDef here fill:#FDE68A,stroke:#B45309,stroke-width:3px,color:#1F3864
  classDef done fill:#DCFCE7,stroke:#1E7F4F,color:#14532D
  classDef bad fill:#FEE2E2,stroke:#B91C1C,color:#7F1D1D
  class Z done
```

At any step it can be sent back with a **query**: the accountant’s query goes to the cashier; the finance manager’s query goes to the accountant. A **rejected** expense is finished.

## Cash in and out

Money moved in or out of the drawer.

```mermaid
flowchart TD
  A["Draft<br/>Cashier"] -->|"Submit"| B["Submitted<br/>Accountant checks"]
  B -->|"Verify"| C["Verified<br/>Finance Manager decides"]
  C -->|"Approve"| D["Approved ✓"]
  B -.->|"Query"| Q["Queried<br/>back to the Cashier"]
  Q -->|"Fix and resubmit"| B
  C -.->|"Query"| F["Queried by Finance<br/>back to the Accountant"]
  F -->|"Fix and resend"| C
  B -.->|"Reject"| X["Rejected"]
  C -.->|"Reject"| X
  classDef here fill:#FDE68A,stroke:#B45309,stroke-width:3px,color:#1F3864
  classDef done fill:#DCFCE7,stroke:#1E7F4F,color:#14532D
  classDef bad fill:#FEE2E2,stroke:#B91C1C,color:#7F1D1D
  class D done
  class X bad
```

### Closing the day

```mermaid
flowchart TD
  A["Day open<br/>Cashier records cash in and out"] -->|"Count the drawer and close the day"| B["Day closed ✓"]
  B -->|"Owner reopens, with a reason"| A
  classDef here fill:#FDE68A,stroke:#B45309,stroke-width:3px,color:#1F3864
  classDef done fill:#DCFCE7,stroke:#1E7F4F,color:#14532D
  classDef bad fill:#FEE2E2,stroke:#B91C1C,color:#7F1D1D
  class B done
```

## Your steps

- Watch, don’t push: the people on each step press the buttons.
- You **can** change an expense’s status, and **reopen a closed cash day** (with a reason).

## Where to find it

- **Dashboard → Stuck with whom** — counts for the Cashier, the Accountant and the Finance Manager. Click a tile to see the entries and how long they have waited.
- **Expenses** — every expense in every branch, with its status.
- **Dashboard → Awaiting approval** — what is submitted or verified but not yet final.

## What every status means

| What the status says | Who has it now | What happens next |
|---|---|---|
| **Draft** | Cashier | The cashier finishes it and presses **Submit** (or **Send for Review** if an expense needs approval first). |
| **Waiting for FM approval** *(expense)* | Finance Manager | They **Approve** (with an optional comment) or **Query** it back. Then the cashier submits. |
| **Submitted** | Accountant | They **Verify**, **Query** or **Reject**. |
| **Queried** | Cashier | The cashier reads the note, fixes it and resubmits. |
| **Verified** | Finance Manager | They **Approve**, **Query** or **Reject**. An in-limit expense never waits here — it closes when verified. A claim waits here for **Close Claim**. |
| **Queried by Finance Manager** | Accountant | The accountant fixes it and resends it straight to the Finance Manager. |
| **Approved** | Nobody — it's done | Locked. A new payment added later reopens a receipt for review. |
| **Closed** *(expense)* | Nobody — it's done | Locked for good. |
| **Rejected** | Nobody — it's finished | The entry doesn't stand. |

**Reading “stuck”:** if one person holds many entries for days, ask them. *Approved* and *Closed* entries are held by nobody — they are finished.
