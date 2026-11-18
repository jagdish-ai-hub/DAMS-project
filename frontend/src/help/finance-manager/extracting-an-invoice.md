# Extract invoice (test tool)

This is a **test tool**. It reads an Eicher dealer PDF and shows you what is on it, as tables. It does **not** create or change anything in DAMS.

## Use it

1. On **Approvals & Claims**, press **Extract invoice (test)**.
2. Choose a PDF — a customer **tax invoice** or a **warranty claim** invoice — and press **Read the PDF**.
3. You get:
   - **Document details** — invoice / claim number, job card number, vehicle, chassis, customer, GST.
   - **Spares**, **Lube** and **Labour** tables — code, HSN/SAC, description, quantity, amount, discount, taxable, tax and total. A warranty claim also shows man-hours × rate, handling and TDS.
   - **Replaced under warranty / AMC / FOC** — items listed with no charge.
   - **Totals printed on the document**, and the HSN summary.
4. Press **Download CSV** to open the lines in Excel.

## Can I trust it?

Look at the line under the title.

- **Green — “All N checks passed”.** Every line was recomputed and every section, the invoice total and the HSN summary match the totals the document prints. A missed or misread row would break one of them.
- **Red — “x of N checks failed”.** Don’t rely on that read. Open **Checks** to see which figure doesn’t add up, and compare it with the PDF.
- **Amber — “Nothing could be checked”.** The file isn’t one of the formats this tool reads.

Long descriptions that wrap over several lines are read from their position on the page; the checks can’t prove the wording, so glance at those.

## Limits

- It reads PDFs that carry real text (the Eicher dealer invoices do). A scanned photo can’t be read.
- Files up to 5 MB. Nothing is stored — the file is deleted as soon as it is read.
