# M3 — AI Evaluation Baseline

## Status

**ACTIVE**

M0 through M2 are complete. M3 establishes a repeatable, explainable AI/RAG quality baseline before
any prompt, retrieval, model, framework, or orchestration change is treated as an improvement.

The first incomplete task is **M3-1 — Evaluation contract and stage-provenance schema**.

## Objective

Measure the current AI/RAG pipeline so that each evaluation case can answer all of the following
without guessing:

1. What architecture facts were extracted from the input?
2. What retrieval query was produced from those facts?
3. Which reference documents were retrieved, in what order, with what score and source metadata?
4. Which retrieved references were supplied to generation?
5. What components, relationships, warnings, and Terraform resources were generated?
6. What validation accepted or rejected the Terraform draft?
7. If the result is wrong, which observable stage first diverged from the expected result?

The explainability target is **observable provenance**, not hidden model chain-of-thought. M3 records
inputs, structured intermediate outputs, retrieval provenance, generated artifacts, validation
results, and failure classification. It does not ask a model to reveal private reasoning or treat
free-form rationale as authoritative evidence.

## Current implementation facts

Current `main` already contains a linear pipeline:

`source image → architecture facts → retrieval query → embedding/OpenSearch search → ranked ReferenceDocument list → prompt construction → model generation → response parse → TerraformDraftValidator → result storage`.

Reusable assets include:

- `BedrockArchitectureFactsExtractor` for bounded structured architecture facts;
- `RetrievalQueryTextBuilder` and `ReferenceQuery`;
- `EmbeddingProvider`, `ReferenceRetriever`, and the OpenSearch k-NN implementation;
- `ReferenceDocument`, which already carries id, score, document type, source path, provider/corpus
  version, authority, priority, resource types, and risk tags;
- `BedrockPromptBuilder`, which supplies retrieved references to generation;
- `AnalysisProvider`, `AnalysisResult`, and `TerraformDraftValidator`;
- versioned corpus `terraformers-reference-v2`.

The v2 corpus currently contains 128 documents: 90 provider-documentation entries, 30 provider-schema
entries, and 8 project-decision entries. Its recorded identity includes AWS Provider 5.100.0,
Titan Text Embeddings v2 at 1024 dimensions, and corpus version `terraformers-reference-v2`.
These are baseline facts, not an endorsement of AWS as the modernization deployment target.

## Observed evaluation gaps

Repository inspection shows the following gaps that M3 must measure before M4 changes behavior:

1. There is no fixed/versioned evaluation dataset.
2. Architecture facts are used to build retrieval input but are not preserved in the final result.
3. Retrieval query text/resource filters are not preserved as evaluation evidence.
4. `ReferenceDocument` contains score and source metadata, but `AnalysisResult` keeps only reference
   IDs, so score/authority/source/risk provenance is lost at the result boundary.
5. Existing logs can show retrieval hit count and IDs, but they do not provide a complete per-case
   chain from extracted facts through generated Terraform and validation.
6. There is no explicit mapping from generated Terraform resource types or claims back to retrieved
   evidence, so unsupported generation cannot currently be localized cleanly.
7. The current application validator is intentionally lightweight and does not by itself establish
   every level of Terraform syntax/provider-schema correctness.
8. There is no repeatable baseline report that separates extraction, retrieval, grounding,
   generation, and validation failures.

These are measurement gaps. M3 must not assume which stage is the dominant quality problem before
the fixed dataset is executed.

## Evaluation design principles

- Reuse the current Spring Boot-centered pipeline and provider-neutral ports first.
- Use one fixed/versioned dataset, one reusable runner, one machine-readable result model, and one
  baseline report. Do not create a workflow/script per evaluation stage.
- Preserve exact dataset version, corpus version, provider/model identity, configuration fingerprint,
  and source/input hash for each run.
- Prefer deterministic expectations and structural checks before introducing LLM-as-a-judge.
- Do not invent a target accuracy percentage before current behavior is measured.
- Separate a failed stage from downstream symptoms. For example, a bad Terraform resource caused by
  wrong extracted facts is classified at fact extraction before calling it a retrieval failure.
- Generated artifacts/logs from one run are not committed by default. Commit the fixed dataset,
  evaluation contract, and intentional baseline report; transient raw outputs can remain CI/local
  artifacts.
