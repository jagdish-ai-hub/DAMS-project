-- rev 84: an optional GST No on a customer, kept by the Owner in Masters -> Customers. Standalone for now:
-- the job card keeps its own gst_no and nothing copies this onto it.
ALTER TABLE customer ADD COLUMN gst_no VARCHAR(15);
