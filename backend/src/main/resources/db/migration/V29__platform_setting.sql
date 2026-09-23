-- rev 50: platform-wide settings owned by Super Admin. Deliberately NOT org-scoped (no org_id),
-- so the tenant filter never touches it. Only key today: ui.font (the app typeface).

CREATE TABLE platform_setting (
    key        VARCHAR(64)  PRIMARY KEY,
    value      VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by BIGINT       NULL REFERENCES app_user(id) ON DELETE SET NULL
);

INSERT INTO platform_setting (key, value) VALUES ('ui.font', 'plex');
