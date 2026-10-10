-- Separate failed-draft accountability; never registered as a successful project file.
ALTER TABLE analysis_jobs ADD COLUMN diagnostic_bucket VARCHAR(255),
    ADD COLUMN diagnostic_key VARCHAR(1024),
    ADD COLUMN diagnostic_sha256 VARCHAR(64),
    ADD COLUMN diagnostic_status VARCHAR(32),
    ADD COLUMN diagnostic_expires_at TIMESTAMP(6) NULL;
CREATE INDEX idx_analysis_diagnostic_expiry ON analysis_jobs (diagnostic_expires_at, diagnostic_status);
