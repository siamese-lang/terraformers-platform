# M3 — AI Evaluation Baseline

## Status

**ACTIVE**

M0 through M2 are complete. M3 establishes a repeatable, explainable AI/RAG quality baseline before
any prompt, retrieval, model, framework, or orchestration change is treated as an improvement.

The current task is **M3-R2 — Single target AI/RAG runtime foundation**. Static implementation is
complete and the live account/model pre-apply gate has passed except for one final duplicate-runtime
read-only check. M3-4 remains **WAITING_FOR_TARGET_RUNTIME** until M3-R2 and M3-R3 complete.

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

## Dependency-driven sequencing rule

M3-4 exposed a real dependency: the historical AWS live RAG runtime was intentionally deleted, while
the project target is GCP/open-source-oriented. Recreating AWS solely for evaluation, or creating a
throwaway GCP evaluation stack and later building the "real" deployment again, would duplicate
infrastructure and distort the modernization direction.

Therefore M3 uses this order:

`M3-1 → M3-2 → M3-3 → M3-R1 → M3-R2 → M3-R3 → M3-4 → M3-5 → M3-6`

M3-R2/R3 create the **actual target AI/RAG runtime foundation**. It is not disposable test
infrastructure. The same IaC, adapters, corpus/index, runtime identity, and provider configuration
are reused for M3-4 baseline, M4 improvements, later observability/failure verification, and M9
runtime closure. Local/CI/stub/Kind checks remain separate deterministic verification mechanisms
without becoming a second live environment.

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

**Status: DONE**

**Problem / gap.** The fixed dataset existed, but the current pipeline could not emit one complete
per-case provenance record and the generation step was private inside `BedrockAnalysisProvider`.

**Change boundary.** Added one reusable `EvaluationRunner`, `EvaluationRunResult`, and
`EvaluationResultWriter`. The runner consumes the M3-2 dataset and records fact extraction,
retrieval query/hits, generation classification/output, generated Terraform resource/module types,
and Terraform validation through the M3-1 `EvaluationTrace` contract.

To avoid a parallel AI implementation, the existing Bedrock model-call/retry/parse logic was
extracted into `AnalysisGenerationStage` / `BedrockGenerationStage`, and
`BedrockAnalysisProvider` now uses that same generation stage. `BedrockArchitectureFactsExtractor`
implements the small `ArchitectureFactsExtractor` boundary so the runner can reuse the same
extraction implementation. Prompt text, retrieval ranking, corpus contents, model selection, and
production business behavior are unchanged.

**Validation.** `EvaluationRunnerTest` executes all six `terraformers-eval-v1` cases with
deterministic stage doubles and writes one machine-readable run result. It proves retrieved document
metadata reaches generation provenance, matching ambiguous/non-architecture classifications skip
Terraform validation, and required-retrieval/classification failures are localized to the first
failed stage. Existing Bedrock provider/parser tests and the full backend regression suite passed
after the generation refactor. The existing M1 boundary check was minimally extended to classify
`BedrockGenerationStage` as an AWS-specific adapter; no new verifier or workflow was added.

**Completion evidence.** One runner API now produces one machine-readable result set for the fixed
dataset. Deterministic execution is covered by
`mvn -q -f backend/pom.xml -Dtest=EvaluationRunnerTest test`. M3-4 only needs to wire the current
live/provider configuration into these existing stage boundaries; it does not need a second
evaluator or per-stage script/workflow.

### M3-R1 — Target runtime evidence and capability decision

**Status: DONE**

**Problem / gap.** The project had no active target AI/RAG runtime, while M3-4 requires the runtime
that the project will actually keep using rather than an AWS reconstruction or disposable
evaluation stack.

**Decision.** [ADR-005](../../architecture/decisions/ADR-005-target-ai-rag-runtime.md) selects one
zonal GKE Standard target cluster, Vertex AI `gemini-3.8-flash` generation,
`gemini-embedding-001` at 1024 dimensions, OpenSearch OSS in the same cluster, and Workload
Identity Federation for GKE. M3-R3 will derive `terraformers-reference-v3` because the embedding
model identity changes while the curated document/provider content remains the same.

