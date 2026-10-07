-- Historical updated_at also includes lease renewals and result cleanup. Do not backfill it.
ALTER TABLE analysis_jobs ADD COLUMN terminal_at TIMESTAMP(6) NULL;