- No M3 task changes prompt content, retrieval ranking, corpus contents, model choice, or production
  architecture solely to improve a score. Those changes belong to M4 after failure evidence exists.

## Evaluation case contract

Each case must provide enough expected information to score stages independently. The concrete file
format is selected in M3-1, but the logical contract includes:

- stable case ID and dataset version;
- input fixture and SHA-256;
- input classification expectation: architecture / ambiguous / non-architecture;
- expected or acceptable components;
- expected or acceptable directed relationships;
- expected Terraform resource-type candidates where applicable;
- retrieval expectations expressed as required/acceptable reference IDs, authorities, resource
  types, project-decision coverage, or forbidden risky evidence where exact IDs would overfit;
- expected generation constraints, including resources that must not be invented;
- validation expectations;
- case notes describing ambiguity that should not be scored as a hard error.

The dataset must include positive architecture cases and negative/ambiguous cases so the baseline
does not reward generating Terraform for every image.

## Stage provenance record

The reusable runner must produce one stage-oriented record per case. At minimum it preserves:

### Input/configuration
- case ID, input hash, content type;
- dataset/corpus/provider versions;
- analysis provider, embedding provider, retrieval mode, top-K;
- generation and embedding model IDs when they are actually used;
- configuration fingerprint needed to compare runs.

### Fact extraction
- classification if available;
- extracted summary;
- components;
- relationships;
- resource-type candidates;
- stage status, latency, and normalized failure category.

### Retrieval
- exact query text and normalized resource-type filters;
- requested top-K;
- ordered hits containing document ID, score, title, authority, document type, source path,
  resource types, corpus/provider version, priority, and risk tags;
- stage status, latency, and normalized failure category.

### Generation
- IDs of references actually supplied to the model;
- structured components, relationships, warnings, and summary returned by the model;
- generated Terraform resource/module types;
- model stop reason/token usage/cost only when the provider exposes them reliably;
- stage status, latency, retry occurrence, and normalized failure category.

### Validation
- application `TerraformDraftValidator` result and reason;
- additional reproducible HCL/Terraform parsing or validation evidence only when the runner can
  execute it consistently without turning M3 into cloud deployment work;
- final stage status.

Raw image bytes, credentials, private keys, access tokens, or unrelated prompt payloads are not
required in the machine-readable result.

## Initial failure taxonomy

M3-1 may refine names, but the baseline must be able to distinguish at least these classes:

- input/classification failure;
- fact extraction missing component;
- fact extraction unsupported component;
- relationship extraction missing/incorrect;
- retrieval query construction failure;
- retrieval empty/failure;
- retrieval irrelevant or wrong-scope evidence;
- required project-decision evidence missed;
- generation unsupported/ungrounded component or Terraform resource;
- generation omitted supported required element;
- response/format/truncation failure;
- Terraform structural/validation failure;
- provider/timeout/runtime failure.

A downstream failure can record contributing stages, but the report must identify the **first
observable divergence** whenever evidence supports that conclusion.

## Work sequence

### M3-1 — Evaluation contract and stage-provenance schema

**Status: DONE**

**Problem / gap.** There was no stable contract describing what an evaluation case contains or what
evidence must be captured to explain a result.

**Change boundary.** Added provider-neutral `EvaluationCase`, `EvaluationTrace`, shared
`StageTrace<T>`, normalized stage/status/failure enums, and
[Evaluation Contract v1](../../evaluation/m3-evaluation-contract-v1.md). Production AI behavior,
prompt content, retrieval ranking, corpus contents, and model selection are unchanged.

**Validation.** `EvaluationContractTest` covers a normal success, fact-extraction failure, retrieval
relevance failure, ungrounded generation with structurally valid Terraform, and Terraform validation
failure. The common trace contract enforces that `firstDivergence` identifies the earliest failed
stage and a category present in that stage.

**Completion evidence.** One machine-readable contract now preserves input/config identity,
extracted facts, retrieval query, ordered reference provenance, generation evidence, validation
results, and normalized failure localization. No evaluator framework or new workflow was added.

### M3-2 — Fixed/versioned evaluation dataset

**Status: DONE**

**Problem / gap.** There was no fixed set of inputs with stage-level expectations.

