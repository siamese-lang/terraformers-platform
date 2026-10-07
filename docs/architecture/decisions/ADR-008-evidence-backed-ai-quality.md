# ADR-008 — Evidence-backed AI quality for Terraformers

## Status

ACCEPTED FOR CASE A EXTENSION PLANNING

This ADR freezes the architecture direction for the Case A semantic-reliability extension before
implementation starts.

The original Case A retrieval-grounding/generalization closure remains valid. This ADR adds an
operational quality claim; it does not erase or rewrite the earlier closure.

## Context

Terraformers already has direct evidence that technical pipeline success can be a false green.

A retained VPC evaluation trace completed fact extraction, retrieval, generation, and Terraform
validation while required retrieval grounding was incomplete. Later Case A work corrected that
specific retrieval defect and passed canonical N=3 plus the frozen holdout.

The new problem is broader:

> How does an AI-backed backend decide whether an apparently successful result is sufficiently
> supported by authoritative evidence, and how does it distinguish model/provider, knowledge,
> retrieval, generation, and executable-correctness failures?

The answer must not depend on adding more LLM judges. Terraformers has a stronger domain-specific
advantage: generated Terraform can be checked against version-pinned provider knowledge, provider
schema, and Terraform CLI behavior.

The current v3 corpus is not sufficient as a universal quality oracle. It contains 128 documents
but covers only a curated subset of roughly thirty AWS resources. Its v3 derivation preserved v2
content rather than expanding provider coverage.

## Decision

Terraformers will implement **Evidence-backed AI Quality** using a layered authority and outcome
model.

### 1. Authority hierarchy

The quality contract uses three knowledge classes.

1. **AWS Provider schema**
   - version-pinned to the selected provider version;
   - deterministic authority for resource existence, arguments, types, required/optional fields,
     and nested block structure;
   - direct lookup, not semantic retrieval, when the resource type is known.

2. **Official AWS Provider documentation/examples**
   - authoritative RAG evidence for resource semantics and valid usage;
   - generated from pinned provider source with source path, source commit, provider version,
     document type, checksum, and stable document identity;
   - retrieved with resource-aware and semantic selection.

3. **Terraformers project decisions**
   - curated repository-owned evidence for relationship and architecture decisions that provider
     documentation alone does not answer;
   - examples include security-group relationships and EKS/IRSA decisions;
   - remain few, explicit, and manually reviewed.

Provider schema and official documentation are not interchangeable. Schema proves structural
validity; docs/examples provide usage evidence. Project decisions provide application-specific
relationship intent.

### 2. Corpus architecture

The hard-coded provider-resource allowlist in the current corpus builder is not the target design.

A new corpus version must be derivable from pinned provider source/schema without requiring a source
code change for each newly supported aws_* resource.

The compiler must:

- discover or accept resource types from the pinned provider schema/source;
- validate every requested resource against the provider schema;
- locate official provider documentation deterministically;
- create bounded provider-document, example, and schema evidence;
- preserve stable IDs and provenance;
- preserve sanitization/risk metadata;
- preserve the v3 corpus as immutable historical evidence;
- create a new corpus identity for expanded content;
- report provider-resource coverage separately from retrieval success.

The implementation may support bounded resource-set and full-provider build modes, but the resource
universe must not be encoded as a Python dictionary that requires code edits for each resource.

The production corpus scope is selected from measured provider-source inventory and ingestion cost,
not from the old thirty-resource allowlist. If some provider resources have schema but no usable
official documentation, the system records a knowledge-coverage gap rather than inventing evidence.

### 3. Input interpretation boundary

Image-to-fact extraction remains probabilistic.

Runtime quality is therefore explicitly:

> evidence-backed quality **conditional on the extracted architecture facts**.

Runtime evidence may not claim that the extracted facts are complete ground truth for an arbitrary
user image.

Fact-extraction accuracy is evaluated offline with the frozen canonical/holdout expectations.

No second LLM or model-voting layer is introduced merely to judge the first model.

### 4. Runtime outcome model

Persist and expose distinct dimensions rather than one overloaded PASS/FAIL.

