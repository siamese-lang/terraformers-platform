# AI Project State

This checkpoint lets a new conversation or agent resume from repository evidence without guessing. It records current state, not an implementation guide or a new architecture decision.

## Repository

- Repository: `siamese-lang/terraformers-platform`
- Default branch: `main`
- M0 closure evidence SHA: `3ccf17582ae91ad131d3efe8ce1a38492c401c72`

M0 closure was validated against this main SHA. Current `main` may differ after merge, so every new task must verify GitHub `main` again rather than treating this SHA as permanently current. It is not this PR's head SHA or a predicted merge SHA.

## Current milestone

- Milestone: **M3 — AI Evaluation Baseline**
- Status: **ACTIVE**
- Phase: M3-R3 live corpus ingestion and serving-path smoke
- Active plan: [M3 — AI Evaluation Baseline](plans/active/M3-ai-evaluation-baseline.md)
- Delivery prerequisite proposal: [M3 GCP Delivery Automation](plans/m3-gcp-delivery-automation.md)
- Current implementation task: **M3-R3b — Live ingest terraformers-reference-v3 on the proven target runtime**

M0, M1, and M2 are complete. M3-1 defined the stage-provenance contract, M3-2 fixed the first
repository-owned evaluation dataset, and M3-3 added one reusable runner. M3-R2 now has a corrected
main-branch GitHub Terraform plan (run `36335535056`, commit
`fc95a7f54d375dce900bac573dcd79ea0215750f`) with exactly 12 creates and no
update/delete/replacement. The separate apply trust boundary is also proven: `terraformers-apply`
was bootstrapped with the reviewed role matrix, `gcp-target-apply` is protected and main-only, and
identity-check run `36363635464` completed successfully on
`334dc611dd44eccb286739d95c193dd3ffeedbb3`. The automated mutable preflight is also now proven:
run `36365247935` on `40750799aba5bf531ee939e0c50fe2b8042c1dd3` passed the plan-identity
non-mutation check, mutable quota/API/machine/duplicate-runtime checks, state-bucket protection,
empty canonical runtime state, and the corrected exact 12-create foundation contract; its apply job
was skipped. Cloud Billing API remains disabled, so billing status and remaining Free Trial credit
remain an operator-owned approval fact rather than a reason to broaden the plan identity.

The protected foundation run `36365702742` then applied the reviewed plan successfully:
`12 added, 0 changed, 0 destroyed`. Its final workflow status was failure only because the
post-apply check read deprecated GKE `Cluster.currentNodeCount`, which returned blank after the
cluster and node pool had already been created. The verification was corrected without re-applying
resources. Read-only runtime-check run `36369764529` on
`d47c85526f2b2a290b394084cbb19e0356883a0c` passed with the canonical 12 managed Terraform
resources, GKE cluster/node-pool runtime checks, Terraform `node_count=1`, and a zero-drift refresh
plan; its apply job was skipped.

The first OpenSearch readiness run `36370511427` then exposed the missing Kubernetes-object
permission before mutation. After the separately approved `roles/container.developer` addition,
readiness run `36371424659` on `31785eb0f9f1afc83584412c1e1ad8c011fc3b74` completed
successfully: the namespace/StorageClass/Service/StatefulSet were applied, the single replica became
Ready, the 15 GiB PVC was Bound to `terraformers-pd-standard`, the Service remained
`ClusterIP`, and the OpenSearch 3.8.0 API reached yellow/green health. M3-R2 is therefore
complete on the single target runtime. Historical AWS live
infrastructure is intentionally absent, so M3-4 is **WAITING_FOR_TARGET_RUNTIME**, not a request to
recreate AWS. M3-R1 through M3-R3 now establish the actual GCP/open-source-oriented target AI/RAG
runtime once; that same runtime is reused by M3-4, M4, later observability/failure work, and M9
closure.

## Completed

