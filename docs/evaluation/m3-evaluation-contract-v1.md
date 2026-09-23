# M3 Evaluation Contract v1

## Purpose

This contract defines the fixed evaluation-case input and machine-readable stage provenance required
by M3. It does not change production AI behavior.

The goal is to explain an AI/RAG result from observable evidence:

`input → extracted facts → retrieval query → ranked references → generation → Terraform validation`

It deliberately does not capture or require hidden model chain-of-thought.

## Schema identity

- Schema version: `m3-evaluation-v1`
- Java case contract: `EvaluationCase`
- Java result contract: `EvaluationTrace`
- Stage wrapper: `EvaluationTrace.StageTrace<T>`

The first concrete dataset is `terraformers-eval-v1` under
[`evaluation/terraformers-eval-v1/`](../../evaluation/terraformers-eval-v1/). Dataset version
and schema version are separate so the same result contract can compare multiple fixed datasets.

## EvaluationCase

Each fixed case records:

- stable `caseId` and `datasetVersion`;
- fixture path, SHA-256, and content type;
- expected input classification;
- required/acceptable/forbidden components;
- required/acceptable/forbidden relationships;
- required/acceptable/forbidden Terraform resource-type candidates;
- retrieval expectations:
  - required reference IDs;
  - acceptable authorities;
  - required resource types;
  - required project-decision IDs;
  - forbidden risk tags;
- generation expectation:
  - whether Terraform is expected;
  - required/acceptable/forbidden generated resource types;
- application validation expectation;
- ambiguity/annotation notes.

Expectations may intentionally be sets rather than one exact string so M3-2 does not overfit a
single wording.

## EvaluationTrace

Each run/case result records exact input and configuration identity plus the following stage traces.

### FACT_EXTRACTION

Evidence:

- classification when the extraction boundary actually exposes one; `null` means the current extraction stage does not classify input;
- summary;
- components;
- relationships;
- Terraform resource-type candidates.

### RETRIEVAL

Evidence:

- exact query text;
- normalized resource-type filters;
- requested top-K;
- ordered hits.

Each hit preserves:

- rank and similarity score;
- document ID and title;
- authority and document type;
- source path;
- resource types;
- provider/corpus version;
- priority;
- risk tags.

This is the minimum evidence needed to answer **which data the RAG path actually retrieved**.

### GENERATION

Evidence:

- reference IDs actually supplied to generation;
- observed input classification and available classification confidence;
- returned summary/components/relationships/warnings;
- exact generated Terraform;
- generated resource types and module sources;
- stop reason;
- retry occurrence;
- input/output tokens and cost only when the provider exposes them reliably.

The contract does not fabricate missing usage/cost fields.

### VALIDATION

Evidence:

- `TerraformDraftValidator` outcome and reason;
- optional additional reproducible validation checks.

Additional validation is represented as named checks rather than new stage-specific fields.

## Common stage envelope

All stages use the same envelope:

- `stage`;
- `status`: `PASS`, `FAIL`, `BLOCKED`, or `NOT_RUN`;
- `latencyMs`;
- typed `evidence`;
- normalized `failures`.

A failed stage must have at least one `EvaluationFailure`. A passed stage cannot contain failures.
Failure objects always identify their stage and normalized failure category.

`BLOCKED` is intentionally distinct from `FAIL`. For example, unavailable live model credentials
in M3-4 must not be counted as an AI-quality failure.

## First observable divergence

`EvaluationTrace.firstDivergence` identifies the earliest stage with status `FAIL` and one failure
category present in that stage.

The contract rejects inconsistent traces where:

- a failed stage exists but `firstDivergence` is absent;
- `firstDivergence` points to a later failed stage;
- the declared category is not present in the earliest failed stage;
- a trace field uses the wrong stage enum.

This makes failure localization part of the reusable result contract rather than report-only
interpretation.

## Failure taxonomy v1

### Fact extraction

- `INPUT_CLASSIFICATION`
- `FACT_EXTRACTION_MISSING_COMPONENT`
- `FACT_EXTRACTION_UNSUPPORTED_COMPONENT`
- `RELATIONSHIP_EXTRACTION_MISSING`
- `RELATIONSHIP_EXTRACTION_INCORRECT`

### Retrieval

- `RETRIEVAL_QUERY_CONSTRUCTION`
- `RETRIEVAL_EMPTY`
- `RETRIEVAL_FAILURE`
- `RETRIEVAL_IRRELEVANT_EVIDENCE`
- `RETRIEVAL_WRONG_SCOPE`
- `PROJECT_DECISION_MISSED`

### Generation

- `GENERATION_UNSUPPORTED_COMPONENT`
- `GENERATION_UNGROUNDED_RESOURCE`
- `GENERATION_OMITTED_REQUIRED_ELEMENT`
- `RESPONSE_FORMAT`
- `OUTPUT_TRUNCATED`

### Validation/runtime

- `TERRAFORM_STRUCTURAL_VALIDATION`
- `PROVIDER_TIMEOUT`
- `PROVIDER_RUNTIME`

Provider/runtime categories may occur at the stage where the provider dependency was actually used.
The failure object therefore stores both stage and category.

## Examples represented by the contract

`EvaluationContractTest` proves that the same schema represents:

1. complete success with retrieval provenance;
2. fact-extraction failure;
3. retrieval relevance failure;
4. ungrounded generation even when structural Terraform validation passes;
5. Terraform validation failure after earlier stages pass.

This test protects the evaluation contract itself; it is not a new milestone workflow and does not
test document status strings.

## Fixed dataset

M3-2 added `terraformers-eval-v1`: four positive architecture diagrams, one ambiguous/cropped
diagram, and one non-architecture dashboard. `EvaluationDatasetLoader` verifies the repository
fixture path and SHA-256 before exposing input bytes.

## M3-3 handoff

M3-3 should load `terraformers-eval-v1` through the shared loader and emit `EvaluationTrace`
records from the existing application stages. It must not change prompts, retrieval ranking, corpus
contents, model selection, or production orchestration in order to make those cases pass.
