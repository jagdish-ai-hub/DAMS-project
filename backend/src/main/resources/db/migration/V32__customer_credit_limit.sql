-- V32: B2B credit limits (FEAT-46) — a ceiling before the catastrophic default.
--
-- One nullable limit per customer. NULL = no limit (existing behaviour).
-- Enforcement is warn-first in v1: posting is never blocked, but the
-- counter sees exposure-vs-limit at history time and the defaulter view
-- ranks breached customers first. A hard block needs an Owner override path
-- and is deliberately left for v2.

ALTER TABLE customer ADD COLUMN credit_limit NUMERIC(14,2);