#### Technical status

Whether the repository-owned pipeline contracts completed successfully.

Minimum values:

- PASS
- FAIL

#### Knowledge status

Whether authoritative knowledge needed for the observed facts is available.

Minimum values:

- COMPLETE
- INCOMPLETE
- UNKNOWN
- NOT_APPLICABLE

#### Quality status

Whether the successful output is supported by the available authoritative evidence.

Minimum values:

- EVIDENCE_BACKED
- DEGRADED
- UNKNOWN
- NOT_APPLICABLE

#### Bounded reason

Reason codes distinguish at least these classes when applicable:

- RESOURCE_UNKNOWN_TO_PROVIDER
- OFFICIAL_KNOWLEDGE_NOT_AVAILABLE
- REQUIRED_EVIDENCE_NOT_RETRIEVED
- REQUIRED_PROJECT_DECISION_NOT_RETRIEVED
- GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE
- CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING
- PROVIDER_CONTENT_BLOCKED
- PROVIDER_OUTPUT_TRUNCATED
- PROVIDER_EMPTY_RESPONSE
- PROVIDER_TIMEOUT
- PROVIDER_RATE_LIMITED
- PROVIDER_ERROR
- PROVIDER_SCHEMA_FAILURE
- TERRAFORM_EXECUTABLE_FAILURE

Reason codes may be refined during implementation only when directly derived from observed
mechanics. They must remain bounded and low-cardinality.

The USER-approved PT-3 correction adds `CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING` for the
independently observed OAC/new-S3-origin omission. The existing final-output contract inspector
checks identifiable authorization declarations; the existing quality assessor records DEGRADED
without rewriting a valid provider/CLI technical PASS as a syntax/schema failure. This is a
bounded omission check, not general IAM-policy evaluation or deployment validation. Supplied
policy expressions/references and alternate declared read access remain editable; their presence
does not prove policy correctness, deployment readiness or realistic generalization. UI status and
waiting behavior remain PT-4 responsibilities.

### 5. Evidence-backed quality rules

For an architecture request, quality evaluation considers:

- extracted resource types;
- availability of provider schema for those types;
- availability of official documentation for those types;
- final selected official/reference evidence;
- applicable selected project-decision evidence;
- generated resource types;
- provider-schema validity of generated resources;
- structural/executable Terraform validation.

A generated resource can be deterministically checked against:

- provider schema existence; and
- selected evidence whose resourceTypes support that generated type.

This creates generated-output-to-evidence support without asking the LLM to self-report citations.

Missing corpus knowledge and failed retrieval are different states.

Example:

- schema exists, official docs unavailable -> KNOWLEDGE INCOMPLETE;
- docs exist in corpus, required evidence absent from selected context -> RETRIEVAL GAP;
- evidence selected, generated resource has no supporting evidence -> GENERATION EVIDENCE GAP.

### 6. Relationship/project-decision boundary

Resource coverage alone is insufficient.

Where the repository has a deterministic applicability contract for relationship/project-decision
evidence, quality must preserve that evidence separately from resource-schema evidence.

For arbitrary unlabeled runtime input, absence of a project decision may not be promoted to semantic
failure unless applicability is deterministically established. Otherwise the result is UNKNOWN for
that dimension.

Frozen evaluation fixtures may use their explicit required project-decision expectations as labeled
truth.

### 7. False-green definition

False green is an **offline labeled-evaluation concept**.

For a frozen evaluation case:

- technical_success uses the existing pipeline/validation success contract;
- labeled_quality_success uses the frozen classification, grounding, generated-resource, forbidden
  resource, and validation expectations;
- false_green = technical_success && !labeled_quality_success.

The extension must preserve the historical VPC false-green before-state and demonstrate the final
after-state without changing the old Case A acceptance history.

### 8. Provider partial failures

A successful SDK/HTTP exchange is not equivalent to a usable model completion.

Provider-neutral completion/failure semantics must distinguish, when safely observable:

- normal completion;
- output truncation;
- provider content/safety blocking;
- empty response;
- timeout;
- rate limit;
- generic provider error.

