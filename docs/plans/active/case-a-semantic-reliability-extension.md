# Case A Extension — Evidence-backed AI Quality and Semantic Reliability

## Status

**A7-0 COMPLETE — A7-1 COMPLETE — A7-2 COMPLETE — A7-3 COMPLETE — A7-4 COMPLETE — A7-5 COMPLETE — A7-6 DECISION IMPLEMENTED / AWAITING REVIEW — LIVE GCP NOT AUTHORIZED**

The original Case A retrieval-grounding/generalization closure remains valid.

This extension adds one new portfolio question:

> How does Terraformers decide whether an apparently successful AI result is supported by
> authoritative Terraform evidence, and how does it distinguish knowledge, retrieval, generation,
> provider, and executable-correctness failures?

The architecture decision is frozen in
ADR-008 — Evidence-backed AI quality for Terraformers.

Extension direction is governed by
`.agents/work-packages/case-a-semantic-reliability-v1.yml`. A7-1 through A7-5 are complete. A7-6 is a repository-only decision task under `.agents/work-packages/case-a-a7-6-observability-backend-decision-v1.yml`; the decision is implemented and awaits independent review.

PR #196 remains intentionally unmerged until this extension reaches its closure boundary.


A7-0 closed on the measured AWS Provider 5.100.0 universe:

- provider-schema resources: **1,526**
- schema resources with official resource documentation: **1,514**
- schema resources without official resource documentation: **12**
- historical v3 provider-resource coverage: **30 / 1,526 (1.9659%)**
- broad v4 candidate: **1,514 provider resources**, **5,395 total documents**, **5,387 provider chunks**
- candidate JSONL size: **14,833,335 bytes**
- candidate content volume: **10,618,014 characters**, approximately **2,654,504 tokens** at the retained 4-characters/token sizing estimate
- official-evidence extraction gaps in the broad candidate: **0**

The selected A7-0 scope is therefore not a bounded legacy subset. Structural authority remains the
full 1,526-resource provider schema, while RAG ingestion targets the full 1,514-resource
schema/official-document intersection. The remaining 12 schema resources are explicit official
knowledge gaps; the project does not fabricate RAG evidence for them.

A7-1 was explicitly approved on 2026-10-04, implemented on execution base
`2efa4dcc1425feafac896a069315416949fa7ab5`, and merged as
`f9009ce8f9a744afee848b8e196b57de5860c78f`. The deterministic
`evidence-quality-v1` contract passed Backend Local Verification run `37188607802`.
AnalysisJob persistence/API exposure remains deferred to A7-4. A7-2 closed after PR #205 merged as
`9ced20a4e9fc7fb42c6499d08bf5d7c66052f6f6`; Backend Local Verification run `37191939296`
passed. A7-3 implementation PR #208 merged as `fdb0e8a3f7e6656c46dd24e173894460ec09fd7f`; corrective PR #209 merged as `94869668ef177370701adcaae7e5c5473b3076ac`. Backend Local Verification `37198612965` and Terraform Static Verification `37198612964` passed. A7-4 PR #213 merged as `ab1b1f410b6aabf674af51d6161a2efd0f6df6a4`; bounded repair commit `0e457f7bd9339fc44f901bdd90b2c95f40c081b0` resolved the one initial transaction-boundary test error, and final Backend Local Verification `37210008792` plus Terraform Static Verification `37210008786` passed. A7-5 PR #217 merged as `c5840c05c60ec78dc3a8c698453424b587014814`; Backend Local Verification `37215839871` and Terraform Static Verification `37215839970` passed. A7-6 selects repository-native observability for the current portfolio scope and defers OpenTelemetry/Langfuse behind explicit future triggers.

## 1. Why this extension exists

The repository already contains a direct false-green before-state.

A retained VPC evaluation trace recorded fact extraction PASS, retrieval PASS, generation PASS,
Terraform validation PASS, and no first divergence, while the same result had incomplete required
grounding:

- project-decision coverage: 0/1
- required resource-type coverage: 2/4

The generated Terraform still contained the expected major resources.

