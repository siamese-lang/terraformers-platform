# Case A A7-5 — safe executable diagnostics

Status: **COMPLETE — ACCEPTANCE PASS — A7-6 NOT STARTED — LIVE VALIDATION NOT PERFORMED**

## Closure evidence

- feature implementation PR: #216
- feature merge SHA: `0ce33a2302cfda87bcbf505d67b71257958a2b80`
- main validation/implementation PR: #217
- implementation merge SHA: `c5840c05c60ec78dc3a8c698453424b587014814`
- Backend Local Verification `37215839871`: **SUCCESS**
  - Flyway migration uniqueness: 7 unique migrations
  - Maven clean tests: success
  - application package: success
  - MariaDB Flyway/Hibernate schema validation: success
  - canonical repository smoke queries: success
- Terraform Static Verification `37215839970`: **SUCCESS**
  - scope/workflow policy checks: success
  - Terraform static job: skipped because A7-5 changed no Terraform paths
- independent A7-5 acceptance: **PASS**
- no live C2 rerun
- no Vertex/Bedrock/GCP/OpenSearch/cost-bearing action
- A7-6 is not started

## Decision

A7-5 will reduce Terraform `validate -json` failures into a bounded Terraformers-owned diagnostic
summary before the temporary validation workspace and captured CLI output are deleted.

The existing top-level failure contract remains unchanged:

- `TerraformValidationFailureException.Category.VALIDATE_CONFIGURATION`;
- observability category `terraform_validate_configuration`;
- A7-4 quality reason `TERRAFORM_EXECUTABLE_FAILURE`.

A7-5 adds root-cause evidence beneath that boundary rather than creating a second quality model.

## Observed evidence

Case C C2 run `37016993776` used one frozen source/image/fixture/runtime identity.

- attempt 1: complete integrated path PASS;
- attempt 2: terminal `CORRECTNESS_FAILURE`;
- retained category: `terraform_validate_configuration`;
- automatic whole-gate rerun: false.

The failed HCL was intentionally not persisted before validation success, and arbitrary Terraform CLI
diagnostic text was intentionally not exported. The exact historical invalid construct is therefore
unknown and remains unknown.

Current `TerraformCliValidator` already executes:

`terraform validate -json -no-color`

and parses the returned JSON. It currently verifies only the top-level `valid` field before reducing
every invalid configuration to:

`VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation`

The diagnostic array is therefore available at the exact point where safe reduction can occur.

## Selected boundary

The selected path is **bounded structured diagnostic reduction**.

For an invalid `validate -json` result, the validator may inspect in memory:

- `error_count`;
- `warning_count`;
- diagnostic severity;
- diagnostic summary/detail only for deterministic classification.

It must emit only:

- a finite diagnostic enum/set;
- bounded numeric error count;
- bounded numeric warning count.

Candidate classes are:

- `MISSING_REQUIRED_ARGUMENT`;
- `UNSUPPORTED_ARGUMENT_OR_BLOCK`;
- `UNDECLARED_REFERENCE`;
- `INVALID_FUNCTION_ARGUMENT`;
- `INVALID_VALUE_OR_TYPE`;
- `SYNTAX_OR_CONFIGURATION`;
- `PROVIDER_CONFIGURATION`;
- `UNKNOWN`.

The exact final enum is implementation-derived. A class is retained only if a deterministic Terraform
JSON fixture supports it. Unmatched diagnostics fall back to `UNKNOWN`; implementation must not
invent a more specific class from a plausible guess.

## Rejected alternatives

### Keep only VALIDATE_CONFIGURATION

Safe but rejected because it preserves the exact C2 root-cause gap.

### Retain raw failed HCL or raw Terraform diagnostics

Rejected. Generated HCL, diagnostic summary/detail/snippet/range/filename, and arbitrary CLI output
may carry user/generated values and are unnecessary once a bounded class/count representation is
available.

### Add a new observability product

Rejected for A7-5. Existing Micrometer, MDC, exceptions, and repository tests are sufficient to prove
this boundary. A7-6 separately decides whether any external observability product is justified.

## Persistence and API boundary

A7-5 does not add a database migration or a new API object.

If durable read-back is needed, only a fixed machine-generated bounded summary may reuse the existing
`failureReason` field. Raw Terraform text must never enter that field. The preferred implementation
keeps the existing API/schema surface unchanged.

A7-4 `evidence-quality-v1` is not extended. Executable failure remains
`TERRAFORM_EXECUTABLE_FAILURE`; the A7-5 diagnostic class is RCA evidence, not quality status.

## Observability boundary

The existing `terraform_validate_configuration` top-level category remains stable.

A7-5 may add:

- one low-cardinality diagnostic-class metric/counter;
- bounded error/warning numeric measurements;
- one bounded terminal diagnostic log under existing `analysisJobId` MDC.

No job/project/resource/path ID, diagnostic text, HCL, exception message, or generated value becomes a
metric label.

## Validation

Repository-only validation is sufficient.

Deterministic tests must cover at minimum:

- missing required argument;
- unsupported argument/block;
- undeclared reference;
- unknown fallback;
- error/warning count handling;
- preservation of top-level `VALIDATE_CONFIGURATION`;
- raw diagnostic/HCL sentinel non-leakage.

A representative fixture must demonstrate that a future recurrence of the C2 top-level failure class
would retain more actionable bounded evidence. It must **not** claim to reconstruct the exact
historical attempt-2 invalid construct.

No live C2 rerun, Vertex/Bedrock call, GCP action, OpenSearch ingestion, image publication, rollout, or
cost-bearing operation is authorized.

## Implementation evidence

The bounded implementation recognizes only four deterministic classes supported by repository JSON
fixtures: `MISSING_REQUIRED_ARGUMENT`, `UNSUPPORTED_ARGUMENT_OR_BLOCK`, `UNDECLARED_REFERENCE`, and
`UNKNOWN`. Classes are de-duplicated and enum-sorted, while error and warning counts are clamped to
`0..1000`. Terraform summary/detail and all other diagnostic payload fields remain local to the
validator reduction and are neither returned nor placed in exception messages, metric labels, or
logs.

The typed summary is propagated beneath the unchanged `VALIDATE_CONFIGURATION` exception category.
`AnalysisObservability` retains `terraform_validate_configuration` and emits only diagnostic enum
labels plus numeric count measurements and a bounded enum/count log event. The durable failure
reason and `evidence-quality-v1` mapping remain unchanged.

The repository-only representative fixture demonstrates improved evidence for a future failure in
the same top-level class as C2. It does not reconstruct or assign a subtype to historical run
`37016993776`; that historical subtype remains unknown.

Local focused Maven execution was attempted, but dependency resolution was blocked by the execution
environment returning HTTP 403 for Maven Central's Spring Boot parent POM. No dependency or
repository workaround was introduced. Final authority came from repository CI: Backend Local
Verification `37215839871` passed Flyway uniqueness, Maven clean tests/package, MariaDB
Flyway/Hibernate validation, and canonical repository smoke queries. Terraform Static Verification
`37215839970` passed its scope/policy checks.

## Next gate

A7-6 does not start automatically. It requires separate user approval after A7-5 implementation,
independent acceptance, merge, and closure.
