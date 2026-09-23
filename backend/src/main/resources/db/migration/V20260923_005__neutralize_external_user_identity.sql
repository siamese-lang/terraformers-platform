ALTER TABLE users
    ADD COLUMN external_identity_provider VARCHAR(64) NULL;

ALTER TABLE users
    ADD COLUMN external_identity_subject VARCHAR(128) NULL;

UPDATE users
SET external_identity_provider = 'cognito',
    external_identity_subject = cognito_sub
WHERE external_identity_provider IS NULL
  AND external_identity_subject IS NULL
  AND cognito_sub IS NOT NULL;

ALTER TABLE users
    MODIFY COLUMN external_identity_provider VARCHAR(64) NOT NULL;

ALTER TABLE users
    MODIFY COLUMN external_identity_subject VARCHAR(128) NOT NULL;

ALTER TABLE users
    MODIFY COLUMN cognito_sub VARCHAR(128) NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_external_identity
        UNIQUE (external_identity_provider, external_identity_subject);
