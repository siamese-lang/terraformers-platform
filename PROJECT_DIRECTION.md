# Terraformers Modernization — final project direction

## 1. Document role

This file records the final portfolio direction of the modernization effort.

Earlier revisions of this document were AWS-runtime-centric and should be treated as historical
planning context. Current source-of-truth precedence remains defined by AGENTS.md and
docs/AI_PROJECT_STATE.md.

The project is now closed around three technically defensible engineering cases:

1. Case A — AI/RAG retrieval grounding and evaluation
2. Case B — backend durable asynchronous processing
3. Case C — GCP production-representative runtime and immutable delivery

Observability supports all three cases and is not a separate fourth portfolio case.

## 2. Project identity

Terraformers began as a 5-person AWS Cloud School team project. The service accepts a cloud
architecture image, analyzes it with AI, generates a Terraform draft, and manages project/result
artifacts through a web application.

The modernization work does not attempt to turn that team project into a completely new individual
product. Its purpose is to make the retained service explainable through evidence-backed backend,
AI/RAG, cloud-runtime, and operational decisions.

The final portfolio identity is therefore:

Terraformers — AI-assisted infrastructure analysis service with durable backend processing and a
production-representative cloud runtime.

The repository is not currently hosting an online service. The representative GCP runtime was
created for bounded live validation and was fully torn down after evidence collection.

## 3. Success criterion

The success criterion is not feature count, PR count, workflow count, or the number of named
technologies.

A representative case must explain:

~~~text
operating scenario
  -> observed failure or limitation
  -> measurable impact/baseline
  -> root mechanism
  -> alternatives and trade-offs
  -> explicit decision
  -> bounded implementation
  -> same-scenario validation
  -> residual risk
~~~

The final case set satisfies that criterion and is intentionally limited to three cases.

## 4. Case A — AI/RAG

### Portfolio question

Can the project show that retrieved evidence actually affects generation quality and generalizes
beyond a few known fixtures, rather than merely showing that an LLM and vector store are present?

### Final scope

- Vertex AI architecture-fact extraction
- Vertex generation
- gemini-embedding-001
- OpenSearch semantic retrieval
- bounded evidence-role-aware selection
- project-decision and provider/resource grounding
- canonical repeated evaluation
- frozen holdout
- negative controls
- first-divergence tracking

### Final decision boundary

LangChain, LangGraph, and LangSmith are not adopted merely because they are common AI application
frameworks.

The current production-representative path is an explicit bounded pipeline rather than an
agent-style control loop. Repository-owned evaluation artifacts already provide the required
before/after and generalization evidence. A future orchestration framework would require a concrete
control-flow problem such as dynamic branching, tool selection, or iterative recovery that the
current path cannot express cleanly.

### Closure

Case A is portfolio-closed. The accepted claim is retrieval grounding, generalization, and
negative-control behavior. It does not claim universal deployment correctness of generated
Terraform.

Canonical closure:
docs/evaluation/case-a-final-closure.md

## 5. Case B — backend durability

### Portfolio question

What happens after the API has accepted an asynchronous job if a process disappears, work is
delivered twice, a provider times out, result persistence partially succeeds, or an old worker
continues after ownership has moved?

### Final architecture

MariaDB is the durable AnalysisJob source of truth.

The runtime combines:

- durable eligibility
- atomic claim/reclaim
- time-bounded lease ownership
- generation fencing
- bounded local executor
- timeout-only bounded retry
- deterministic result identity
- durable result intent
- fenced relational finalization
- compensation and durable cleanup recovery

### Technology decision

RabbitMQ alone was rejected for the atomic-acceptance requirement because DB commit and message
publish would be separate failure boundaries. Transactional Outbox plus RabbitMQ remains a
reasonable future alternative only if measured fan-out, throughput, or service-boundary needs
justify the added operational layer.

### Closure

Case B is portfolio-closed after the integrated 12-scenario matrix and real MariaDB contention
validation.

