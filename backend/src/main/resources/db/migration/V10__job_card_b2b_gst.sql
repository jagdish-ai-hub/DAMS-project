-- V10: B2B / GST on the job card.
--
-- The Receive Entry screen (cashier-home.html) has a B2C / B2B toggle and, when B2B is
-- chosen, a mandatory GST number. These are case-level facts (they belong to the customer's
-- transaction, not to one settlement document), so they live on job_card next to invoice_no.
--
-- gst_no is required when is_b2b = true — enforced in the service layer (JobCardService), not
-- by a CHECK constraint, so the error message can name the field the way AGENT.md requires.

ALTER TABLE job_card
    ADD COLUMN is_b2b  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN gst_no  VARCHAR(20);
