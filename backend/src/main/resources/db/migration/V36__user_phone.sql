-- V36: user phone numbers (FEAT-36 reminders need recipients; FEAT-42 digest
-- needs the owner's number). Nullable — existing users simply have none until
-- the Owner fills it on the Team page. No backfill: never invent contact data.

ALTER TABLE app_user ADD COLUMN phone VARCHAR(20);
