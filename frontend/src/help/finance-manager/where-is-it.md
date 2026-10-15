# Where is it — and what’s next?

You are the **last step**. Nothing is final until you approve it — and you also decide expense requests *before* the cashier submits them.

**How to read the pictures:** a **yellow** box is a step that is **yours**. Green is finished. Dashed lines are queries and rejections.

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
  class C here
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
  class R here
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
  class C,K here
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
  class C here
```

## Your steps

- **Waiting for FM approval** *(expense)* — read **why the cashier needs it**, then **Approve** (add a comment if you like) or **Query**.
- **Verified** — **Approve**, **Query** (back to the accountant) or **Reject**.
- **Transfer to Claim** — you act **twice**: first approve the request (if it was over its limit), then — **after the accountant verifies it — come back to Open claims and press Close Claim** with the amount recovered.
- **Warranty / AMC / CG receipts** — **Close Claim** approves and closes in one step.

## Where to find it

- **Approvals & Claims** — approval requests at the top, then everything awaiting your decision, and **Open claims**.
- You see all branches.

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

**Not for you:** an ordinary in-limit expense never reaches you — the accountant’s verify closes it.
