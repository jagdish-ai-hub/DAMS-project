-- V28: claim next-action tracker (FEAT-38) — every open claim has an owner + date.
--
-- Aging buckets show OLD claims; this table records the NEXT STEP so claims
-- die from neglect less often. One or more open actions per claim job card;
-- completing them is explicit (done_at), deleting them is not allowed —
-- history of what was chased matters at write-off time.

CREATE TABLE claim_action (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    job_card_id   BIGINT        NOT NULL REFERENCES job_card(id)     ON DELETE RESTRICT,
    action        VARCHAR(500)  NOT NULL,
    owner_user_id BIGINT        REFERENCES app_user(id) ON DELETE RESTRICT,
    due_date      DATE          NOT NULL,
    done_at       TIMESTAMPTZ,
    created_by    BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX claim_action_open_idx ON claim_action (org_id, due_date)
    WHERE done_at IS NULL;
CREATE INDEX claim_action_job_idx ON claim_action (org_id, job_card_id);