- [Repository working contract](../AGENTS.md)
- [Component inventory](architecture/component-inventory.md)
- [ADR-001: Project scope](architecture/decisions/ADR-001-project-scope.md)
- [ADR-002: Cloud-neutral boundaries](architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Evaluation before complexity](architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Evidence-based change gates](architecture/decisions/ADR-004-change-gates.md)
- [ADR-005: Target AI/RAG runtime](architecture/decisions/ADR-005-target-ai-rag-runtime.md)
- [Provider-neutral target architecture](architecture/target-architecture.md)
- [GCP deployment architecture and capability mapping](architecture/deployment-gcp.md)
- [Modernization master plan](plans/MASTER_PLAN.md)
- [Active M0 plan](plans/active/M0-baseline-and-governance.md)
- [AI project state](AI_PROJECT_STATE.md)
- M0 final cross-document consistency validation — **PASS**
- M0 closure evidence SHA — `3ccf17582ae91ad131d3efe8ce1a38492c401c72`
- M1-1 Backend external identity neutralization — **COMPLETE**
  - neutral provider-plus-subject persistence and lookup with an additive compatibility migration;
  - existing Cognito user linkage and internal numeric `user_id` preserved; and
  - Backend Local Verification and MariaDB schema/repository validation — **PASS** at `edc9eb87e9bb1105c4f5d94f117356768190db92`.
- M1-2 Backend JWT / Resource Server boundary — **COMPLETE**
  - generic resource-server wiring separated from provider-specific JWT validation;
  - Cognito token validation and JWT claim interpretation isolated behind provider boundaries while preserving current token/user compatibility; and
  - Backend Local Verification, including MariaDB schema/repository validation — **PASS** at `9b32f0ded961da74e5a83f4af7ac80b91872cc81`.
- M1-3 Frontend auth/session boundary — **COMPLETE**
  - application/UI auth calls now use a provider-neutral frontend auth client;
  - Cognito/Amplify configuration, guest-error normalization, user attributes, token objects, and provider calls are isolated in the compatibility adapter while existing session/routing/API/auth-flow semantics are preserved; and
  - Frontend CI and Frontend Delivery Contract Verification — **PASS** at `1e72c4d9320f3e4af82aeede8fb4c63366e80de9`.
- M1-4 Object storage decoupling completion — **COMPLETE**
  - source reads and upload/result writes now flow through provider-neutral `ObjectReader`/`ObjectWriter` contracts;
  - AWS S3 SDK types are isolated to compatibility adapters, while explicit provider/persistence semantics eliminate eTag-based false-S3 classification; and
  - Backend Local Verification and MariaDB schema/repository validation — **PASS** at `ca7f60382143a25a010ec49131075011c76c7c1f`.
- M1-5 OpenSearch transport/auth boundary — **COMPLETE**
  - `OpenSearchReferenceRetriever` now depends on provider-neutral `OpenSearchTransport` and no longer knows SigV4 or signing service names;
  - AWS credentials, region, payload signing, and `aoss`/`es` signing semantics remain inside the `SignedOpenSearchHttpClient` compatibility adapter, while retrieval/query/parser behavior is preserved; and
  - Backend Local Verification, MariaDB schema/repository validation, and Terraform Static Verification — **PASS** at `7d25243f0ee727d251d21ff5affeb9841df5fc3e`.
- M1-6 Model / embedding provider configuration — **COMPLETE**
  - generic analysis and embedding provider selectors now choose implementations behind the existing `AnalysisProvider` and `EmbeddingProvider` ports;
  - Bedrock generation/embedding model identifiers and max-token settings are isolated in `BedrockRuntimeProperties`, with legacy `BEDROCK_PROVIDER_ENABLED` retained only as a transitional fallback; and
  - Backend Local Verification, MariaDB schema/repository validation, Terraform Static Verification, and AWS Deployment Contract Inventory Verification — **PASS** at `631fefe9ebae72becc66d33a9a672e9ffcb36bb2`.
