# Case C C1 — Executable Terraform Contract Evidence

## Execution identity

- Bound authoritative GitHub `main`: `47529fe426a95de18f57030e1426b1eede0df824`
- Work Package: `.agents/work-packages/case-c-executable-terraform-contract-v1.yml`
- Frozen executable boundary: Terraform `1.8.5`, `registry.terraform.io/hashicorp/aws` `5.100.0`

## Implemented contract

The backend image derives `/opt/terraform-provider-schema/aws-5.100.0.json` during its build by
initializing the checksum-verified offline provider and executing `terraform providers schema -json`.
The build fails unless the exact provider key and `aws_vpc` resource are present, and the resulting
catalog is read-only in the runtime image.

For each request, architecture-fact resource types and resource types carried by the retrieved
curated references form one deterministic candidate set. `AwsProviderSchemaCatalog` rejects
malformed, non-AWS, and catalog-absent candidates, then returns an immutable
`AwsProviderSchemaEvidence` containing compact summaries only for the selected resources. The prompt
receives that bounded evidence rather than the complete provider catalog.

After generation and the existing REQUIRED curated-grounding check, the generated Terraform
contract inspector rejects module blocks, non-AWS resources, catalog-absent resources, and AWS
resources outside the request evidence envelope. Contract violations do not trigger regeneration.

The truncation retry and sensitive-credential recovery share the existing `retryOccurred` state.
Sensitive recovery occurs only when the first generation did not already retry, limiting provider
generation to two calls. Terraform CLI failures expose stable safe categories for init timeout,
provider closure, other init/configuration failure, validate timeout, validate/configuration failure,
and malformed/internal failure; captured CLI output is not returned in those diagnostics.

## Issue mapping

### C-ARCH-01

- The Docker build derives the schema from the same bundled AWS `5.100.0` executable used by offline
  validation.
- Catalog resolution admits a fact-selected catalog resource without requiring curated-corpus
  membership and rejects malformed, non-AWS, or nonexistent resource candidates.
- Mechanical inspection rejects modules and resources outside the exact request envelope.

### C-ARCH-02

- The request envelope is the bounded union of extracted fact resource types and the resource types
  attached to references retrieved for that request.
- Only selected compact schema summaries enter the prompt.
- Every generated deployable resource must be covered by that envelope before final executable
  validation and persistence can proceed.

### C-ARCH-03

- Terraform initialization and validation outcomes map to six bounded categories.
- Provider-closure recognition uses bounded captured output only for classification; public failure
  messages never include that output, generated HCL, or credential values.

### C-ARCH-10

- A compact truncation retry consumes the second call.
- Sensitive-credential recovery consumes the second call only when no prior retry occurred.
- Schema-envelope and Terraform CLI violations fail closed without model regeneration.

## Static validation state

The single permitted bounded corrective iteration repaired Spring construction by explicitly marking
the production `ObjectMapper` constructor for injection and making the inspector's catalog dependency
lazy. Ordinary unrelated Spring contexts therefore do not load the Docker-only catalog, while the
first Vertex executable-generation request still loads it and fails closed if it is absent or invalid.
The repair also preserves provider-reported argument types and configurable nested-block structure,
directly verifies the fact-plus-curated-companion candidate union and unknown-fact rejection, and
adds direct safe observability-category coverage.

The first bounded repair fixed Spring construction and Docker-only catalog lazy loading. Subsequent
Backend Local Verification run `36972549049` reached the complete 415-test backend suite and passed
MariaDB schema/repository validation. Two test-contract failures remained: the schema test's
`id: type` substring incorrectly matched the valid `vpc_id: type` entry, and the existing orchestrator
test still supplied and asserted the pre-C1 unclassified Terraform CLI failure message. The user
explicitly approved one narrow test-contract closure amendment. That closure replaces the substring
check with standalone nested-`id` boundaries and aligns the orchestrator fixture/assertion with the
approved `VALIDATE_CONFIGURATION` exception category. No production behavior changed in this
closure.

Final PR #182 head `0136ca3491eecfa0011d52b9025a15ccf5e0a171` passed Terraform Static
Verification run `36974352021` and Backend Local Verification run `36974352028`, including the
backend local smoke baseline and MariaDB schema/repository validation. Independent acceptance review
confirmed the predeclared C-ARCH-01, C-ARCH-02, C-ARCH-03, and C-ARCH-10 criteria without changing
their acceptance text, severity, ownership, architecture, or scope. PR #182 then merged to
authoritative `main` as `40237e593530086b4649bfc8bc5499848c4b7165`.

This closes C1 evidence for those four issues. Capacity execution remains suspended; C2 is not
started by this maintenance transition.

## Residual risks

- Fact extraction can still omit a needed resource type; curated companion-resource relationships
  mitigate but do not eliminate that stochastic gap.
- Provider schema compatibility does not prove semantic architecture or security quality.
- The bounded inspector is deliberately not a complete HCL parser; Terraform CLI remains the final
  executable syntax and provider-validation boundary.

## Live-evidence correction

The original C1 closure above remains historical evidence of PR #182 and its then-approved
contract. It is not the current selected behavior in full.

C2 run `37003640318` demonstrated that two original constraints were over-constrained for the
actual product role of generated Terraform:

- request-specific schema evidence acted as a hard generated-resource allowlist; and
- policy-only sensitive-literal detection triggered a dedicated regeneration path.

PR #189 therefore revised the selected contract. The current implementation candidate is documented
in [Simplified Terraform Draft Validation Evidence](case-c-simplified-terraform-validation.md).

The retained parts of the original C1 contract are:

- exact local AWS Provider 5.100.0 catalog;
- AWS-only generated resource scope;
- module blocks unsupported;
- Terraform CLI offline initialization/validation;
- bounded safe diagnostics.

The superseded parts are:

- request-local schema evidence as a final generated-resource allowlist;
- policy-only credential/account-ID/account-ARN/placeholder rejection;
- sensitive-credential-specific regeneration.

C1 remains reopened until the revised implementation is merged and accepted.