**Evidence.** [M3-R1 Target Runtime Evidence](../../evaluation/m3-r1-target-runtime-evidence.md)
maps every selected capability to an existing application port or runtime requirement, records the
last operator-observed Free Trial/quota constraints with their observation dates, compares rejected
and deferred alternatives, and records current public cost/compatibility evidence.

**Mutable-account gate.** M3-R1 does not claim that 2026-09-16 quota values are current forever.
M3-R2 must refresh project, billing, CPU/disk/GKE quota and Vertex model access before the first
resource-creating Terraform apply. If the refreshed values invalidate the accepted one-cluster
shape, amend ADR-005 before provisioning rather than creating a second environment.

**Completion evidence.** The target capability/product decisions and remaining mutable deployment
variables are explicit enough for M3-R2 implementation. No resource was created in M3-R1.

### M3-R2 — Single target AI/RAG runtime foundation

**Status: IN PROGRESS — STATIC FOUNDATION COMPLETE / LIVE PLAN PENDING**

**Problem / gap.** ADR-005 selected the target products, but the project still needed deployable
provider adapters and reusable GCP/OpenSearch infrastructure before the same runtime could serve
M3-4 and later milestones.

**Implemented static foundation.**

- added Vertex AI generation, architecture-facts, and query-embedding adapters behind the existing
  `AnalysisGenerationStage` / `AnalysisProvider`, `ArchitectureFactsExtractor`, and
  `EmbeddingProvider` boundaries;
- added explicit `VERTEX` provider selection while preserving the historical Bedrock adapters;
- added a provider-neutral HTTP `OpenSearchTransport` and explicit `HTTP` versus
  `AWS_SIGV4` transport selection, leaving the historical SigV4 implementation lazy and isolated;
- added the `prod,gcp-target` runtime profile for Vertex + REQUIRED OpenSearch retrieval and the
  reserved `terraformers-reference-v3` / 1024-dimensional embedding identity;
- added reusable Terraform for one zonal GKE Standard cluster, dedicated VPC/subnet, node
  `vm.max_map_count=262144`, Workload Identity Federation for GKE, and least-privilege Vertex AI
  project access for the backend Kubernetes ServiceAccount; and
- added one internal-only OpenSearch 3.8.0 StatefulSet/ClusterIP overlay in the same target cluster.
  It is single-node portfolio/runtime foundation, not an HA/SLA claim and not a separate evaluation
  environment; and
- changed the Free Trial operating default to an idle node pool size of 0, with
  `1 × e2-standard-2` activated only for live evidence sessions and returned to 0 afterward.

**Validation.** Existing Backend Local Verification compiles/packages the Google Gen AI SDK and
runs the backend regression suite. Existing Terraform Static Verification now includes
`infra/terraform/envs/gcp-target-runtime`. Existing runtime-contract verification renders the
`gcp-target` overlay and checks target provider selection, v3 identity, internal-only OpenSearch,
and absence of AWS region leakage. No new workflow or standalone verifier was added.

**Live pre-apply evidence.** [M3-R2 Live Readiness Evidence](../../evaluation/m3-r2-live-readiness-evidence.md)
records the 2026-09-24 fresh account check: Free Trial credit remains, billing is enabled,
`CPUS_ALL_REGIONS=12/0`, Seoul `E2_CPUS=8/0`, `INSTANCES=8/0`,
`DISKS_TOTAL_GB=2048/0`, `SSD_TOTAL_GB=250/0`, `e2-standard-2` is advertised in
`asia-northeast3-a`, GKE server config is reachable, required APIs are enabled, and minimal real
calls to both `gemini-3.8-flash` and `gemini-embedding-001` succeed. The evidence supports the
existing one-cluster Free Trial profile without a quota increase.