- M1-7 Runtime configuration neutralization — **COMPLETE**
  - canonical production/runtime configuration now uses provider-neutral JWT, storage, analysis, embedding, retrieval, and progress-publisher selectors, while Cognito/S3/Bedrock/SQS/AWS OpenSearch signing values are isolated in the explicit `aws-compat` profile;
  - canonical Kubernetes/runtime-secret/deployment contracts were updated to neutral base keys, legacy Bedrock/S3/SQS enable switches were removed from the canonical selection contract, and historical AWS compatibility remains explicitly preserved; and
  - Backend Local Verification, MariaDB schema/repository validation, AWS Deployment Contract Inventory Verification, AWS Runtime Deployment Package Verification, and Terraform Static Verification — **PASS** at `b4e3c4cb0cf42e64904111447395f7a1435ca9aa`.
- M1-8 Contract/regression verification and closure — **COMPLETE**
  - dedicated closure verification passed provider-neutral boundary inspection plus full backend, MariaDB/Flyway/repository, frontend, and runtime-contract regressions;
  - closure inspection removed provider-specific failure types from the generic analysis lifecycle and preserved failure-message plus observability-category semantics through provider-neutral failure signals; and
  - M1 Cloud Decoupling Closure Verification, Backend Local Verification, Terraform Static Verification, and AWS Deployment Contract Inventory Verification — **PASS** at `d843fb9f08cd6255c1a4738ac220a40b5e760d75`.
- M2-1 Portable runtime baseline and parity gap inventory — **COMPLETE**
  - backend regression, MariaDB 11.4 + Flyway/schema/repository validation, frontend tests/build, and deterministic runtime-contract verification were **PASS** as distinct runtime identities;
  - Kind cluster creation and backend image build/load reached workload application, which then **FAIL**ed because namespace `terraformers-local` did not exist; downstream rollout/HTTP/auth/object-byte/active-provider paths remain **NOT COVERED** rather than inferred; and
  - M2 Runtime Parity Baseline evidence collection — **PASS** at `2623303347e15e580d83f583262c51d2573edbbd`, with the target Kind runtime failure preserved in the uploaded evidence.
- M2-2 Portable persistent runtime substrate — **COMPLETE**
  - added a separate self-contained `terraformers-portable` Kind fixture with MariaDB 11.4, canonical `prod` backend configuration, stub/disabled cloud adapters, fixture-only credentials, and no live cloud credentials;
  - namespace/apply, MariaDB readiness, backend rollout/readiness, health, Flyway schema history, and the existing repository smoke all passed against the same in-cluster MariaDB instance; and
  - M2 Portable Persistent Runtime Verification — **PASS** at `02a00efec167a92c8170e8787b68e28ce6dc2339`. The fixture deliberately does not claim DB restart durability, authentication/ownership, object-byte persistence, active provider behavior, or production topology.
- M2-3 Authenticated identity and ownership parity — **COMPLETE**
  - added a separate `portable-authenticated` Kind fixture with ephemeral RSA signing material and an in-cluster JWKS server, exercising the real JWT decoder/provider validator and existing external-identity mapping without a live IdP or cloud credential;
  - provider-plus-subject persistence, numeric internal user creation/reuse, distinct second identity, anonymous and invalid-token 401 rejection, owner/private access, non-owner 403 rejection, ownership modification checks, and display-name preservation all passed against the same portable MariaDB runtime; and
  - M2 Authenticated Identity Parity Verification — **PASS** at `eda4b9469f258b71aae8e28a0b3ec1a226c4412a`. Historical fixture/harness failures were fixed and rerun before closure.
