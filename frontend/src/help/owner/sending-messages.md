# Sending messages

DAMS sends templated WhatsApp/SMS messages: due reminders, renewal nudges, payment confirmations, and your nightly digest. Templates use `{{variables}}` — there are no free-typed blasts, so every message is reviewable copy.

## Steps

1. Open **Messages** to see the templates and the full send log.
2. To send manually, pick a template, enter the phone, fill the variables as JSON, and press **Send**.
3. Every attempt lands in the log as SENT, LOGGED (no provider configured yet — recorded, not lost) or FAILED with the reason.

## Good to know

- Missing variables render empty instead of crashing — but check the log when a message reads oddly.
- Customers without a phone on file can't be messaged. Fill numbers on the customer record first.
