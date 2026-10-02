# Case C — Generated Terraform Contract Diagnostics Evidence

## Status

**STATIC IMPLEMENTATION / LIVE C2 RERUN NOT EXECUTED**

This evidence records the typed diagnostic implementation selected in PR #187. It does not close
C-ARCH-05 and does not authorize a live C2 rerun.

## Trigger

Live C2 run `36985305485` stopped on attempt 1 with:

- AnalysisJob `6a6eec5d-9e21-4fa8-907a-0dcd7521a24f`;
- source revision `8d72c91767c25705d460551babfc4fc079ce7d80`;
- stage `analysis_execution`;
- `errorClass=GeneratedTerraformContractViolation`;
- previous observability `category=other`.

The failed run proved a repository-owned generated-Terraform contract rejection but did not preserve
which fail-closed branch rejected the generated output.

## Implemented typed reasons

`GeneratedTerraformContractViolation` now has exactly three reasons:

- `MODULE_BLOCK`
- `RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT`
- `RESOURCE_OUTSIDE_REQUEST_SCHEMA_ENVELOPE`

`GeneratedTerraformContractInspector` assigns one of these reasons at the exact rejection point.
No dynamic resource type, label, candidate set, schema content, or generated HCL is attached to the
reason.

## Stable safe categories

`AnalysisObservability` maps the reasons one-to-one:

| Typed reason | Stable category |
|---|---|
| `MODULE_BLOCK` | `generated_terraform_contract_module` |
| `RESOURCE_OUTSIDE_AWS_PROVIDER_CONTRACT` | `generated_terraform_contract_provider` |
| `RESOURCE_OUTSIDE_REQUEST_SCHEMA_ENVELOPE` | `generated_terraform_contract_request_schema` |

Existing Terraform CLI categories are unchanged.

## C2 evidence integration

The existing C2 live-validation workflow accepts one of the three stable categories for
`GeneratedTerraformContractViolation` and records it in sanitized `attempts.jsonl`.

If the backend reports the same error class without one of the three expected categories, the
workflow records `unclassified` rather than guessing or parsing an exception message.

The workflow still does not upload:

- raw backend logs;
- raw HCL;
- JWT/private keys;
- request/response bodies;
- generated resource identifiers;
- schema candidate lists.

## Behavior intentionally unchanged

This implementation does not change:

- C1 executable Terraform envelope;
- schema candidate-set construction;
- generation prompt or model;
- retrieval or corpus behavior;
- generation provider-call budget;
- sensitive-credential/truncation recovery;
- AnalysisJob retryability;
- generic user-facing failure reason;
- C2 repeat count, concurrency, pass rule, or stop-on-first-failure behavior.

A generated-Terraform contract violation remains a non-retryable correctness failure.

## Deterministic verification target

Tests must prove:

1. each inspector branch emits the selected typed reason;
2. each typed reason maps to exactly one stable category;
3. metric output contains none of the fixed exception messages, raw HCL markers, resource names,
   credentials, or other dynamic generated/request content;
4. existing Terraform CLI categories remain unchanged.

## Live boundary

No image publication, rollout, C2 rerun, capacity baseline, or C3 action is part of this static
implementation.

After implementation acceptance and merge, a separate user checkpoint is still required before a
new immutable image is published and the C2 gate is rerun.