- M2-4 Upload → analysis → Terraform result parity — **COMPLETE**
  - PR #24 first reproduced the current metadata-only limitation under an authenticated runtime: upload, analysis, Terraform validation, and inline result read-back passed, while source/result object bytes were not persisted and source reads returned 409;
  - added a JDK-only `filesystem` implementation behind the existing neutral `ObjectReader`/`ObjectWriter` ports and enabled it only in the `portable-object-store` test overlay, without selecting a production storage product; and
  - M2 Object Byte Storage Verification — **PASS** at `99fa2864ae3ef86536332172c4a3e50be612e686`: the same 68-byte source input round-tripped exactly, source/result rows recorded `binary_persisted=1`, source endpoints returned 200, the analysis job reached `SUCCEEDED`, and generated Terraform filesystem bytes matched the database/API checksum. The fixture is single-runtime evidence only and makes no restart/shared-storage durability claim.
- M2-5 User/project/comment and frontend experience parity — **COMPLETE**
  - the authoritative portable runtime matrix passed project list/get, private/public authorization, Terraform read/update/read-back, project tree, canonical and frontend-compatible comments, authenticated attribution, and deletion;
  - frontend Jest regression, production build, and built entrypoint passed, while the baseline isolated three provider-specific user-visible strings and classified browser E2E as not required for M2; and
  - the frontend-only follow-up at `7d8cdf357cb16a9073c86c6eb5140e617219180d` removed the Cognito/Bedrock/`s3://` presentation leakage without changing APIs or provider selection. M2 User Experience Baseline Verification run #5 then passed with all three provider-neutral classifications `PASS`, `known_provider_specific_visible_copy=0`, and `first_confirmed_gap=none`.
- M2-6 Runtime parity closure — **COMPLETE**
  - reviewed the accepted M2-1 through M2-5 evidence against all 14 M2 exit criteria without adding a closure-specific verifier or workflow;
  - confirmed no subsequent production/runtime change invalidated the latest successful M2-5 claims; and
  - [M2 Runtime Parity Closure](verification/m2-runtime-parity-closure.md) records **PASS** with explicit residual limitations.
- M3-1 Evaluation contract and stage-provenance schema — **COMPLETE**
  - added one provider-neutral case/result contract for extraction, retrieval, generation, and validation provenance;
  - retained retrieved document score/source/authority/risk metadata and explicit first-observable-divergence classification; and
  - [M3 Evaluation Contract v1](evaluation/m3-evaluation-contract-v1.md) plus `EvaluationContractTest` cover success and four representative failure locations without changing production AI behavior.
- M3-2 Fixed/versioned evaluation dataset — **COMPLETE**
  - added `terraformers-eval-v1` with four positive architecture cases, one ambiguous case, and one non-architecture case;
  - fixed input identity with repository-owned WebP fixtures and SHA-256, plus stage-level extraction/retrieval/generation/validation expectations;
  - required five existing `PROJECT_DECISION` documents across positive cases so retrieval quality can be distinguished from generic vector hits; and
  - `EvaluationDatasetLoaderTest` validates deterministic loading, fixture identity, case composition, expectation completeness, and corpus-reference existence.

- M3-3 Reusable evaluation runner and provenance capture — **COMPLETE**
  - added one runner/result/writer path that emits M3-1 stage provenance for the fixed dataset;
  - extracted Bedrock generation into a shared `AnalysisGenerationStage` so production and evaluation use the same model-call/retry path; and
  - full backend regression and deterministic runner cases passed, including retrieval provenance, input classification, validation, and first-divergence localization.
- M3-R1 Target runtime evidence and capability decision — **COMPLETE**
  - selected one reusable GKE Standard + Vertex AI + OpenSearch OSS target runtime rather than an evaluation-only cloud stack;
  - retained the existing 1024-dimensional retrieval contract with `gemini-embedding-001` and required a new immutable `terraformers-reference-v3` embedding identity; and
  - recorded a mandatory fresh billing/quota/model-access check before M3-R2 creates resources.


## Verified architectural direction

This project is **not** a greenfield rewrite, a simple AWS-to-GCP migration, or a technology-count expansion exercise. It **is** modernization of the existing Terraformers system through reuse of existing domain/business flows, a provider-neutral logical architecture, a current GCP deployment target, reproduced-failure-based backend reliability work, fixed-evaluation-based AI/RAG work, actual-RCA-based observability, and cloud portability. The Spring Boot application remains the logical center.