Canonical closure:
docs/evaluation/case-b-integrated-closure.md

## 6. Case C — cloud infrastructure and operations

### Portfolio question

Can the application run through a real authenticated cloud path with durable relational/object
state and immutable delivery after AWS is no longer available?

### Selected GCP runtime

- GKE Standard for Spring Boot backend and OpenSearch
- Vertex AI for fact extraction, embedding, and generation
- dedicated MariaDB 11.4 Compute Engine VM
- dedicated Persistent Disk for MariaDB data
- GCS for source/result object persistence
- Secret Manager + GKE Secret Sync
- Artifact Registry
- GitHub Actions OIDC / Workload Identity Federation
- separate plan/apply/image-publish trust responsibilities

### Important architecture decisions

Cloud SQL/MySQL was evaluated and deferred because MySQL 8.4 rejected the retained Flyway migration
contract. Forcing the migration solely to use a managed database would have changed the proven
Case B relational boundary.

MariaDB inside the measured GKE cluster was rejected for the representative runtime because DB
resource pressure would be coupled to backend/OpenSearch worker pressure and weaken bottleneck
attribution.

The selected dedicated MariaDB VM preserved compatibility while separating that pressure boundary.

### Closure

The integrated live path, backend replacement durability, immutable image publication, and exact
digest rollout all passed.

Capacity tuning was stopped when workload correctness proved unstable. The project therefore does
not claim a saturation curve, HPA tuning result, or production HA.

Canonical closure:
docs/evaluation/case-c-portfolio-closure.md

## 7. Observability direction

Observability is required when it improves diagnosis of the selected cases. It is not a technology
collection objective.

The repository retains:

- Actuator and Prometheus-format application metrics
- bounded failure categories
- total and stage duration
- queue wait
- claim/dispatch/lease/retry/recovery/cleanup signals
- executor rejection
- analysisJobId MDC correlation
- source revision log correlation

A real deterministic finalization failure demonstrated that job-level failed/other signals were too
coarse. Provider-neutral stage telemetry then made analysis execution, result finalization failure,
and compensation separately identifiable.

No additional Grafana, OpenTelemetry Collector, Jaeger, Tempo, Cloud Trace, or SLO platform is
required for portfolio closure.

## 8. Cloud portability direction

Cloud portability means preserving application/domain boundaries while isolating provider-specific
adapters. It does not mean implementing every cloud service twice.

Historical AWS adapters remain useful reference/compatibility assets. The final representative
runtime used GCP.

The project does not need to recreate AWS infrastructure, maintain simultaneous AWS/GCP production
environments, or prove multi-cloud failover.

## 9. Frontend boundary

Frontend work is not a primary portfolio claim.

The retained frontend is useful only to support the original service scenario and authenticated
end-to-end flows. The modernization should not be extended into a frontend redesign project.

## 10. Explicit non-goals

Do not reopen the project merely to add:

- LangChain/LangGraph/LangSmith without a demonstrated orchestration/evaluation gap
- Kafka/RabbitMQ/Redis without a demonstrated backend requirement
- Grafana/Loki/Tempo/OpenTelemetry merely for technology breadth
- HPA or capacity tuning without a stable workload prerequisite
- MariaDB HA/failover solely for portfolio completeness
- zero-downtime multi-replica delivery claims
- unattended generated-Terraform apply
- a second live cloud environment
- a new product/domain unrelated to the original service

## 11. Final stop rule

Further implementation is justified only when at least one of the following becomes true:

1. new repository evidence directly contradicts a material accepted Case A/B/C claim;
2. a new operational requirement requires reopening a deferred production-hardening item;
3. a portfolio requirement changes and the user explicitly approves a new representative claim.

Absent one of those triggers, implementation remains stopped.

The next work belongs to portfolio presentation, evidence extraction, interview preparation, and
application-specific material selection rather than to new product/runtime development.
