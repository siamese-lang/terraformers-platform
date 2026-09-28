# M3-R3c — Live Spring serving-path smoke on the GCP target runtime

**Status: READY FOR STATIC IMPLEMENTATION — live execution is a later protected workflow step**

## Execution authorization

This is an explicitly authorized **non-live repository development task**. It may proceed without
waiting for another live GCP action.

Do not require GitHub CLI authentication, live GCP credentials, billing access, or a running cluster
to complete the static implementation and tests. If a validation step requires live Vertex/OpenSearch,
defer only that step and finish all local code/tests first.

## Read first

- `AGENTS.md`
- `docs/AI_PROJECT_STATE.md`
- `docs/plans/MASTER_PLAN.md`
- `docs/plans/active/M3-ai-evaluation-baseline.md`
- `docs/architecture/decisions/ADR-005-target-ai-rag-runtime.md`
- `evaluation/terraformers-eval-v1/README.md`
- `backend/src/main/java/com/terraformers/modernization/evaluation/EvaluationRunner.java`
- `backend/src/main/java/com/terraformers/modernization/evaluation/EvaluationDatasetLoader.java`
- `backend/src/main/java/com/terraformers/modernization/evaluation/EvaluationResultWriter.java`
- `backend/src/main/java/com/terraformers/modernization/reference/VertexArchitectureFactsExtractor.java`
- `backend/src/main/java/com/terraformers/modernization/reference/VertexEmbeddingProvider.java`
- `backend/src/main/java/com/terraformers/modernization/reference/opensearch/OpenSearchReferenceRetriever.java`
- `backend/src/main/java/com/terraformers/modernization/reference/RetrievalModeReferenceRetriever.java`
- `backend/src/main/java/com/terraformers/modernization/analysis/vertex/VertexGenerationStage.java`
- `backend/src/main/java/com/terraformers/modernization/analysis/TerraformDraftValidator.java`
- `backend/src/main/resources/application-gcp-target.yml`

## Live evidence already proven

Do not redo corpus ingestion in this task.

M3-R3b live run `36375171823` completed successfully at
`155b7293a7d7a8cbd6c2e69eb6b45d86c08b25e8`.

Its machine-readable receipt proved:

- corpus version: `terraformers-reference-v3`
- corpus checksum: `df8c198f0648827d754e1ef92ff4c07b1397e7dd36a06487eebfee1443ce892f`
- document count: `128`
- embedding model: `gemini-embedding-001`
- vector dimension: `1024`
- index: `terraformers-reference-v3`
- outcome: `ingested`
- representative k-NN hits existed
- ephemeral ingestion pod cleanup succeeded

## Problem

The target Vertex/OpenSearch runtime and v3 corpus are now live, but there is still no bounded live
execution path that proves the **existing Java serving pipeline** works end-to-end on that same
runtime.

The full Spring Boot application must **not** be deployed merely for this smoke because its normal
web runtime still carries unrelated MariaDB/JWT/object-storage dependencies outside M3-R3.

The repository already has the correct provider-neutral evaluation orchestration:
`EvaluationRunner`.

## Goal

Add the smallest repository-owned Java execution entry point that wires the **existing production
components** for the GCP target and runs a bounded live serving-path smoke through:

`fixture image → VertexArchitectureFactsExtractor → RetrievalQueryTextBuilder →
VertexEmbeddingProvider(RETRIEVAL_QUERY) → OpenSearchReferenceRetriever →
VertexGenerationStage → VertexResponseParser → TerraformDraftValidator → EvaluationTrace`

Do not create a second evaluator, alternate retrieval implementation, alternate prompt, alternate
parser, or alternate Terraform validator.

## Required implementation

Add a focused live-evaluation launcher/command under the existing backend/evaluation boundary.

The launcher must:

1. reuse `EvaluationDatasetLoader`, `EvaluationRunner`, and `EvaluationResultWriter`;
2. directly reuse the production target components listed above;
3. avoid starting the full web/JPA/security application context;
4. take runtime configuration from explicit CLI/environment inputs rather than silently hard-coding
   a second configuration source;
5. require the target identity:
   - analysis provider `vertex`
   - embedding provider `vertex`
   - retrieval mode `REQUIRED`
   - generation model `gemini-3.8-flash`
   - embedding model `gemini-embedding-001`
   - OpenSearch index `terraformers-reference-v3`
   - corpus `terraformers-reference-v3`
   - provider version `5.100.0`
   - vector dimension `1024`
   - top-K `8`
