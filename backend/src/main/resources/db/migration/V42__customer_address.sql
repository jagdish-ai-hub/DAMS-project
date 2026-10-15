-- rev 77: an optional address on a customer. No form asks for it yet; the Owner fills it in
-- from Masters -> Customers.
ALTER TABLE customer ADD COLUMN address VARCHAR(500);