Therefore technical pipeline success did not prove that the result was grounded in the evidence
contract the project intended to rely on.

Case A later corrected this specific retrieval problem and passed canonical N=3 plus the frozen
holdout. This extension does not reopen that accepted result. It operationalizes the deeper lesson.

## 2. Final architecture direction

The extension is centered on **Authoritative RAG Evidence**, not on another LLM evaluator.

~~~text
architecture image
  -> AI fact extraction
  -> provider schema authority
  -> official provider docs/examples retrieval
  -> curated Terraformers project-decision retrieval
  -> evidence-backed generation
  -> generated-resource/evidence support check
  -> provider-schema check
  -> Terraform executable validation
  -> durable technical / knowledge / quality state
~~~

The runtime quality claim is conditional on the extracted architecture facts. Fact extraction itself
remains probabilistic and is calibrated with labeled canonical/holdout expectations.

## 3. Knowledge authority model

### 3.1 Provider schema

The pinned AWS Provider schema is deterministic authority for resource existence, attribute names,
required/optional/computed fields, field types, and nested block structure.

When the resource type is known, this is a direct lookup problem, not a vector-search problem.

### 3.2 Official provider docs/examples

Pinned official provider documentation and examples are authoritative RAG evidence for resource
semantics and usage.

Every generated corpus document must preserve provider version, provider source commit, source path,
document type, authority, stable document ID, corpus version, and checksum/provenance.

Official examples are not treated as the unique answer key for a user architecture. They are valid
usage evidence.

### 3.3 Terraformers project decisions

Repository-owned project decisions remain separate curated evidence for questions provider docs do
not answer alone, such as security-group relationships, private-origin choices, EKS/IRSA
relationships, and project-specific architecture constraints.

They remain manually reviewed and few.

## 4. Corpus problem and selected correction

### 4.1 Current limitation

terraformers-reference-v3 contains 128 documents, but v3 preserved the earlier curated content.
The current provider corpus is built from a hard-coded resource dictionary covering only a limited
subset of AWS resources.

The existing structure therefore cannot serve as a universal quality oracle.

### 4.2 New corpus direction

Create a new corpus identity, expected as terraformers-reference-v4.

Do not mutate v3; v3 remains historical Case A evidence.

The new compiler must remove the source-code requirement to add every supported resource to a
hard-coded RESOURCE_SPECS dictionary.

It must be able to:

1. inspect the pinned provider schema/source;
2. accept or discover arbitrary provider resource types;
3. validate each resource against provider schema;
4. locate official documentation deterministically;
5. generate bounded overview/example/schema evidence;
6. preserve stable provenance and sanitization;
7. generate machine-readable provider/corpus coverage reports.

At least one resource absent from v3 must be built and verified without editing compiler source.

### 4.3 Full versus bounded provider corpus

The architecture supports both bounded resource-set compilation and discovered broad/full-provider
compilation.

The actual v4 ingestion scope is selected from measured provider resource count, official
documentation availability, document/chunk count, embedding-call volume, index size, and ingestion
duration/cost.

This is a measured implementation decision inside the frozen architecture, not permission to return
to the old hand-curated thirty-resource allowlist.

If schema exists but official docs are unavailable, the state is a knowledge coverage gap, not an
AI failure.

## 5. Outcome state model

The AnalysisJob/result path must not overload one success field.

### Technical status

- PASS
- FAIL

### Knowledge status

- COMPLETE
- INCOMPLETE
- UNKNOWN
- NOT_APPLICABLE

### Quality status

- EVIDENCE_BACKED
- DEGRADED
- UNKNOWN
- NOT_APPLICABLE

### Contract version

The initial quality contract identity is evidence-quality-v1.

It is persisted with the result so later rule changes do not silently reinterpret historical jobs.

## 6. Failure/reason taxonomy

The system must distinguish mechanics instead of reporting one generic poor-quality bucket.

Minimum bounded reasons include:

- RESOURCE_UNKNOWN_TO_PROVIDER
- OFFICIAL_KNOWLEDGE_NOT_AVAILABLE
- REQUIRED_EVIDENCE_NOT_RETRIEVED
- REQUIRED_PROJECT_DECISION_NOT_RETRIEVED
- GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE
- PROVIDER_CONTENT_BLOCKED
- PROVIDER_OUTPUT_TRUNCATED
- PROVIDER_EMPTY_RESPONSE
- PROVIDER_TIMEOUT
- PROVIDER_RATE_LIMITED
- PROVIDER_ERROR
- PROVIDER_SCHEMA_FAILURE
- TERRAFORM_EXECUTABLE_FAILURE

Implementation may split a reason only when observed mechanics justify a smaller bounded taxonomy.
It may not replace these distinctions with free-text errors.

## 7. Runtime evidence-backed quality

### 7.1 Resource evidence

For extracted architecture resource types:

- check provider-schema existence;
- check official knowledge availability;
- record selected evidence coverage.

This creates explicit distinctions:

~~~text
schema exists + docs unavailable
  -> knowledge INCOMPLETE

docs available + required evidence not selected
  -> retrieval gap

evidence selected + generation adds unsupported resource
  -> generation evidence gap
~~~

### 7.2 Generated-output support

Generated resource types are matched deterministically against exact provider schema and selected
reference resourceTypes.

Do not ask the generation model to self-report citations.

### 7.3 Relationship/project-decision evidence

Resource coverage alone is not enough.

Project-decision/relationship evidence remains a separate dimension.

For frozen evaluation fixtures, exact required decision IDs are labeled truth.

For arbitrary runtime requests, a missing project decision becomes a failure only when applicability
can be derived deterministically. Otherwise that dimension remains UNKNOWN rather than being guessed.

## 8. Input-interpretation boundary

The system cannot prove from runtime evidence alone that image fact extraction captured every
component in the user's diagram.

Therefore runtime quality is evidence-backed quality conditional on extracted facts.

Canonical and holdout evaluation continue to test input classification, components, relationships,
resource types, retrieval expectations, and generation expectations.

No second LLM judge or multi-model voting layer is part of the default architecture.

## 9. False-green measurement

False green is defined only where labeled truth exists.

For a frozen evaluation fixture:

~~~text
false_green = technical_success AND NOT labeled_quality_success
~~~

labeled_quality_success uses the frozen expected classification, required resource grounding,
required project-decision grounding, required generated-resource coverage, forbidden-resource
absence, and accepted validation result.

Negative controls require expected classification/rejection and empty Terraform.

The implementation must re-express the historical VPC false-green before-state, final canonical N=3,
and frozen holdout without modifying the historical Case A result.

The labeled datasets serve as calibration/benchmark evidence for the runtime evidence model. They
are not required as production labels for each request.

## 10. Provider partial-failure model

A successful SDK/HTTP exchange does not imply usable AI output.

Current Vertex handling already distinguishes output truncation but can collapse other abnormal
finish reasons into a generic response-format failure.

The extension must distinguish bounded outcomes where available:

- normal completion
- output truncation
- provider content/safety block
- empty response
- timeout
- rate limit
- generic provider error

Provider-specific metadata stays inside the adapter. Application-facing state is provider-neutral.

The Case C run 36949479621 is not evidence of Vertex censorship. It was rejected by a
repository-owned hard-coded-sensitive-credential rule that PR #190 later removed.

Provider safety controls will not be weakened to make a test pass.

## 11. Executable correctness diagnostics

C2 run 37016993776 retained attempt 1 PASS and attempt 2
terraform_validate_configuration under one frozen runtime identity, but no raw failed HCL.

That was safe but too coarse for root-cause analysis.

The new direction keeps raw HCL private and derives bounded diagnostics from structured Terraform
JSON output.

Candidate classes are accepted only when supported by real/fixture diagnostics, for example missing
required argument, unsupported argument, invalid reference, invalid function argument, invalid
value/type, syntax/configuration, provider configuration/closure, and unknown/other.

The exact final taxonomy is implementation-derived.

Allowed retained evidence is bounded category, bounded error/warning counts, and optionally a
sanitized stable diagnostic fingerprint.