6. create one exact `EvaluationTrace.ConfigurationIdentity` from those effective values;
7. compute a deterministic configuration fingerprint from the effective configuration values, not
   from timestamps or environment-specific paths;
8. support:
   - a **single-case smoke mode** by case ID; and
   - a later **full-dataset mode** using the same runner/launcher;
9. write the existing machine-readable `EvaluationRunResult` schema with
   `EvaluationResultWriter`;
10. preserve all existing stage provenance:
    - extracted facts
    - retrieval query
    - ordered reference hit metadata
    - supplied reference IDs
    - generation classification/output
    - Terraform validation result
    - first divergence/failure category;
11. fail closed if the requested case ID does not exist or if target runtime configuration is
    inconsistent with the GCP target contract;
12. never log credentials, image bytes, raw embedding vectors, or full prompt contents.

## Wiring guidance

Do **not** add another Spring Boot application.

Prefer a small Java command/launcher that constructs only the required production objects:

- `ObjectMapper`
- `VertexRuntimeProperties`
- Google GenAI `Client`
- `VertexArchitectureFactsExtractor`
- `RetrievalQueryTextBuilder`
- `VertexEmbeddingProvider`
- `AnalysisRuntimeProperties`
- `OpenSearchKnnQueryBuilder`
- `OpenSearchResponseParser`
- `HttpOpenSearchTransport`
- `OpenSearchReferenceRetriever`
- `RetrievalModeReferenceRetriever`
- `VertexPromptBuilder`
- `VertexResponseParser`
- `VertexGenerationStage`
- `TerraformDraftValidator`
- `EvaluationRunner`

If a small factory/configuration object improves testability, that is acceptable, but do not create
parallel provider abstractions.

Use Application Default Credentials through the existing Google GenAI client behavior. Do not add
service-account keys.

## Smoke execution contract

Static implementation should make it possible for a later protected GitHub workflow to run the
launcher inside an ephemeral pod using the existing `terraformers-backend` KSA / Workload Identity
principal and internal OpenSearch service.

The first live smoke should run **one positive architecture case only**, preferably
`arch-vpc-three-tier`, because its purpose is runtime path readiness rather than quality scoring.

A live smoke is considered runtime-successful when:

- fact extraction executes;
- retrieval executes against `terraformers-reference-v3`;
- at least one ordered reference hit is captured;
- generation executes;
- response parsing completes;
- TerraformDraftValidator executes;
- one machine-readable trace file is produced.

The trace may still contain a quality failure against dataset expectations; that belongs to M3
baseline evidence and must **not** be hidden or rewritten. Runtime success and quality success are
different concepts.

## Offline validation

Add focused tests proving at minimum:

- the target configuration identity/fingerprint is deterministic;
- invalid provider/model/corpus/index/dimension/top-K values fail closed;
- single-case selection returns exactly the requested case;
- unknown case ID fails;
- full-dataset mode still uses all six fixed cases;
- the launcher uses the existing `EvaluationRunner` rather than duplicating stage orchestration;
- machine-readable output uses the existing schema;
- no full Spring Boot web/JPA/security context is required by the launcher;
- existing `EvaluationRunnerTest`, Vertex provider/parser tests, and retrieval tests remain green.

Do not add live-provider calls to unit tests.

## Non-goals

Do not:

- deploy the full backend;
- add MariaDB, JWT/IdP, S3/GCS/object-storage infrastructure;
- change prompts;
- change retrieval ranking/filtering;
- change corpus contents;
- change models;
- add rerankers/judge models;
- add LangChain/LangGraph/agents;
- add a second evaluation schema;
- run all six live cases in this static task;
- analyze baseline quality yet.

## Completion report

Report:

- base SHA;
- branch/head SHA;
- changed files;
- launcher execution contract;
- exact reused production components;
- deterministic configuration identity/fingerprint behavior;
- validation commands/results;
- confirmation that full backend/web/JPA/security startup is not required;
- unresolved live-only handoff items;
- immediate next single task.

If GitHub authentication is unavailable, complete code/tests locally and report the commit/diff.
Missing PR capability is not an implementation blocker.
