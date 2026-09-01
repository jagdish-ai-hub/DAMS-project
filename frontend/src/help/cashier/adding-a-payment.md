# Adding a payment

When a customer pays more against a job that **already has a receipt**, you add a line to that receipt. You never start a second receipt for the same job — one job, one receipt, however many payments come in over time.

## When you'd do this

A customer left a ₹5,000 advance last week; today they clear the remaining ₹8,000. The job already has receipt `OOR-AUG26-R-001`, so today's ₹8,000 goes on as line 2 of that same receipt.

## Steps

1. Find the customer and open their history.
2. Look at the **Balance** on the job card row. If money is still due, an **Add Payment** button is there.
3. Press **Add Payment**. The box tells you the balance due and which line it will create (e.g. `OOR-AUG26-R-001-L2`).
4. Enter the **Date**, **Settlement Mode** and **Amount**. The amount defaults to the full balance — change it if they're paying part.
5. Fill **Bank / Transaction ID** if the mode asks for them.
6. Attach the payment proof if you have it.
7. Press **Add Payment**.

The balance updates immediately. When it reaches zero the receipt **settles itself** — there's no "close" button to press.

## If there's no Add Payment button

- The balance is already **zero** — nothing left to pay, or
- the job card belongs to **another branch** — only that branch's cashier can take the payment.
