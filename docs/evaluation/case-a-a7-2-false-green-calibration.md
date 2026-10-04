# Case A A7-2 — False-green measurement and offline calibration

## Status

**COMPLETE — ACCEPTANCE PASS — A7-3 NOT STARTED**

Implementation merge SHA:

`9ced20a4e9fc7fb42c6499d08bf5d7c66052f6f6`

## Scope and decision

A7-2 adds a deterministic offline calibration boundary. It does not rerun a provider, change
retrieval or generation, or reinterpret A7-1 runtime evidence quality as a label. The frozen
`EvaluationCase` is the sole source of labeled quality expectations.

The new `case-a-quality-calibration-v1` report embeds the existing
`case-a-retrieval-grounding-report-v1` report unchanged. Per case it records pipeline technical
success, frozen-label success, and the exact derived value
`falseGreen = technicalSuccess && !labeledQualitySuccess`. Optional A7-1 `QualityStatus` is retained
only as a side-by-side `MATCH`, `MISMATCH`, `INDETERMINATE`, or `UNAVAILABLE` comparison.

## Deterministic evidence

Unit fixtures represent the retained VPC before-state without fabricating historical raw run
artifacts: all four technical stages pass, project-decision coverage is `0/1`, retrieval resource
coverage is `2/4`, required generated resources are present, forbidden resources are absent, and
validation passes. The expected calibration is technical success, labeled-quality failure, and a
false green.

The same tests cover a fully grounded positive case, technical failure, each individual positive
label boundary, both frozen negative-control classifications, generated Terraform on a negative
control, optional/contradictory A7-1 statuses, report identities, and aggregate counts. These
fixtures express the accepted canonical and holdout scoring semantics; they do not claim to
reconstruct raw `EvaluationRunResult` artifacts that are not checked into the repository.

## Offline use

Given an existing run result and its matching frozen dataset, produce a calibration report with:

```bash
java ... CaseAMeasurementLauncher \
  --mode=calibrate \
  --result=/path/to/evaluation-run-result.json \
  --dataset=/path/to/dataset.json \
  --output=/path/to/case-a-quality-calibration.json
```

No canonical or holdout dataset file is changed by A7-2. A7-3 is outside this work package.


## Validation and acceptance evidence

- PR #205 merged as `9ced20a4e9fc7fb42c6499d08bf5d7c66052f6f6`.
- Backend Local Verification run `37191939296`: **SUCCESS**.
  - full `mvn clean test`
  - application package
  - MariaDB Flyway + Hibernate schema validation
  - canonical repository smoke queries
- Terraform Static Verification run `37191939292`: workflow **SUCCESS** at scope level.
  - the terraform/RAG job was skipped because PR #205 changed no terraform/RAG paths.
- Independent review aligned the historical VPC fixture with the frozen A2 evidence:
  `tfref-v2-sg-relations` missing and exact required retrieval resources
  `aws_vpc`, `aws_lb`, `aws_db_instance`, `aws_security_group` at `2/4` coverage.
- Independent review also aligned `ValidationExpectation.FAIL` with actual `EvaluationRunner`
  semantics: an expected invalid Terraform result is represented by validation stage `FAIL` with
  `valid=false`, and is not technical success.
- Frozen canonical and holdout dataset files were unchanged.
- No live GCP, Vertex, Bedrock, prompt, model, retrieval, generation, DB/API, or workflow behavior
  change was required.

All A7-2 Work Package acceptance criteria passed.

## Next gate

A7-3 — Provider Partial Failures — is not started. It requires separate user approval and a newly
bounded Work Package before implementation.