**Change boundary.** Added repository-owned `terraformers-eval-v1` with six fixed WebP fixtures:
four positive architecture cases, one cropped/ambiguous case, and one non-architecture dashboard.
The positive cases cover VPC three-tier, CloudFront/private-ALB, private AOSS, and S3/RDB metadata
split behavior and require the relevant versioned `PROJECT_DECISION` evidence where applicable.
No prompt, corpus, retrieval algorithm, model, or production orchestration behavior changed.

Added `EvaluationDataset` and `EvaluationDatasetLoader` as dataset-loading contracts only. The
loader verifies case/schema identity, unique case IDs, safe relative fixture paths, fixture
existence, and exact fixture SHA-256 before exposing bytes to the later runner.

**Validation.** `EvaluationDatasetLoaderTest` loads all six cases, verifies the WebP fixture bytes
through their recorded hashes, confirms four architecture / one ambiguous / one non-architecture
classification mix, checks positive versus negative stage expectations, and confirms every required
project-decision ID exists in `terraformers-reference-v2`.

**Completion evidence.** [Terraformers Evaluation Dataset v1](../../../evaluation/terraformers-eval-v1/README.md)
is fixed and loadable deterministically by one reusable loader using the M3-1 contract.

### M3-3 — Reusable evaluation runner and provenance capture

**Status: TODO**

**Problem / gap.** The current pipeline cannot emit a complete per-case provenance record.

**Change boundary.** Implement one reusable evaluation runner and the minimum instrumentation/hooks
needed to capture the stage-provenance record. Reuse `AnalysisProvider`, `EmbeddingProvider`,
`ReferenceRetriever`, corpus contracts, prompt builder, and Terraform validator rather than
reimplementing the application in a second service.

The evaluation path may add evaluation-only data structures or adapters, but it must not make a
persistent Python AI service a runtime dependency.

**Validation.** Run deterministic fixture/stub cases proving that the runner records stage inputs and
outputs in order and assigns a failure to the correct first observable stage.

**Completion evidence.** One command can execute the fixed dataset and emit one machine-readable
result set using the selected configuration.

### M3-4 — Current live/provider baseline

**Status: TODO**

**Problem / gap.** There is still no measured quality baseline for the current AI/RAG implementation.

**Change boundary.** Execute the M3 dataset against the currently selected real AI/retrieval path
when the required credentials/resources and cost approval are available. Record exact model,
embedding, corpus, retrieval, and configuration identity. Do not change behavior during the run.

If a required live dependency is unavailable, record the exact blocker rather than silently
substituting another provider/model or fabricating scores.

**Validation.** Re-run the same dataset/configuration sufficiently to distinguish deterministic
contract failures from provider variability. Preserve each run identity; do not average away
case-level failures before classification.

**Completion evidence.** Machine-readable baseline results exist for every executable case, with
blocked/not-covered cases explicitly distinguished from failures.

### M3-5 — Baseline metrics, provenance review, and failure taxonomy

**Status: TODO**

**Problem / gap.** Raw results alone do not explain which AI/RAG stage needs improvement.

**Change boundary.** Produce per-case and aggregate baseline findings for component/relationship
extraction, retrieval relevance/success, project-decision evidence coverage, unsupported generation,
Terraform validation, latency, and available token/cost data. Use deterministic scoring where the
case contract supports it; human-review or judge-based scoring must be clearly separated.

For each failed case, identify the first observable divergent stage and show the supporting
provenance: extracted facts, retrieval query, retrieved evidence, generated resources, and
validation result.

**Validation.** Every reported failure category must trace back to machine-readable case evidence.
Aggregate numbers must be reproducible from the result file.

**Completion evidence.** A baseline report identifies concrete failure classes that M4 can target,
without yet choosing the remedy.

### M3-6 — Framework decision checkpoint and M3 closure

**Status: TODO**

**Problem / gap.** LangChain, LangGraph, rerankers, judge frameworks, or other AI tooling should not
be adopted merely because they are common.

**Change boundary.** Review M3 evidence against ADR-004. Record whether each proposed framework is
`DEFER` or has enough evidence to be considered in M4.

- **LangGraph** is justified only if measured failures require explicit branching, repair/retry
  states, checkpointed AI workflow state, or control flow that the current linear Java orchestration
  cannot express cleanly.
