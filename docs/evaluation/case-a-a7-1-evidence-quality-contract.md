# Case A A7-1 — Evidence-backed Quality Contract

## Status

**COMPLETE — ACCEPTANCE PASS — A7-2 NOT STARTED**

Execution base:

`2efa4dcc1425feafac896a069315416949fa7ab5`

Contract identity:

`evidence-quality-v1`

A7-1 implements the deterministic quality-decision contract defined by ADR-008. It does not
persist new fields on `AnalysisJob`, change the result API, modify evaluation false-green outputs,
change provider failure classification, activate v4, or perform live cloud/model calls.

## Implemented representation

`EvidenceQualityAssessment` is an immutable JSON-serializable representation containing separate:

- technical status: `PASS | FAIL`
- knowledge status: `COMPLETE | INCOMPLETE | UNKNOWN | NOT_APPLICABLE`
- evidence-backed quality status: `EVIDENCE_BACKED | DEGRADED | UNKNOWN | NOT_APPLICABLE`
- project-decision status: `COMPLETE | INCOMPLETE | UNKNOWN | NOT_APPLICABLE`
- bounded reason vocabulary from ADR-008
- contract version: `evidence-quality-v1`
- runtime quality boundary: `CONDITIONAL_ON_EXTRACTED_FACTS`
- bounded resource/project-decision evidence details needed to explain the decision

The representation rejects any other contract version or runtime-quality boundary.

## Deterministic decision rules

`EvidenceQualityAssessor` uses only deterministic inputs and repository/provider metadata. It
does not call an LLM.

### Knowledge gap

For an extracted resource that exists in the provider schema but has no official knowledge:

~~~text
knowledge = INCOMPLETE
quality = UNKNOWN
reason = OFFICIAL_KNOWLEDGE_NOT_AVAILABLE
~~~

This is not reported as an AI-quality failure.

If an extracted resource is absent from the provider schema:

~~~text
knowledge = UNKNOWN
quality = UNKNOWN unless another deterministic degradation exists
reason = RESOURCE_UNKNOWN_TO_PROVIDER
~~~

### Retrieval gap

When official knowledge exists for an extracted resource but selected official provider evidence
does not cover it:

~~~text
knowledge = COMPLETE
quality = DEGRADED
reason = REQUIRED_EVIDENCE_NOT_RETRIEVED
~~~

Official evidence is counted only from selected `AWS_PROVIDER_DOC` /
`AWS_PROVIDER_EXAMPLE` documents with `authority=PROVIDER_DOCUMENTATION`. Provider-schema
documents and project-decision documents do not falsely satisfy this RAG-evidence dimension.

### Generated-resource support

Generated resource types are extracted through the existing
`GeneratedTerraformContractInspector` parser rather than a second Terraform parser.

Each generated type is checked independently against:

1. AWS Provider schema membership; and
2. selected official provider evidence resource types.

A generated type absent from provider schema produces
`RESOURCE_UNKNOWN_TO_PROVIDER`. A schema-valid generated type without selected official evidence
produces `GENERATED_RESOURCE_UNSUPPORTED_BY_EVIDENCE`.

### Project-decision grounding

Project-decision applicability is a separate dimension:

- applicability `UNKNOWN` -> project-decision status `UNKNOWN`; no fabricated missing-decision failure;
- `NOT_APPLICABLE` -> `NOT_APPLICABLE`;
- known applicable + all required `TERRAFORMERS_PATTERN` IDs selected -> `COMPLETE`;
- known applicable + required pattern missing -> `INCOMPLETE`,
  `REQUIRED_PROJECT_DECISION_NOT_RETRIEVED`.

A provider document with the same ID cannot satisfy a required project decision.

### Quality precedence

A known deterministic degradation wins over an unresolved/unknown dimension.

Without a known degradation:

- incomplete/unknown authoritative knowledge -> `quality=UNKNOWN`;
- unknown project-decision applicability -> `quality=UNKNOWN`;
- all applicable deterministic evidence dimensions complete -> `quality=EVIDENCE_BACKED`.

Technical `PASS/FAIL` remains an independent field and is not collapsed into the evidence-quality
status.

## Runtime interpretation boundary

Every assessment records:

`CONDITIONAL_ON_EXTRACTED_FACTS`

The contract therefore does not claim that runtime evidence proves the image fact extractor found
every component or relationship in arbitrary user input.

## Validation implemented

Focused tests cover:

1. official-knowledge absence -> knowledge gap, not quality failure;
2. available knowledge but absent selected evidence -> retrieval gap;
3. generated resource schema membership and selected-evidence support;
4. project-decision UNKNOWN versus deterministically missing;
5. provider documents cannot satisfy project-decision IDs;
6. non-architecture input -> NOT_APPLICABLE;
7. deterministic degradation precedence over unknown knowledge;
8. technical status independence;
9. JSON round trip preserving `evidence-quality-v1` and the runtime-quality boundary;
10. shared generated-resource extraction without adding a second Terraform parser.

Validation evidence:

- PR #202 merge SHA: `f9009ce8f9a744afee848b8e196b57de5860c78f`
- Backend Local Verification run `37188607802`: **SUCCESS**
  - `mvn clean test`
  - application package
  - MariaDB Flyway + Hibernate schema validation
  - canonical repository smoke queries
- Terraform Static Verification run `37188607799`: workflow **SUCCESS**
  - scope checks passed
  - terraform/RAG verification job was skipped because PR #202 changed no terraform/RAG paths
- PR #202 changed no corpus, RAG tooling, RAG tests, workflow, DB migration, API, infra, or live-cloud paths.

Independent acceptance review confirmed the implementation against the A7-1 Work Package after the
final compile correction.

## A7-4 persistence boundary

A7-1 defines the versioned representation and deterministic decision semantics only.

It does **not** add:

- Flyway migrations;
- `AnalysisJobEntity` columns;
- result API fields;
- durable restart/replacement behavior;
- new metrics/log correlation.

Those remain A7-4 responsibilities. A7-4 can persist this already-versioned contract rather than
redefining the quality rules.

## Non-claims

A7-1 does not claim:

- runtime jobs currently persist or expose the quality contract;
- false-green labeled evaluation has been added;
- Vertex/Bedrock partial failures have been reclassified;
- v4 corpus is embedded, ingested, or served;
- official-knowledge availability is dynamically discovered at runtime in this phase;
- an evaluator LLM or model-voting layer is required.

## Next gate

A7-1 is closed. A7-2 must not start automatically and requires separate user approval.