The historical Case C sensitive-credential incident is **not** classified as provider censorship.
It was a repository-owned draft-policy rejection later removed by PR #190.

Provider safety controls are not weakened to manufacture successful output.

### 9. Terraform executable diagnostics

Raw failed HCL is not required for useful diagnosis.

Terraform structured diagnostics should be reduced to a bounded safe taxonomy derived from actual
terraform validate -json output, plus bounded counts/fingerprints when safe.

Do not persist or export:

- raw generated HCL;
- arbitrary CLI diagnostic text;
- credentials;
- prompts;
- image bytes;
- retrieved document content as monitoring payload.

### 10. Observability product boundary

Repository-native metrics/evaluation artifacts, Langfuse, and OpenTelemetry remain candidates.

No product is adopted by default.

A new observability product may be selected only if the implemented evidence/quality model leaves a
demonstrated trace, score, or correlation problem that repository-native evidence cannot answer
cleanly.

Langfuse, if selected, is an observability/analysis surface for Terraformers-owned scores and model
traces. It is not the quality oracle.

### 11. Live validation boundary

Repository tests and historical artifacts are used first.

If the service-level claim still requires proof, the existing Case C GCP representative topology
may be recreated through reviewed repository bootstrap/IaC/delivery paths after a separate user
checkpoint.

No second cloud architecture is introduced by this ADR.

A live session must preserve failures as evidence, avoid rerun-until-lucky behavior, and end with a
reviewed cost/resource teardown.

## Rejected alternatives

### LLM-as-a-judge as the primary quality oracle

Rejected as the default because it adds another probabilistic dependency and can produce
self-consistent errors. It may be reconsidered only for a demonstrated semantic question that
authoritative deterministic evidence cannot address.

### Multiple-model voting

Rejected for the same reason and because model agreement is not ground truth.

### Keep the current fixed thirty-resource corpus

Rejected because corpus absence would be confused with AI/retrieval quality failure.

### Embed the entire provider blindly without provenance or bounded structure

Rejected. Scale alone is not a quality architecture. Expanded evidence must retain pinned source
identity, authority, resource metadata, stable IDs, and bounded chunking.

### Treat Terraform official examples as the exact answer key

Rejected. Official examples are authoritative usage evidence, not the unique correct implementation
for a user's architecture image.

## Change-control rule

This ADR is the **direction freeze** for the Case A extension.

After merge, implementation may not change these architecture axes merely because a new framework,
tool, or interesting article is discovered.

Changing an axis requires all of:

1. repository or live evidence showing that a frozen assumption is materially false or insufficient;
2. a written proposed ADR amendment identifying the exact failed assumption;
3. explicit user approval before implementation changes direction.

Implementation defects and local design details do not reopen the architecture automatically.

The frozen axes are:

1. authority hierarchy;
2. scalable corpus compiler rather than a hard-coded resource allowlist;
3. no default evaluator LLM;
4. separation of technical, knowledge, runtime quality, and labeled evaluation states;
5. resource plus relationship/project-decision grounding;
6. provider partial-failure taxonomy;
7. safe bounded Terraform diagnostics;
8. evidence-gated observability-product adoption;
9. optional existing-topology GCP live proof;
10. final teardown after any recreated live runtime.

## Consequences

Positive:

- RAG becomes the explicit evidence basis for output-quality reasoning;
- corpus absence, retrieval failure, generation unsupportedness, and executable failure are no
  longer collapsed into one quality bucket;
- the runtime can explain why a result is trusted, degraded, or unknown;
- the design reuses deterministic Terraform/provider mechanisms rather than multiplying LLMs;
- the portfolio story connects AI, RAG, backend state, validation, and observability coherently.

Costs:

- corpus build/verification logic becomes more sophisticated;
- a new corpus version and re-ingestion are likely required;
- AnalysisJob/result persistence and API contracts may need additive fields;
- expanded provider-document ingestion may increase embedding/index cost;
- live representative validation may require temporary GCP recreation.

These costs are accepted because they directly support the selected operational quality claim.
