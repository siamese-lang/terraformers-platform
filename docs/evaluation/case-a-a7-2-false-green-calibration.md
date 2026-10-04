# Case A A7-2 — False-green measurement and offline calibration

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
