# AI Project State

This checkpoint lets a new conversation or agent resume from repository evidence without guessing. It records current state, not an implementation guide or a new architecture decision.

## Repository

- Repository: `siamese-lang/terraformers-platform`
- Default branch: `main`
- M0 closure evidence SHA: `3ccf17582ae91ad131d3efe8ce1a38492c401c72`

M0 closure was validated against this main SHA. Current `main` may differ after merge, so every new task must verify GitHub `main` again rather than treating this SHA as permanently current. It is not this PR's head SHA or a predicted merge SHA.

## Current milestone

- Milestone: **M2 — Runtime Parity**
- Status: **ACTIVE**
- Phase: portable persistent runtime substrate
- Active plan: [M2 — Runtime Parity](plans/active/M2-runtime-parity.md)
- Current implementation task: **M2-2 — Portable persistent runtime substrate**

M0 and M1 are complete. M1-1 through M1-8 have closure evidence. M2 is active through its
repository-owned plan; M2-1 is complete and M2-2 is the first remaining task.

## Completed

- [Repository working contract](../AGENTS.md)
- [Component inventory](architecture/component-inventory.md)
- [ADR-001: Project scope](architecture/decisions/ADR-001-project-scope.md)
- [ADR-002: Cloud-neutral boundaries](architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Evaluation before complexity](architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Evidence-based change gates](architecture/decisions/ADR-004-change-gates.md)
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

## Verified architectural direction

This project is **not** a greenfield rewrite, a simple AWS-to-GCP migration, or a technology-count expansion exercise. It **is** modernization of the existing Terraformers system through reuse of existing domain/business flows, a provider-neutral logical architecture, a current GCP deployment target, reproduced-failure-based backend reliability work, fixed-evaluation-based AI/RAG work, actual-RCA-based observability, and cloud portability. The Spring Boot application remains the logical center.

Reuse the domain/project/user/file/comment flow, `AnalysisJob` lifecycle baseline, MariaDB + Flyway, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, Terraform draft validation, versioned RAG corpus/contract, Kubernetes-compatible workload contract, and deterministic test/validation assets.

## Current gaps

The following M2 gaps are observed repository facts or execution questions, not completed
implementations or product selections:

- the Kubernetes base has a backend workload but no frontend Deployment/Service; this does not by
  itself prove that a frontend container is required;
- the local-stub path uses H2 with Flyway disabled and disabled/metadata-only storage, stub analysis,
  disabled embedding/retrieval, and logging progress, so it cannot alone prove MariaDB/Flyway,
  object-byte, active provider, or production-like authenticated parity;
- metadata-only object stubs do not persist or read uploaded/result bytes;
- the first confirmed Kind runtime gap is earlier than authentication: the local-stub workload
  cannot be applied because namespace `terraformers-local` is absent; rollout and HTTP paths are
  therefore not yet reached. The no-bearer-JWT upload remains an unconfirmed downstream hypothesis; and
- frontend unit/build regression exists, but portable browser E2E and coverage criteria remain
  `UNKNOWN/TBD`.

## Historical AWS implementation

Cognito, Amplify Cognito integration, Bedrock, S3, the SQS adapter, AOSS/SigV4, CloudWatch, AWS Terraform runtime stacks, EKS-specific integrations, and AWS delivery/deploy/teardown workflows are **HISTORICAL** compatibility/baseline/reference assets, not the active target. Do not assume they must be deleted.

Reusable patterns include provider abstraction, immutable image/SHA identity, least privilege, state separation, approval gates, plan/apply separation, deterministic validation, and deployment/rollback/teardown evidence.

## Deferred / gated decisions

**DEFER — evidence required:** RabbitMQ, Transactional Outbox, LangGraph, a persistent Python AI worker/service, Keycloak, Redis, Kafka, and multi-agent architecture.

**GCP deployment GATED:** GKE use and mode/topology, node count, VM type/size, concrete managed database hosting, object storage implementation, exact identity provider, OpenSearch hosting/auth/network topology, generation and embedding model/providers, exact observability deployment topology, ingress/load-balancer product, exact secret/runtime identity product, and exact CI federation/registry implementation.

Do not invent TPS or latency targets, AI quality-improvement percentages, or node/resource counts. None of these deferred or gated decisions is selected by this state document.

## Capacity and quota status

Repository evidence contains no actual values, so each deployment gate remains `UNKNOWN/TBD`:

- GCP billing / Free Trial status and project/region;
- CPU and persistent-disk quotas;
- IP and load-balancer constraints;
- GKE feasibility/quota;
- OpenSearch and observability resource budgets; and
- model API availability/quota.

Do not infer the user's account state.

## Remaining M0 work

No remaining M0 work.

## Remaining M1 work

No remaining M1 work.

## Immediate next work

**M2-2 — Portable persistent runtime substrate:** M2-1에서 확인된 evidence를 기준으로
canonical provider-neutral configuration과 MariaDB + Flyway를 사용하는 최소 deterministic
runtime fixture를 정하고, backend startup/readiness와 repository persistence를 반복 가능하게
검증한다. Kind namespace/apply gap은 이 task의 persistent-runtime fixture에 직접 필요한 범위에서만
최소 수정하고, authenticated identity·object-byte persistence·active provider 문제를 선행 해결하지 않는다.

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
[active M2 plan](plans/active/M2-runtime-parity.md). Start with its first incomplete task and do not
expand that atomic task into later M2 boundaries. Retain the
[completed M1 plan](plans/active/M1-cloud-decoupling.md) as historical milestone evidence.

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