Forbidden retained evidence is raw generated HCL, credentials, arbitrary CLI output, and
prompt/reference payloads.

## 12. Runtime persistence and observability

The preferred product contract is additive persistence on the durable AnalysisJob/result boundary:

- technical status
- knowledge status
- quality status
- bounded reason
- quality-contract version

The existing result API should expose the bounded state with compatibility tests.

Metrics must remain low cardinality. Job/project/resource names do not become metric labels.

Job identity remains in durable state/log correlation.

A backend restart/replacement must not erase the quality state.

## 13. Observability-product decision

A new product is not preselected.

Compare:

1. repository-native Micrometer + evaluation artifacts
2. Langfuse
3. OpenTelemetry + existing metrics

Compare model-call trace visibility, quality-score attachment, job/release/config correlation, Java
integration, sensitive payload control, self-hosted/managed cost, extra GCP footprint, actual RCA
value, and portfolio explanatory value.

Langfuse, if selected, records and explores Terraformers-owned traces/scores. It is not the quality
oracle.

A product is adopted only if A7-0 through A7-5 leave a demonstrated observability gap.

## 14. Implementation sequence

### A7-0 — Authoritative knowledge coverage

Required:

- machine-report current v3 resource coverage;
- machine-report full pinned provider-schema resource universe;
- implement v4 compiler without per-resource source allowlist edits;
- prove at least one previously unsupported resource can be built without compiler-source change;
- report schema/docs/index coverage separately;
- preserve v3 unchanged;
- select bounded/broad v4 ingestion from measured size/cost.

### A7-1 — Evidence-backed quality contract

Required:

- separate technical, knowledge, and quality status;
- separate corpus gap from retrieval gap;
- deterministic generated-resource/evidence support;
- separate relationship/project-decision dimension;
- versioned durable quality representation;
- explicit conditional-on-extracted-facts boundary.

### A7-2 — False-green measurement and calibration

A7-2 was explicitly approved on 2026-10-04 and is complete. Its execution was isolated in
`.agents/work-packages/case-a-a7-2-false-green-calibration-v1.yml`; PR #205 merged as
`9ced20a4e9fc7fb42c6499d08bf5d7c66052f6f6`. The accepted implementation preserves the existing
retrieval-grounding report, adds `case-a-quality-calibration-v1`, and computes false green only from
frozen labeled truth. A7-1 runtime quality remains a comparison signal rather than ground truth.

The selected implementation boundary is repository-only: preserve the existing Case A grounding
report unchanged and add a parallel versioned calibration report that computes deterministic
`technicalSuccess`, `labeledQualitySuccess`, and
`falseGreen = technicalSuccess && !labeledQualitySuccess` from frozen dataset labels. A7-1 runtime
quality status, when available, is only a side-by-side calibration signal and never label truth.
Canonical/holdout datasets remain immutable and no live AI rerun is authorized.

Required:

- machine-represent historical false green;
- add technical success, labeled quality success, and false green to canonical/holdout outputs;
- preserve all previous Case A acceptance metrics;
- include negative-control semantics;
- compare runtime evidence status with labeled results.

### A7-3 — Provider partial failures

A7-3 was explicitly approved on 2026-10-04 and is complete. Execution is recorded in
`.agents/work-packages/case-a-a7-3-provider-partial-failures-v1.yml`. PR #208 merged as
`fdb0e8a3f7e6656c46dd24e173894460ec09fd7f`; its null-safety corrective PR #209 merged as
`94869668ef177370701adcaae7e5c5473b3076ac`. The accepted implementation extends the existing
provider-neutral failure contract, preserves timeout-only retry semantics, and adds no DB/API
persistence or live provider validation.

The selected boundary is classification only: explicit provider content/safety blocks, truncation,
empty successful responses, timeout, rate limiting, generic provider errors, and application
response-format failures remain distinct where deterministic provider evidence exists. Existing
timeout retry behavior is preserved; A7-3 does not make rate limiting retryable, add DB/API
persistence, weaken provider safety controls, or require live unsafe prompts.

