# Case C C1 Correction — Simplified Terraform Draft Validation Decision

## Status

**SELECTED / USER-APPROVED — 2026-10-02**

This decision replaces the over-constrained parts of the earlier C1 executable-Terraform contract.
The user explicitly selected the direction: generated Terraform is a reference/editable draft, not
an automatically applied production artifact; unnecessary policy restrictions must be removed and
validation responsibility simplified.

Implementation is not authorized by this document alone. The bounded implementation contract is
`.agents/work-packages/case-c-simplified-terraform-validation-v1.yml`.

## Operating scenario

The service analyzes an architecture image and returns Terraform that a user can read, edit, and
adapt. The service does not automatically execute `terraform plan` or `terraform apply` against
the user's AWS account.

The generated result therefore needs to be:

- recognizable as Terraform rather than prose or empty output;
- composed of supported AWS Provider resource types within the current product scope;
- syntactically/provider-schema valid enough to be useful as a draft;
- grounded in the architecture/retrieval context where that context matters to system intent.

It does **not** need to satisfy deployment-policy rules that belong to a later human review or
deployment pipeline.

## Observed live failure

C2 run `37003640318` on source revision
`61accc9ee50bba57ae5fa9dff0074fdc01e970e4` failed on attempt 1.

The correlated backend trace proves:

- REQUIRED retrieval succeeded with eight documents;
- generation performed the existing `sensitive_credential` recovery;
- the terminal failure was
  `generated_terraform_contract_request_schema`;
- the backend typed category was correct;
- the C2 artifact parser recorded `unclassified` because it selected an earlier non-terminal
  `category=sensitive_credential` line.

The request-local schema envelope was constructed before generation from fact resource types and
retrieved-reference metadata. A later generated AWS resource could therefore be valid in the exact
bundled provider yet still be rejected merely because it was absent from that pre-generation set.

The same retrieval trace also showed a structural mismatch: retrieved provider example content may
contain companion resource blocks that are not represented by the document's `resourceTypes`
metadata. Prompt evidence can therefore show a resource that the request-local allowlist later
rejects.

## Root mechanism

The earlier C1 contract conflated three different responsibilities:

1. **generation context** — which provider schemas are useful to show the model before generation;
2. **provider capability** — which AWS resources actually exist in bundled Provider 5.100.0;
3. **deployment policy** — whether literals, account IDs, ARNs, placeholders, or similar values are
   appropriate for an environment.

The first was incorrectly promoted into a hard allowlist, while the third was enforced even though
the generated result is only an example/editable draft.

That coupling created false failures and an unnecessary regeneration path.

## Selected architecture

### 1. Generated Terraform is an editable draft

The generated Terraform result is explicitly an example/reference draft. It is not claimed to be
safe for unattended apply.

The product may still provide warnings or guidance, but those warnings must not become hidden hard
validation rules unless they are necessary for Terraform/provider correctness or a separately
approved product requirement.

### 2. Remove policy-only draft rejection

`TerraformDraftValidator` must no longer reject an otherwise structured Terraform draft merely
because it contains:

- a literal assigned to a password/secret/token-like attribute;
- a 12-digit AWS account ID;
- an account-scoped ARN;
- words such as `placeholder`, `TODO`, `replace this`, or `example only`.

The validator remains responsible for basic structural screening such as blank output, prose-only
output, missing Terraform deployable blocks, and markdown-fence cleanup.

This decision does not instruct the model to disclose or recover real credentials. It only removes
repository policy that falsely treats example literals as a correctness failure.

### 3. Remove sensitive-credential regeneration

The special `sensitive_credential` regeneration path is removed.

The normal generation path retains the existing bounded truncation retry only. A policy detector must
not trigger a second model call.

### 4. Request-specific schema evidence becomes advisory context

Before generation, request-relevant schema summaries remain useful prompt context.

The context candidate set should be formed from:

- fact-extraction AWS resource types;
- `resourceTypes` metadata on retrieved references; and
- AWS `resource "aws_*"` blocks actually present in retrieved reference content.

All candidate types are resolved against the exact local Provider 5.100.0 catalog before schema
summaries are injected.

This candidate set is **not** a generated-resource allowlist.

### 5. Final generated-resource boundary uses the exact provider catalog

After generation, every generated AWS resource block must exist in the exact bundled
`hashicorp/aws` 5.100.0 catalog.

A generated resource must **not** be rejected merely because it was absent from the pre-generation
request context.

The current AWS-only resource-block product scope is retained. Module support remains outside this
correction because the offline validator intentionally uses `terraform init -get=false`; enabling
remote modules would be a separate capability decision, not a safety exception.

### 6. Terraform CLI remains the executable correctness boundary

The existing offline execution checks remain:

- `terraform init -backend=false -get=false` against the bundled plugin directory;
- `terraform validate -json`.

They do not run `plan` or `apply`.

These checks remain responsible for Terraform/provider syntax and configuration validity.

### 7. Retrieval remains grounding/guidance, not a provider allowlist

REQUIRED retrieval continues to support architecture intent, project decisions, relationship
guidance, and representative patterns.

A valid provider resource does not require a matching retrieved document in order to be emitted.

Provider existence/schema correctness is owned by the exact local provider catalog and Terraform CLI,
not by OpenSearch corpus membership or request-local retrieval metadata.

### 8. C2 failure parser must use terminal failure evidence

The existing C2 workflow must stop taking the first arbitrary `category=` token for an
AnalysisJob.

It must derive the failure category from the job-correlated terminal failure line:

- prefer `Analysis job failed outcome=failed exceptionCategory=...`;
- use the stage-failure line only as a bounded fallback if terminal evidence is absent.

Earlier informational categories such as `sensitive_credential` must not become the final artifact
classification.

## Explicitly retained boundaries

This correction does **not**:

- add a second cloud runtime;
- change Terraform 1.8.5 or AWS Provider 5.100.0;
- query Terraform Registry at request time;
- ingest the complete AWS provider documentation set;
- permit nonexistent AWS resource types;
- enable non-AWS providers;
- enable Terraform modules;
- run `terraform plan` or `terraform apply`;
- resume capacity measurement;
- alter the C2 5/5 repeated-correctness acceptance rule.

## Acceptance

Implementation is accepted only if deterministic tests prove:

1. example literal password/token/account-ID/account-scoped-ARN/TODO/placeholder content is not
   rejected solely by policy;
2. the special sensitive-credential regeneration path no longer exists;
3. truncation remains the only generation retry path and cannot exceed the existing two-call budget;
4. retrieved reference content can add AWS companion resource types to prompt schema context;
5. a generated AWS resource that exists in the exact local catalog is accepted by the contract
   inspector even when absent from request schema evidence;
6. nonexistent/non-AWS generated resources still fail;
7. module behavior remains unchanged and unsupported;
8. Terraform init/validate remains the final executable correctness check;
9. C2 failure evidence uses terminal exception category rather than an earlier informational
   category;
10. no new workflow or standalone verifier is added.

## Same-scenario validation

After implementation merge, a new immutable backend image must be published from the exact then-main
SHA, rolled out by exact digest, and the existing protected C2 five-request gate may be rerun only
after a separate user live checkpoint.

Capacity remains suspended until C2 later passes and the remaining C3/C4 prerequisites close.
