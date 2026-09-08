-- V26: messaging (FEAT-36) — provider-seamed outbound messages + full log.
--
-- DAMS's only outbound channel was a log-stub email. This adds templates
-- (org-owned, `{{variable}}` placeholders) and an append-only send log.
-- Delivery goes through a MessageSender seam: the default logs (like
-- LoggingEmailService) until a WhatsApp/SMS provider is configured — the
-- same pattern as StorageService for R2. Every send attempt leaves a row,
-- so reminders are auditable, never fire-and-forget.

CREATE TABLE message_template (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    code        VARCHAR(60)   NOT NULL,
    channel     VARCHAR(10)   NOT NULL DEFAULT 'WHATSAPP',
    body        VARCHAR(1000) NOT NULL,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT message_template_channel_chk CHECK (channel IN ('WHATSAPP','SMS')),
    CONSTRAINT message_template_org_code_uq UNIQUE (org_id, code)
);

CREATE TABLE message_log (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    channel       VARCHAR(10)   NOT NULL,
    to_phone      VARCHAR(20)   NOT NULL,
    template_code VARCHAR(60),
    body          VARCHAR(1000) NOT NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'QUEUED',
    related_type  VARCHAR(30),
    related_id    BIGINT,
    error         VARCHAR(500),
    created_by    BIGINT        REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT message_log_status_chk CHECK (status IN ('QUEUED','SENT','LOGGED','FAILED'))
);

CREATE INDEX message_log_org_created_idx ON message_log (org_id, created_at DESC);

-- Default templates for every existing org; new orgs get them at onboarding
-- (see MessageTemplateService#ensureDefaults).
INSERT INTO message_template (org_id, code, channel, body)
SELECT o.id, t.code, t.channel, t.body
FROM organization o
CROSS JOIN (VALUES
    ('payment_received', 'WHATSAPP',
     'DAMS: received Rs.{{amount}} from {{name}} ({{docNo}}). Balance due: Rs.{{balance}}. Thank you!'),
    ('due_reminder', 'WHATSAPP',
     'DAMS:Dear {{name}}, Rs.{{amount}} ({{docNo}}) was due on {{dueDate}}. Please pay at the earliest. {{branch}}'),
    ('renewal_reminder', 'WHATSAPP',
     'DAMS:Dear {{name}}, your {{vehicleNo}} AMC/service is due around {{dueDate}}. Book your slot: {{branch}}.'),
    ('owner_digest', 'WHATSAPP',
     'DAMS daybook {{date}}: collected Rs.{{collections}}, spent Rs.{{expenses}}, net Rs.{{net}}. {{pending}} awaiting review, {{unclosed}} branches unclosed.')
) AS t(code, channel, body)
WHERE NOT EXISTS (
    SELECT 1 FROM message_template m WHERE m.org_id = o.id AND m.code = t.code
);