Required:

- provider content block is not generic response-format failure;
- truncation/empty/timeout/rate limit/generic provider error stay distinct where observable;
- deterministic tests;
- no intentionally unsafe live prompt requirement.

### A7-4 — Durable runtime quality observability

**Status: COMPLETE — ACCEPTANCE PASS.**

A7-4 was explicitly approved on 2026-10-04 and is complete. Execution is recorded in
`.agents/work-packages/case-a-a7-4-durable-runtime-quality-observability-v1.yml`. PR #213 merged as
`ab1b1f410b6aabf674af51d6161a2efd0f6df6a4`; final Backend Local Verification
`37210008792` and Terraform Static Verification `37210008786` passed.

The selected decision is **persist bounded terminal quality snapshots instead of recomputing on API
read**. A7-1 `evidence-quality-v1` remains the quality contract. The snapshot is computed while
facts, selected references, and generated output are still available, then stored atomically with
the owned terminal AnalysisJob transition. Existing MariaDB, Micrometer, and MDC correlation are
reused; no new observability product is introduced.

A7-4 does not deploy or ingest v4, change prompts/models/retrieval, alter retry/lease/fencing, or
perform live provider/GCP actions. Official-knowledge availability must come from reproducible
A7-0 pinned coverage metadata, never from the current retrieval hits.

Required:

- persist technical/knowledge/quality/reason/version;
- expose through API;
- low-cardinality metrics;
- job-correlated logs;
- durability across backend restart/replacement.

### A7-5 — Safe executable diagnostics

**Status: COMPLETE — ACCEPTANCE PASS.**

A7-5 was explicitly approved on 2026-10-05 and is complete. Execution is recorded in
`.agents/work-packages/case-a-a7-5-safe-executable-diagnostics-v1.yml`. PR #217 merged as
`c5840c05c60ec78dc3a8c698453424b587014814`; Backend Local Verification
`37215839871` and Terraform Static Verification `37215839970` passed.

The selected decision is **bounded structured diagnostic reduction**. The existing
`TerraformValidationFailureException.Category.VALIDATE_CONFIGURATION`,
`terraform_validate_configuration` observability category, and A7-4
`TERRAFORM_EXECUTABLE_FAILURE` quality reason remain unchanged. Terraform `validate -json`
diagnostics are inspected only in memory and reduced to a finite enum/set plus bounded error/warning
counts before raw CLI output and the temporary workspace are deleted.

The exact final diagnostic taxonomy is fixture-derived. Unsupported/unmatched diagnostic text maps
to `UNKNOWN`; historical C2 run `37016993776` remains subtype-unknown and is not reconstructed.

A7-5 adds no DB migration, new API endpoint, workflow, model/retrieval change, live C2 rerun, GCP
action, or new observability product.

Required:

- parse bounded Terraform JSON diagnostic classes;
- preserve the existing top-level Terraform validation category;
- keep raw failed HCL and raw Terraform diagnostic text out of persistence/log/export paths;
- retain only bounded diagnostic enum/count evidence;
- show with a deterministic representative fixture that a recurrence of the C2 top-level class
  would produce more actionable evidence without claiming the historical exact invalid construct.

### A7-6 — Observability backend decision

**Status: DECISION IMPLEMENTED / AWAITING REVIEW.**

A7-6 is executed on main base `175de19a32d4aac906eabc0e222090cf0ddf9f69` under
`.agents/work-packages/case-a-a7-6-observability-backend-decision-v1.yml`.

The measured remaining gap is an **interactive unified trace UI**, not a missing correctness or
portfolio-evidence primitive. Current repository-native evidence already combines Micrometer stage /
failure / quality metrics, `analysisJobId` MDC plus source revision logs, durable AnalysisJob quality
state/API read-back, and deterministic evaluation/configuration/false-green artifacts.

Selected now: **repository-native observability**.

Deferred with explicit evidence triggers: **OpenTelemetry** as the preferred future
instrumentation/transport standard, and **Langfuse** if a dedicated LLM trace/score/release UI becomes
a demonstrated operator requirement.

