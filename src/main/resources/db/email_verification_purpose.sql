-- Email change confirmation tokens (run once if ddl-auto does not add columns).
ALTER TABLE email_verification_tokens
    ADD COLUMN IF NOT EXISTS purpose VARCHAR(30) NOT NULL DEFAULT 'ACCOUNT_ACTIVATION',
    ADD COLUMN IF NOT EXISTS pending_email VARCHAR(150) NULL;
