# Case C C1 — Executable Terraform Contract Decision

## Status

**PARTIALLY SUPERSEDED BY LIVE EVIDENCE — 2026-10-02**

The provider-version pin, exact local provider schema catalog, AWS-only resource scope, and offline
Terraform CLI validation remain selected. The request-local generated-resource allowlist,
policy-only sensitive/account-identifier rejection, and sensitive-credential regeneration are
superseded by
[Case C C1 Correction — Simplified Terraform Draft Validation Decision](case-c-simplified-terraform-validation-decision.md)
after C2 live run `37003640318` exposed their interaction as a correctness defect.

Implementation is not authorized by this document alone. The bounded implementation contract is
`.agents/work-packages/case-c-executable-terraform-contract-v1.yml`.

## Operating scenario

The production-style backend accepts an architecture image, extracts architecture facts, retrieves
project-owned guidance, asks Vertex to generate Terraform, validates it offline with the exact bundled
Terraform/provider runtime, and persists the result only after validation succeeds.

C1 must make those boundaries agree before repeated correctness or capacity measurement resumes.

## Observed problems

1. The curated v3 corpus contains 128 documents but official provider material covers only 30 AWS
   resource types. It was intentionally designed as a small Terraformers-relevant corpus, not as a
   complete AWS Provider reference.
2. The image runtime already bundles Terraform 1.8.5 and hashicorp/aws 5.100.0, whose executable
   resource surface is much broader than the curated corpus.
3. REQUIRED retrieval currently proves only that some references exist; it does not prove that every
   generated resource was supported by request-relevant authoritative evidence.
4. Terraform CLI non-zero initialization failures are collapsed into one generic reason, which forced
   a separate runtime probe after capacity run 36957682821.
5. Generation recovery is split into failure-specific paths; adding another retry for each new
   validation failure would hide defects and create unbounded exception handling.

## Alternatives

### A — Keep the 30-resource curated corpus as the executable allowlist

Rejected. It would turn an evaluation-oriented corpus into an artificial production capability
boundary and would discard the much broader exact provider schema already available in the image.

### B — Embed the complete AWS Provider documentation set

Rejected for C1. It would require large-scale document extraction, chunking, embedding, ingestion,
index/version management, and retrieval-quality maintenance merely to obtain schema facts that the
provider already exposes deterministically.

### C — Query Terraform Registry/provider docs at request time

Rejected. It adds an external availability/network/version boundary to every analysis request and
weakens reproducibility.

### D — Curated RAG + exact local provider schema catalog

**Selected.**

The curated corpus remains responsible for Terraformers-specific decisions, risk guidance,
relationships, and representative patterns. The exact bundled AWS Provider becomes the authoritative
source for executable resource schema.

## Selected architecture

### Build-time provider schema catalog

The backend image already downloads and checksum-verifies Terraform 1.8.5 and
hashicorp/aws 5.100.0.

During that same image build, initialize the exact local provider and run
`terraform providers schema -json`. Preserve the resulting provider schema catalog inside the image.

Do not commit the complete generated schema dump to the repository and do not query the public
Registry during analysis requests.

### Request-specific schema context

Fact extraction remains able to emit any `aws_[a-z0-9_]+` type.

For one request, construct a bounded candidate resource set from:

- fact-extraction `resourceTypes`; and
- resource types attached to the curated references selected for that request.

Every candidate must be resolved against the exact AWS 5.100.0 local catalog. Only compact
request-relevant schema summaries are added to the generation context.

A resource is not rejected merely because it has no curated corpus document. If the architecture
facts identify it and the exact provider catalog contains it, it can be supported without expanding
the vector corpus first.

### Generated Terraform closure

Generated deployable resource types must:

1. be AWS resource blocks;
2. exist in the exact local provider catalog; and
3. belong to the request candidate set whose schema evidence was supplied.

C1 does not support module blocks or non-AWS deployable providers. Those require a later explicit
architecture decision rather than implicit download/runtime behavior.

Terraform CLI init/validate remains the final executable safety boundary.

## Recovery decision

One analysis generation has a maximum provider-call budget of **two**.

- normal generation may consume a second call for the existing output-truncation compact retry; or
- a first-pass hard-coded-sensitive-credential failure may consume a second call for the existing
  safety regeneration.

These paths share the same budget. There is no third generation call.

New contract violations introduced by C1 do not automatically gain a regeneration path. A generated
resource outside the request schema envelope, a second-attempt validation failure, or an executable
Terraform CLI failure fails closed. This prevents a new one-off retry from being added for every
failure class.

## Diagnostic decision

Terraform executable validation must retain bounded, non-sensitive failure categories sufficient to
distinguish at least:

- initialization timeout;
- provider-closure initialization failure;
- other initialization/configuration failure;
- validation timeout;
- Terraform validation/configuration failure; and
- internal/malformed CLI diagnostic failure.

Raw generated Terraform, credentials, or unbounded CLI output must not be emitted as the diagnostic.

## Validation plan

C1 static acceptance must prove:

1. a resource absent from the curated corpus can still be supported when it is present in the exact
   provider catalog and selected by architecture facts;
2. a nonexistent/non-AWS resource cannot enter the request schema envelope;
3. generated resources outside the request candidate set fail closed;
4. retrieved Terraformers patterns can add bounded companion resource types before schema lookup;
5. module blocks fail closed;
6. the runtime image derives its schema catalog from the same exact provider version it executes;
7. schema evidence added to prompts is request-bounded rather than the whole catalog;
8. truncation + safety recovery cannot produce a third model invocation;
9. Terraform init/validate failures map to stable safe categories without raw HCL/credential leakage.

No C1 acceptance requires a capacity run, GKE change, provider-version change, corpus-wide
re-embedding, or registry lookup at request time.

## Residual risk

- Fact extraction can still miss a required resource type. Curated relationship patterns are retained
  to add common companion resources, while repeated correctness in C2 measures the remaining
  stochastic gap.
- The curated corpus does not become complete provider documentation; it remains a project-quality
  guidance layer.
- Provider schema proves Terraform compatibility, not all semantic security/architecture quality.
  Project decision references and existing safety validation remain separate constraints.