A7-6 adds no dependency, exporter, runtime service, cloud resource, or new quality oracle.

Required:

- compare native/Langfuse/OTel against actual remaining gap;
- select or explicitly reject additional tooling;
- preserve explicit future adoption triggers rather than adopting tools for portfolio breadth.

Any dependency/runtime adoption requires a separate user decision.

### A7-7 — Representative GCP live proof

**Status: REQUIRED AFTER A7-6 MERGE — NOT STARTED.**

Repository-only evidence is explicitly insufficient for final closure. The improved Terraformers
must demonstrate that the broad v4 corpus is actually served by live retrieval and that the
A7-4 `evidence-quality-v1` snapshot is persisted and readable through the real AnalysisJob API.

Reuse the existing Case C representative topology; do not create a second architecture.

Minimum evidence:

1. exact source/image/corpus/quality-contract identity;
2. authenticated positive architecture request;
3. terminal AnalysisJob/result read-back;
4. persisted quality state visible through API;
5. RAG/quality metrics/log evidence;
6. authenticated negative control with no Terraform output;
7. naturally observed provider/executable failures retained rather than rerun away;
8. final reviewed teardown to zero known Terraformers billable resources.

The user's 2026-10-05 end-to-end requirement authorizes A7-7 in principle after A7-6 is merged and
an A7-7 bounded Work Package freezes the exact live actions. Actions outside that Work Package remain
separate checkpoints.

### A7-8 — Integrated closure

Close only when the project can explain with evidence:

- what authoritative knowledge exists;
- whether knowledge was missing versus retrieval failed;
- whether generation was supported by retrieved evidence;
- whether provider completion was actually usable;
- whether Terraform was executable;
- why a technically successful result was trusted/degraded/unknown;
- how runtime evidence compares against labeled canonical/holdout truth;
- why an observability product was selected or rejected;
- any live service proof and final teardown.

## 15. Secondary measurements

Latency and usage remain supporting evidence.

Where Vertex reliably exposes token/usage metadata, preserve it under the same run/config identity.

Do not fabricate unavailable token or cost values.

No independent cost-optimization case is created unless new measured evidence justifies it.

## 16. Explicitly rejected default directions

Do not add by default:

- LLM-as-a-judge
- multiple-model voting
- LangGraph
- dynamic-config dashboard
- runtime on-demand provider-document scraping/embedding
- a new vector database
- a second GCP architecture
- Grafana/Loki/Tempo merely for breadth
- safety-filter bypass
- raw failed HCL logging

Any reconsideration must satisfy the direction-change rule below.

## 17. Direction-change protocol

This section is the anti-drift rule for the extension.

The following axes are frozen by ADR-008:

1. authority hierarchy;
2. scalable corpus compiler;
3. no default evaluator LLM;
4. separate technical/knowledge/runtime-quality/labeled-quality states;
5. resource plus relationship/project-decision grounding;
6. explicit provider partial failures;
7. bounded safe Terraform diagnostics;
8. evidence-gated observability tool adoption;
9. optional reuse of the existing GCP topology only;
10. mandatory teardown after any recreated live runtime.

During implementation, a new article, framework, vendor product, or interesting technology is not
sufficient reason to change direction.

A direction change requires:

1. repository or live evidence that a frozen assumption is materially false or insufficient;
2. identification of the exact affected frozen axis;
3. a written ADR-008 amendment proposal;
4. explicit user approval before implementation follows the new direction.

A normal implementation bug is handled inside the current phase and does not reopen the architecture.

## 18. Stop rule

Stop the extension when:

- authoritative knowledge coverage is scalable and measured;
- false-green before/after is measurable;
- runtime evidence-backed quality is durable and observable;
- knowledge/retrieval/generation failures are distinguishable;
- provider partial failures are explicit;
- Terraform diagnostics are safe and actionable;
- observability tool decision is evidence-backed;
- required live proof, if any, is complete;
- final repository/portfolio documents are reconciled.

After A7-8, further AI framework or monitoring work requires a new operational requirement or new
contradictory evidence.
