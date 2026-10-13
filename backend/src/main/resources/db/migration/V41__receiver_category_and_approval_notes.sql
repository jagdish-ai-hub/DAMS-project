-- rev 76: vendors are mapped to the expense categories they supply (many per vendor), and an
-- expense carries the cashier's reason for needing Finance Manager approval plus the Finance
-- Manager's optional comment when they approve.

CREATE TABLE receiver_category (
    receiver_id         BIGINT NOT NULL REFERENCES receiver(id)         ON DELETE CASCADE,
    expense_category_id BIGINT NOT NULL REFERENCES expense_category(id) ON DELETE CASCADE,
    PRIMARY KEY (receiver_id, expense_category_id)
);
CREATE INDEX receiver_category_category_idx ON receiver_category (expense_category_id);

-- Vendors already used on an expense start out mapped to the categories they were used under.
INSERT INTO receiver_category (receiver_id, expense_category_id)
SELECT DISTINCT receiver_id, expense_category_id FROM expense_document
ON CONFLICT DO NOTHING;

ALTER TABLE expense_document
    ADD COLUMN approval_reason  VARCHAR(1000),
    ADD COLUMN approval_comment VARCHAR(1000);
