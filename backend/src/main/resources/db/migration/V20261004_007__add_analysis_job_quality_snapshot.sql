ALTER TABLE analysis_jobs
    ADD COLUMN quality_contract_version VARCHAR(64) NULL,
    ADD COLUMN technical_status VARCHAR(32) NULL,
    ADD COLUMN knowledge_status VARCHAR(32) NULL,
    ADD COLUMN quality_status VARCHAR(32) NULL,
    ADD COLUMN project_decision_status VARCHAR(32) NULL,
    ADD COLUMN runtime_quality_boundary VARCHAR(64) NULL,
    ADD COLUMN quality_reasons VARCHAR(512) NULL;
