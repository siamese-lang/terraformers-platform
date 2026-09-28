ALTER TABLE analysis_jobs
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN lease_expires_at TIMESTAMP(6) NULL,
    ADD COLUMN claim_generation BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN result_object_intent_bucket VARCHAR(255) NULL,
    ADD COLUMN result_object_intent_key VARCHAR(1024) NULL,
    ADD COLUMN result_cleanup_status VARCHAR(32) NOT NULL DEFAULT 'NOT_REQUIRED';