The readiness review also found that an unspecified PVC StorageClass could silently use GKE's
default balanced disk. The target now explicitly enables the GCE PD CSI driver and binds the
OpenSearch 15 GiB PVC to a repository-owned `pd-standard` StorageClass.

**Remaining live boundary.** **No GKE cluster/node/OpenSearch disk has been created.** One final
read-only duplicate-runtime check remains after enabling the GKE API. Then review the Terraform
plan with `node_count=1` before the first apply. Do not use a second environment or begin full v3
corpus ingestion.

**Completion evidence.** Static reusable target code/IaC and fresh account/model readiness evidence
are present, but M3-R2 remains incomplete until the approved target runtime is applied and its
minimum GKE/OpenSearch/Vertex live readiness is proven.

### M3-R3 — Corpus ingestion and serving-path smoke

**Status: TODO**

**Problem / gap.** A deployed runtime is not useful for RAG until the versioned corpus/index
contract and application-facing retrieval/model path work together.

**Change boundary.** Ingest the versioned corpus with the selected embedding/index contract and
connect the existing Spring Boot-facing ports to the same target runtime. Preserve corpus/version
identity and any embedding-dimension rebuild decision explicitly.

**Validation.** Prove a small serving-path smoke through the existing extraction/retrieval/generation
boundaries, including retrieval metadata/provenance. This smoke establishes runtime readiness; it
does not replace M3-4 quality evaluation.

**Completion evidence.** The single target runtime is ready for the unchanged
`terraformers-eval-v1` dataset.

### M3-4 — Current live/provider baseline

**Status: WAITING_FOR_TARGET_RUNTIME**

**Problem / gap.** There is still no measured live quality baseline for the current AI/RAG
implementation.

**Observed execution boundary.** The last committed real AWS compatibility configuration is
`prod,aws-compat` with `ANALYSIS_PROVIDER=bedrock`, `EMBEDDING_PROVIDER=bedrock`,
`RETRIEVAL_MODE=REQUIRED`, generation model
`global.anthropic.claude-sonnet-4-6`, embedding model
`amazon.titan-embed-text-v2:0`, corpus `terraformers-reference-v2`, AWS Provider
`5.100.0`, index `terraformers-reference-v1`, vector field `embedding`, content field
`content`, vector dimension 1024, top-K 8, signing service `aoss`, and region
`ap-northeast-2`.

Repository lifecycle evidence also proves that this runtime no longer exists. The final
project-scoped AWS closure records zero OpenSearch Serverless collections and zero Terraformers
runtime resources, and records the state bucket, project GitHub OIDC provider, and live roles as
deleted. Retained GitHub environment/variable/secret configuration contains stale AWS resource
identifiers and is not a usable live identity.

**Execution result.** No M3 evaluation case was executed against a live provider. All six
`terraformers-eval-v1` cases are **BLOCKED**, not failed. The canonical machine-readable status is
[`m3-live-baseline-status.json`](../../../evaluation/baselines/m3-live-baseline-status.json), and
the human-readable evidence is [M3 Live Baseline Blocker](../../evaluation/m3-live-baseline-blocker.md).

No stub, local retriever, alternate model, or different vector store was substituted because doing
so would no longer measure the current implemented RAG path. No AWS resource was recreated because
that would require explicit infrastructure/cost approval and would expand M3-4 into a live
redeployment task.

**Validation.** The blocker was established from current repository configuration plus the final
AWS zero-resource proof. The six case IDs in the blocker record are the complete
`terraformers-eval-v1` dataset. This is a readiness result, not an AI quality result.

**Completion evidence.** **NOT MET.** Machine-readable quality traces do not exist because there is
no executable live retrieval substrate. M3-5 must not start until this blocker is resolved and at
least one faithful live run is captured.

**Resume condition.** Complete M3-R1 through M3-R3 so the project's actual target runtime supplies
valid model/embedding access, the versioned corpus/index, and endpoint/identity/network configuration
required by the existing application ports. Historical AWS redeployment is not an unblock path.

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
