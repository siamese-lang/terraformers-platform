# AI Project State

This checkpoint lets a new conversation or agent resume from repository evidence without guessing. It records current state, not an implementation guide or a new architecture decision.

## Repository

- Repository: `siamese-lang/terraformers-platform`
- Default branch: `main`
- State captured from main SHA: `36fbd9eefb61c42a84fc1a2147d6c3677195bcb6`

This SHA is the reference base used to write this state document. Current `main` may differ after merge, so every new task must verify GitHub `main` again rather than treating this SHA as permanently current. It is not this PR's head SHA or a predicted merge SHA.

## Current milestone

- Milestone: **M0 — Baseline & Governance**
- Status: **ACTIVE**
- Phase: governance/documentation closure

M0 remains active until its final consistency and closure work is completed.

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
- [AI project state](AI_PROJECT_STATE.md) (created by this change)

## Verified architectural direction

This project is **not** a greenfield rewrite, a simple AWS-to-GCP migration, or a technology-count expansion exercise. It **is** modernization of the existing Terraformers system through reuse of existing domain/business flows, a provider-neutral logical architecture, a current GCP deployment target, reproduced-failure-based backend reliability work, fixed-evaluation-based AI/RAG work, actual-RCA-based observability, and cloud portability. The Spring Boot application remains the logical center.

Reuse the domain/project/user/file/comment flow, `AnalysisJob` lifecycle baseline, MariaDB + Flyway, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, Terraform draft validation, versioned RAG corpus/contract, Kubernetes-compatible workload contract, and deterministic test/validation assets.

## Current gaps

The following are **NEW**, **MODIFY**, or planned gaps—not completed implementations:

- provider-neutral frontend auth/session boundary;
- provider-neutral backend external identity mapping;
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

Only **M0 final consistency / closure** remains after this change merges. That task must validate all source-of-truth links; check AGENTS, inventory, ADRs, target/deployment architecture, plans, and this state for contradictions; record the exact closure main SHA; mark M0 `COMPLETE`; record M1 entry readiness; and set the immediate next milestone to **M1 — Cloud Decoupling**. None of that closure is complete in this change.

## Immediate next work

The next single task is **M0 final consistency / closure**. Cloud Shell capacity discovery, M1 implementation, and GCP resource creation are not immediate work; they belong after M0 closure in the appropriate milestone/plan.

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

Before any future task: (1) verify current GitHub `main` SHA, (2) read `AGENTS.md`, (3) read this document, (4) read `MASTER_PLAN.md`, (5) read the current active milestone plan, and (6) start from its first incomplete item.

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
- [Active M0 plan](plans/active/M0-baseline-and-governance.md)