- **LangChain** is justified only if a concrete measured gap is materially reduced by its retrieval,
  evaluation, or model integration abstractions without replacing stable project ports merely for
  framework conformity.
- If the problem is only provenance/tracing, keep the provider-neutral stage record and evaluate a
  tracing/evaluation backend separately rather than rewriting orchestration.
- Any LangGraph/LangChain adoption that changes architecture requires the ADR-004 gate and, where
  appropriate, a dedicated ADR before implementation.

**Validation.** Confirm the dataset, runner, machine-readable results, baseline report, and failure
taxonomy satisfy the M3 exit criteria. Do not build a closure-specific verifier.

**Completion evidence.** M3 is complete when current quality is repeatably measurable and at least
one meaningful failure class (or an evidence-backed finding that a suspected failure is not present)
is ready for M4 decision-making.

## Metrics and scoring boundary

M3 does not set an arbitrary pass threshold. Candidate deterministic measures include:

- expected component coverage and unsupported-component count;
- expected relationship coverage and unsupported-relationship count;
- required-reference hit/coverage and retrieved-evidence relevance by case contract;
- project-decision retrieval coverage where applicable;
- unsupported generated Terraform resource count;
- generation/validation success;
- per-stage and end-to-end latency;
- provider-reported token/cost data when available.

Exact formulas are finalized only after M3-1 defines case semantics. A single composite score is not
required and must not hide which stage failed.

## LangChain / LangGraph position at M3 activation

M3 does **not** reject these tools. It also does not add them at activation.

Current repository evidence shows a mostly linear pipeline with existing Java provider/retrieval
ports. That is enough to build the baseline without an orchestration rewrite. LangGraph is designed
for explicit stateful, branching workflows, which may become relevant if M3 exposes repair/retry or
routing problems. Evaluation tooling can also be introduced later if it solves a measured gap.

The decision therefore remains evidence-driven:

`baseline failure → root cause → tool/architecture decision → same-dataset comparison`.

## Exit criteria

M3 is **COMPLETE** only when:

1. A fixed/versioned evaluation dataset exists.
2. Dataset cases preserve stable input identity and stage-level expectations.
3. One reusable runner executes the dataset without separate per-stage workflows/scripts.
4. Each case emits a machine-readable provenance record across extraction, retrieval, generation,
   and validation.
5. Retrieved references retain source/score/authority metadata needed to explain what data informed
   generation.
6. Baseline results can distinguish first observable failure stage rather than only final success or
   failure.
7. Component and relationship extraction are evaluated.
8. Retrieval success/relevance and project-decision coverage are evaluated where applicable.
9. Unsupported/ungrounded generation is evaluated from observable evidence.
10. Terraform generation/validation outcome is evaluated.
11. Runtime/model/corpus/configuration identity is preserved.
12. Latency and available usage/cost information are recorded without inventing missing data.
13. A reproducible baseline report and failure taxonomy exist.
14. Framework adoption remains deferred unless M3 evidence passes the project change gate.
15. M4 receives one or more evidence-backed failure classes to investigate, or an explicit finding
    that a suspected class was not reproduced.

## Explicitly out of scope

- prompt/retrieval/model tuning to improve M3 scores;
- changing corpus contents because an individual baseline case fails;
- LangGraph/LangChain rewrite before failure evidence;
- agent or multi-agent architecture;
- persistent Python AI worker/service;
- arbitrary new vector database or reranker;
- production observability backend selection;
- GCP runtime deployment;
- reliability/recovery changes belonging to M5/M6.

## Evidence and references

- [Repository working contract](../../../AGENTS.md)
- [AI project state](../../AI_PROJECT_STATE.md)
- [Modernization master plan](../MASTER_PLAN.md)
- [ADR-003: Require evaluation before AI/RAG complexity](../../architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Gate architectural changes on reproducible evidence](../../architecture/decisions/ADR-004-change-gates.md)
- [Completed M2 plan](M2-runtime-parity.md)
- [M2 closure evidence](../../verification/m2-runtime-parity-closure.md)
- [Reference retrieval](../../reference-retrieval.md)
- [Bedrock provider](../../bedrock-provider.md)
- `corpus/terraformers-reference/v2/`
