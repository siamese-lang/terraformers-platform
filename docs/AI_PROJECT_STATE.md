# AI Project State

This checkpoint lets a new conversation or agent resume from repository evidence without guessing. It records current state, not an implementation guide or a new architecture decision.

## Repository

- Repository: `siamese-lang/terraformers-platform`
- Default branch: `main`
- M0 closure evidence SHA: `3ccf17582ae91ad131d3efe8ce1a38492c401c72`

M0 closure was validated against this main SHA. Current `main` may differ after merge, so every new task must verify GitHub `main` again rather than treating this SHA as permanently current. It is not this PR's head SHA or a predicted merge SHA.

## Current milestone

- Milestone: **M1 — Cloud Decoupling**
- Status: **ACTIVE**
- Phase: provider boundary decoupling
- Active plan: [M1 — Cloud Decoupling](plans/active/M1-cloud-decoupling.md)
- Current implementation task: **M1-2 — Backend JWT / Resource Server boundary**

M0 remains complete; its closure evidence is preserved below. M1 implementation proceeds in the active plan's order, with M1-1 complete and M1-2 next.

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

## Verified architectural direction

This project is **not** a greenfield rewrite, a simple AWS-to-GCP migration, or a technology-count expansion exercise. It **is** modernization of the existing Terraformers system through reuse of existing domain/business flows, a provider-neutral logical architecture, a current GCP deployment target, reproduced-failure-based backend reliability work, fixed-evaluation-based AI/RAG work, actual-RCA-based observability, and cloud portability. The Spring Boot application remains the logical center.

Reuse the domain/project/user/file/comment flow, `AnalysisJob` lifecycle baseline, MariaDB + Flyway, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, Terraform draft validation, versioned RAG corpus/contract, Kubernetes-compatible workload contract, and deterministic test/validation assets.

## Current gaps

The following are **NEW**, **MODIFY**, or planned gaps—not completed implementations:

- provider-neutral frontend auth/session boundary;
- portable OpenSearch transport/authentication;
- provider-neutralization of current object, model, and identity provider adapters;
- fixed AI/RAG evaluation dataset, harness, and report;
- backend reliability/failure-injection harness;
- trace propagation, export, and end-to-end validation; and
- GCP provider-specific IaC/runtime implementation.

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

M1-2 backend JWT/resource-server boundary; M1-3 frontend auth/session boundary; M1-4 object storage decoupling completion; M1-5 OpenSearch transport/auth boundary; M1-6 model/embedding provider configuration; M1-7 runtime configuration neutralization; and M1-8 contract/regression verification and closure.

## Immediate next work

**M1-2 — Backend JWT / Resource Server boundary:** JWT validation과 claim mapping의 Cognito-specific assumptions를 provider boundary 뒤로 이동하고 M1-1의 neutral persistence semantics를 재사용한다. Exact IdP는 선택하지 않으며 frontend authentication은 M1-3에서 다룬다.

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

Before any future task: (1) verify current GitHub `main` SHA, (2) read `AGENTS.md`, (3) read this document, (4) read `MASTER_PLAN.md`, and (5) read the [active M1 plan](plans/active/M1-cloud-decoupling.md). Start with its first incomplete task and do not expand that atomic task into later M1 boundaries.

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
- [Active M1 plan](plans/active/M1-cloud-decoupling.md)
- [Active M0 plan](plans/active/M0-baseline-and-governance.md)
