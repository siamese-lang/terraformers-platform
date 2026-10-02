# Case C C2 — Generated Terraform Contract Diagnostic Taxonomy Decision

## Status

**PROPOSED / USER APPROVAL REQUIRED**

This is D_DECISION work. It records a narrow observability contract discovered by live C2 run
`36985305485`. It does not authorize backend implementation, image publication, rollout, live C2
rerun, capacity execution, or C3 work.

## Trigger evidence

The first C2 live gate executed against the accepted runtime identity and stopped on attempt 1.

Recovered backend evidence:

- AnalysisJob: `6a6eec5d-9e21-4fa8-907a-0dcd7521a24f`
- source revision: `8d72c91767c25705d460551babfc4fc079ce7d80`
- stage: `analysis_execution`
- error class: `GeneratedTerraformContractViolation`
- current observability category: `other`
- claim generation: `1`

This proves a repository-owned C1 generated-Terraform contract rejection rather than external
provider/network variance. The current telemetry loses which of the three fail-closed conditions
fired.

## Existing contract branches

`GeneratedTerraformContractInspector` currently rejects only these conditions:

1. generated module block;
2. generated resource outside the executable AWS Provider contract;
3. generated AWS resource outside the request-specific schema envelope.

The current exception carries only a free-form message. `AnalysisObservability` therefore emits
`category=other`. `AnalysisJobRunner` intentionally converts the durable user-facing failure
reason to a generic message, so the subtype cannot be recovered after the process exits.

## Decision goal

Preserve exactly one safe, bounded diagnostic subtype for generated-Terraform contract rejection
without exposing:

- raw generated HCL;
- generated resource names or labels;
- request candidate/resource-type sets;
- credentials or other request content;
- exception messages as metric labels.

The diagnostic must not alter C1 generation, grounding, schema, validation, or retry semantics.

## Alternatives

### A. Leave the exception as `category=other` and rerun C2

Rejected. A second failure could reproduce the same diagnostic dead end and consume another live
execution without identifying the violated contract branch.

### B. Log or persist the existing exception message

Rejected. The messages are currently safe, but a free-form message contract is brittle and can later
accumulate resource names or other generated content. Metrics/artifacts should not depend on message
parsing.

### C. Infer the subtype in the GitHub Actions workflow

Rejected. The workflow sees only post-run telemetry and should not duplicate backend domain logic.
The subtype belongs at the point where the contract violation is created.

### D. Add a typed reason to `GeneratedTerraformContractViolation`

**Selected.**

The exception becomes a closed typed contract with exactly three reasons. The inspector constructs
the exception from the reason enum, and observability maps that enum to a stable bounded category.

## Selected typed contract

`GeneratedTerraformContractViolation.Reason` contains exactly:

- `MODULE_BLOCK`
- `RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT`
- `RESOURCE_OUTSIDE_REQUEST_SCHEMA_ENVELOPE`

The exception must expose `reason()`.

The exception message may remain a fixed implementation detail derived from the enum, but callers,
metrics, C2 evidence, and tests must use the typed reason rather than parse the message.

## Stable observability categories

The three reasons map one-to-one to:

- `generated_terraform_contract_module`
- `generated_terraform_contract_provider`
- `generated_terraform_contract_request_schema`

These strings are the only new metric/log/artifact categories introduced by this decision.

They must not contain:

- generated resource type;
- resource label;
- HCL snippet;
- candidate-set contents;
- provider schema contents;
- exception message.

## User-facing failure behavior

`AnalysisJobRunner` continues to expose the existing generic user-facing analysis failure reason.

This decision does not make internal contract details part of the public API and does not change
retryability. A `GeneratedTerraformContractViolation` remains non-retryable inside one AnalysisJob.

## C2 integration

After backend implementation, the existing C2 workflow may preserve one of the three stable
categories in its sanitized `attempts.jsonl`.

The workflow must not:

- parse exception messages;
- upload raw backend logs;
- add an automatic C2 rerun;
- convert a generated-contract rejection into external variance.

A live C2 rerun remains separately user-approved and requires a newly published immutable image
from the then-current exact main SHA followed by exact-digest rollout.

## Acceptance

Implementation acceptance requires deterministic tests proving:

- each inspector branch throws the correct typed reason;
- each typed reason maps to exactly one stable observability category;
- metric/log labels contain no raw HCL, credentials, resource names, resource labels, candidate
  types, or exception-message content;
- existing Terraform CLI diagnostic categories remain unchanged;
- C2 sanitized evidence allowlists all three new categories;
- existing generic user-facing failure reason remains unchanged;
- no generation retry or recovery behavior is added;
- no C1 executable-envelope acceptance is weakened.

## Residual boundary

This diagnostic taxonomy identifies **which contract branch rejected the generated output**. It does
not by itself prove why the model generated that output. If a future live run reports
`generated_terraform_contract_request_schema`, for example, the next root-cause analysis may need
to compare request facts/retrieved reference types to expected dataset evidence under a separately
approved scope. That future analysis must not be pre-emptively implemented here.

## Decision checkpoint

User approval authorizes only implementation of this typed diagnostic contract and its deterministic
tests/evidence integration.

It does not authorize:

- C1 contract relaxation;
- generation/retrieval/model/corpus changes;
- new retry paths;
- raw-HCL logging;
- image publication;
- backend rollout;
- live C2 rerun;
- capacity baseline execution;
- C3 work.