Reuse the domain/project/user/file/comment flow, `AnalysisJob` lifecycle baseline, MariaDB + Flyway, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, Terraform draft validation, versioned RAG corpus/contract, Kubernetes-compatible workload contract, and deterministic test/validation assets.

## Current gaps

Current M3 gaps after the fresh M3-R2 readiness check:

- the reusable Vertex generation/embedding adapters, GKE/OpenSearch Terraform, private OpenSearch
  transport, Workload Identity IAM, and Free Trial operating profile are implemented;
- 2026-09-24 live evidence confirms billing + remaining Free Trial credit, sufficient CPU/instance/
  disk quota, Seoul `e2-standard-2` availability, GKE server availability, and successful minimal
  calls to both selected Vertex models;
- the final read-only duplicate-runtime check passed on 2026-09-24 (no GKE cluster or target VM);
- the target cluster/OpenSearch disk has not yet been created, so M3-R2 live readiness is not
  complete;
- `terraformers-reference-v2` remains immutable historical corpus identity; M3-R3 must create
  `terraformers-reference-v3` and re-embed the stable documents with
  `gemini-embedding-001` at 1024 dimensions;
- the AWS-bound batch-ingestion implementation must be replaced for the selected target path;
- M3-4 live quality evidence remains **WAITING_FOR_TARGET_RUNTIME** until M3-R2/R3 complete;
- LangChain/LangGraph remain evidence-gated and are not part of the selected runtime foundation.

## Historical AWS implementation

Cognito, Amplify Cognito integration, Bedrock, S3, the SQS adapter, AOSS/SigV4, CloudWatch, AWS Terraform runtime stacks, EKS-specific integrations, and AWS delivery/deploy/teardown workflows are **HISTORICAL** compatibility/baseline/reference assets, not the active target. Do not assume they must be deleted.

Reusable patterns include provider abstraction, immutable image/SHA identity, least privilege, state separation, approval gates, plan/apply separation, deterministic validation, and deployment/rollback/teardown evidence.

## Deferred / gated decisions

**DEFER — evidence required:** RabbitMQ, Transactional Outbox, LangGraph, a persistent Python AI worker/service, Keycloak, Redis, Kafka, and multi-agent architecture.

**GCP target selected/implemented for M3-R2:** one zonal GKE Standard cluster with an idle `node_count=0` and a bounded `1 × e2-standard-2` live session; internal single-node OpenSearch OSS with a 15 GiB `pd-standard` PVC; Vertex AI `gemini-3.8-flash` and `gemini-embedding-001` at 1024 dimensions; Workload Identity Federation for GKE. Static code exists, but no target resources have been applied.

**Still GATED:** database hosting, object storage implementation, external identity provider, frontend/ingress, broader secrets and observability topology, image registry, GitHub Actions-to-GCP identity and delivery method, and Terraform remote backend. The GKE workload identity decision does not select GitHub Actions federation.

Do not invent TPS or latency targets, AI quality-improvement percentages, or additional node/resource counts. Remaining gated decisions are not selected by this state document.

## Capacity and quota status

The [2026-09-24 live readiness evidence](evaluation/m3-r2-live-readiness-evidence.md) records project `terraformers-platform`, enabled billing and remaining Free Trial credit confirmed by the operator, global CPU limit/usage `12/0`, Seoul `E2_CPUS=8/0`, `INSTANCES=8/0`, `DISKS_TOTAL_GB=2048/0`, GKE server availability, and successful minimal requests to both selected Vertex models. It also records an empty GKE cluster list and no matching target VM. These are dated observations, not a live account connection or permission to create resources. Refresh billing, quota and duplicate-runtime checks before apply. Other deployment capacity/choices remain gated as listed above.

