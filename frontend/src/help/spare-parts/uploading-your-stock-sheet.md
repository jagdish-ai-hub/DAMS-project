# Uploading your stock sheet

*For Inventory Managers (or anyone the Owner has allowed to upload).*

You can load stock from your Excel sheet instead of typing parts one by one.

## What the sheet needs

A header row with these columns, in any order. Your usual sheet already has them:

`BRANCH` · `PART ID` · `PART DESCRIPTION` · `CLOSING QTY` (or `OPENING QTY`) · `STORAGE BIN` · `PRICE PER UNIT`

A title line above the headers, a TOTAL line at the bottom, and extra columns such as `BILLED QTY` or `JOB CARD` are all fine. The quantity read is **CLOSING QTY**, because that is what is in the godown now.

## Upload

1. Open **Spare Parts → Upload & add**.
2. **Choose what the upload should do. You are asked every time:**
   - **Add to stock** — adds each row's quantity to what the branch already holds. Use it for parts that came in.
   - **Set stock to the file** — makes each listed part match the file. Parts that are **not** in the file are left alone. Use it to correct counts.
3. Choose the file and click **Check the file**.
4. Read the table. Each row shows what it would do (`Now → after`) or why it cannot be posted, in red. Nothing has changed yet.
5. Click **Post N rows**.

Rows with a problem are skipped; the good rows still post. Fix the skipped ones in the file and upload again if they matter.

## Common messages

- **"'XYZ' is not a branch of this organization"** — the branch code does not match a branch in DAMS. Fix the code in the sheet.
- **"You can only change stock at your own branches"** — you may upload only for the branches assigned to you.
- **"Quantity 2.5 is not a whole number"** — parts are counted in whole numbers.
- **"Description is needed because … is a new part"** — a part DAMS has not seen before needs a description.
- **"Same branch and part as row 3"** — list each part once per branch.

## Add a single part

Below the upload, **Add one part by hand** adds a part and its opening quantity at your branch.
