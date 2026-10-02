# Case C — Simplified Terraform Draft Validation Evidence

## Status

**IMPLEMENTATION CANDIDATE / STATIC VALIDATION PENDING**

This document records the implementation candidate selected by PR #189. It does not close reopened
C1 issues and does not authorize image publication, rollout, C2 rerun, or capacity execution.

## Trigger

C2 run `37003640318` failed at attempt 1 after:

- REQUIRED retrieval succeeded;
- the policy-only `sensitive_credential` regeneration path executed;
- terminal backend category became `generated_terraform_contract_request_schema`;
- the C2 artifact recorded `unclassified` because the workflow selected an earlier informational
  category instead of the terminal AnalysisJob failure category.

That live trace showed that request-local schema context and policy-only literal checks were acting
as correctness gates even though generated Terraform is an editable/reference draft.

## Implemented validation responsibility

### Structural draft screening

`TerraformDraftValidator` now checks only whether a Terraform draft is present:

- blank or language-label-only output fails;
- Terraform must contain at least one resource or module-shaped declaration;
- obvious prose/non-HCL output fails;
- Markdown fences are stripped;
- resource/module body syntax, required arguments, and provider-schema validity are not inferred with
  regex heuristics and are delegated to the existing Terraform executable validator.

It no longer rejects otherwise structured Terraform solely because it contains:

- password/secret/token-like literal assignments;
- AWS account IDs;
- account-scoped ARNs;
- placeholder, TODO, replace-this, or example wording.

### Generation retry

The sensitive-credential-specific regeneration API and prompt path are removed.

`VertexGenerationStage` retains only the existing output-truncation compact retry:

- standard generation = call 1;
- if truncated, compact generation = call 2;
- a second truncation escapes as failure;
- no third model call is introduced.

### Request schema context

Before generation, schema context is built from the union of:

- fact-extraction resource types;
- retrieved-reference `resourceTypes` metadata;
- AWS `resource "aws_*"` blocks found directly in retrieved reference content.

Only types present in the exact local AWS Provider 5.100.0 catalog are included in prompt schema
context. Invalid fact/reference candidates are omitted from advisory context rather than failing the
request before generation.

### Final generated-resource contract

Request-local schema context is not passed to the final contract inspector.

The final generated-resource contract now checks:

- module blocks remain unsupported;
- non-AWS resource blocks fail;
- nonexistent AWS Provider 5.100.0 resources fail;
- an AWS resource that exists in the exact bundled catalog is not rejected merely because it was
  absent from the pre-generation request context.

Terraform CLI initialization and validation remain unchanged as the final executable correctness
boundary.

## C2 diagnostic correction

For failed C2 jobs, the workflow now:

1. prefers the job-correlated terminal
   `Analysis job failed outcome=failed exceptionCategory=...` value;
2. uses job-correlated `analysis stage outcome=failure category=...` only as a fallback;
3. obtains `errorClass` only from a stage-failure line;
4. no longer allows an earlier informational category such as `sensitive_credential` to become the
   final artifact category.

External timeout classification still requires
`errorClass=AnalysisProviderTimeoutException`; provider rate limiting remains explicit.

## Explicitly unchanged

- Terraform 1.8.5;
- hashicorp/aws 5.100.0;
- AWS-only current generation scope;
- module support remains disabled;
- REQUIRED retrieval mode;
- retrieval algorithm/corpus;
- generation and embedding models;
- Terraform CLI `init -backend=false -get=false` and `validate -json`;
- C2 5/5 acceptance;
- no Terraform plan/apply;
- no new workflow or standalone verifier.

## Static acceptance target

The implementation candidate is acceptable only if existing CI proves the full backend suite and
repository policy checks pass, including deterministic tests for:

- illustrative credential/account/placeholder content no longer causing policy rejection;
- truncation as the only generation retry with at most two calls;
- retrieved reference content adding companion resource types to prompt context;
- unknown advisory fact types not failing the request by themselves;
- catalog resources being accepted without request-local envelope membership;
- fake/non-AWS resources and modules still failing;
- safe observability categories remaining bounded;
- C2 terminal failure-category selection semantics.

## Live boundary

C1 remains ACTIVE and C2 remains BLOCKED until this candidate is merged and independently accepted.
A later immutable image publish, rollout, and C2 rerun require a separate user checkpoint.