## Remaining M0 work

No remaining M0 work.

## Remaining M1 work

No remaining M1 work.

## Immediate next work

M3-R2 is complete. Do not repeat the foundation Terraform apply or OpenSearch readiness deployment.

M3-R3a static corpus derivation and the M3-R3b one-shot GCP ingestion adapter are both implemented.
The next single live task is the protected
`GCP Target Corpus Ingestion` workflow. It must reuse the existing
`terraformers-backend` Kubernetes ServiceAccount / GKE Workload Identity principal that Terraform
already granted `roles/aiplatform.user` and
`roles/serviceusage.serviceUsageConsumer`. The GitHub apply identity is limited to bounded
Kubernetes object operations and must not receive Vertex permissions.

The live ingestion path must validate the committed v3 checksum, create or validate the
`terraformers-reference-v3` OpenSearch index, generate 128
`gemini-embedding-001` `RETRIEVAL_DOCUMENT` embeddings at 1024 dimensions, preserve stable
document/provenance identity, verify exact document count and a representative k-NN hit, emit only
a sanitized receipt, and delete its ephemeral ingestion pod afterward.

After successful ingestion, proceed to the M3-R3 serving-path smoke through the existing Spring
Boot extraction/retrieval/generation/validation boundaries. Do not tune prompts, retrieval, corpus,
or models before M3-4 baseline evidence exists.

## Do not revisit

Without new evidence, an ADR where needed, and the change gate, do not:

- turn the project into a greenfield rewrite or reduce it to a simple AWS-to-GCP migration;
- split the Spring Boot-centered structure into microservices;
- replace MariaDB with PostgreSQL or another database;
- redesign existing domain/business flows;
- discard `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, or `ObjectReader`/`ObjectWriter`;
- introduce LangGraph/agent architecture before AI evaluation;
- introduce RabbitMQ/outbox before reliability evidence;
- assume Keycloak is the default IdP;
- select a GCP product/topology without quota evidence;
- replace Terraform with OpenTofu; or
- restore the historical AWS stack as the active deployment target.

“Do not revisit” is not a permanent ban; it prevents reopening accepted direction arbitrarily without new evidence, the required ADR, and the change gate.

## Working rules

Before any future task: (1) verify current GitHub `main` SHA, (2) read `AGENTS.md`, (3) read this
document, (4) read `MASTER_PLAN.md`, and (5) read the
[active M3 plan](plans/active/M3-ai-evaluation-baseline.md). Follow its explicit dependency order:
M3-R1 → M3-R2 → M3-R3 → resume M3-4. Do not create an evaluation-only cloud stack and do not
absorb M4 improvement work into M3 baseline collection. Retain the
[completed M2 plan](plans/active/M2-runtime-parity.md) and [completed M1 plan](plans/active/M1-cloud-decoupling.md)
as historical milestone evidence.

For a substantive change, record an observed problem, reproducible evidence, a change that directly addresses it, and same-condition revalidation. Establish a fixed evaluation baseline before AI changes, reproduce a failure before reliability changes, and require diagnosis evidence—not dashboard count—for observability completion.

## Evidence and references

Interpret this checkpoint through the [repository working contract](../AGENTS.md) and these primary sources:

- [Component inventory](architecture/component-inventory.md)
- [Target architecture](architecture/target-architecture.md)
- [GCP deployment architecture](architecture/deployment-gcp.md)
- [ADR-001](architecture/decisions/ADR-001-project-scope.md)
- [ADR-002](architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003](architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004](architecture/decisions/ADR-004-change-gates.md)
- [Modernization master plan](plans/MASTER_PLAN.md)
- [Active M2 plan](plans/active/M2-runtime-parity.md)
- [Completed M1 plan](plans/active/M1-cloud-decoupling.md)
- [M1 closure verification](verification/m1-cloud-decoupling-closure.md)
- [Active M0 plan](plans/active/M0-baseline-and-governance.md)
