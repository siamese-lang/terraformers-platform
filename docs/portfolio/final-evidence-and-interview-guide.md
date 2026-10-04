# Terraformers — final evidence and interview guide

## 1. Purpose

This document is the final portfolio/interview index for the current terraformers-platform
repository.

It replaces the previous AWS-runtime-centered interview guide as the primary explanation path.

The project should be presented through three engineering cases:

1. Case A — AI/RAG retrieval grounding and evaluation
2. Case B — backend durable asynchronous processing
3. Case C — GCP production-representative runtime and immutable delivery

Observability is supporting evidence across the cases.

Historical AWS/EKS/RDS/AOSS/Argo CD work remains useful background evidence where a question
specifically asks about the earlier runtime, but it is not the primary current portfolio narrative.

## 2. One-line project explanation

Recommended:

Terraformers is an AI-assisted infrastructure analysis service in which I modernized the retained
team-project backend into three evidence-backed cases: RAG grounding evaluation, durable
asynchronous AnalysisJob processing, and a production-representative GCP runtime with immutable
delivery.

Short version:

I used an existing architecture-image-to-Terraform service to build and validate three cases around
AI/RAG quality, backend durability, and cloud runtime operations.

## 3. Contribution boundary

The original Terraformers service was a 5-person team project.

Do not claim:

- the entire original frontend/backend product was individually built;
- all original architecture decisions were individual work;
- every current file started from scratch in the modernization.

The later repository-backed modernization contribution includes:

- backend provider-neutral boundaries;
- durable AnalysisJob ownership/recovery/retry/cleanup;
- RAG grounding measurement and targeted retrieval correction;
- canonical and holdout evaluation;
- GCP target runtime design and deployment;
- immutable image/source provenance;
- bounded operational telemetry;
- failure classification and stopping decisions;
- final GCP teardown and independent residual inventory.

## 4. Case A — AI/RAG retrieval grounding and evaluation

### Interview opening

The first AI problem was that a retrieval stage could return successfully while still omitting
evidence required to ground the generated infrastructure. Downstream Terraform generation could
even validate, so generation success alone was not evidence that RAG was doing the right work.

### Before-state

The retained evidence contained two distinct problems:

1. fact extraction could fail on provider output truncation;
2. retrieval could pass while required project/provider evidence was missing.

The VPC retrieval baseline repeatedly reproduced incomplete grounding even though downstream
generation could still produce the requested resources.

### Root mechanism

A single global top-K and undifferentiated evidence selection could crowd out independent
project-decision/provider-schema evidence required by a larger architecture.

The issue was therefore not simply model quality.

### Alternatives considered

- globally increase K;
- unfiltered larger context;
- hard-code expected fixture evidence;
- global priority reranking;
- resource-aware acquisition plus bounded deterministic coverage.

The selected direction preserved semantic retrieval and introduced bounded coverage behavior without
turning evaluation fixture IDs into production rules.

### Implemented mechanism

- Vertex fact extraction
- gemini-embedding-001
- OpenSearch semantic retrieval
- evidence-role-aware selection
- adaptive final evidence capacity
- project-decision/resource grounding measurement
- canonical repeated evaluation
- frozen holdout
- negative controls
- first-divergence reporting

### Strong evidence

Canonical final N=3:

- 36803174654
- 36803781744
- 36804600570

Aggregate:

- fact extraction 18/18 PASS
- retrieval 18/18 PASS
- VPC decision coverage 3/3
- VPC required-resource coverage 4/4 x 3
- grounding gaps 0/12
- positive Terraform validation 12/12
- negative controls 6/6
- first divergence 0

Frozen holdout:

- run 36805478708
- fact extraction 4/4 PASS
- retrieval 4/4 PASS
- positive grounding complete
- positive validation 2/2
- negative controls 2/2
- first divergence 0

### What to claim

Good:

I measured whether retrieval supplied the evidence required by generation, reproduced a case where
retrieval passed but grounding was incomplete, changed the bounded evidence-selection mechanism,
then revalidated it with repeated canonical runs and a frozen holdout set.

Avoid:

I built a universally reliable Terraform-generating AI.

### LangChain / LangGraph / LangSmith answer

If asked why they were not used:

The problem did not require an agent control loop. The active path was a bounded
fact-extraction -> retrieval -> generation -> validation pipeline. I chose to solve the measured
grounding/evaluation problem directly instead of introducing an orchestration framework without a
control-flow requirement. The repository also already had repeatable run identity, configuration
fingerprints, grounding reports, canonical fixtures, and holdout evaluation, so adopting LangSmith
solely for the product name would not have strengthened the selected claim.

### Residuals

- Case A does not prove every generated Terraform result is deployment-correct.
- Provider latency variance remains external/stochastic.
- The generated result remains an editable/reference draft.

Canonical document:
[Case A Final Closure](../evaluation/case-a-final-closure.md)

## 5. Case B — durable asynchronous AnalysisJob processing

### Interview opening

The backend originally accepted asynchronous work into a process-local execution path. That left
failure boundaries around process loss, duplicate delivery, retry, and partial result persistence
that were not durably owned.

### Before-state

Preserved evidence showed:

- accepted PENDING/RUNNING work could be stranded after process loss;
- duplicate delivery could re-enter execution;
- provider timeout had no durable bounded retry;
- object persistence could succeed before relational finalization failed;
- failed cleanup could leave residue without durable accountability.

### Root mechanism

The executor was being asked to behave like a durable queue even though it only provided local
concurrency.

The durable ownership boundary needed to move into transactional state.

### Decision

ADR-007 selected MariaDB-backed durable eligibility and ownership.

Architecture:

~~~text
MariaDB durable AnalysisJob source of truth
  -> eligible scan
  -> bounded local executor
  -> atomic claim
  -> lease + generation fencing
  -> provider execution
  -> deterministic result intent
  -> fenced finalization
  -> compensation / durable cleanup recovery
~~~

### RabbitMQ trade-off

RabbitMQ by itself was not selected because accepting the DB transaction and publishing a message
would create a commit-to-publish gap.

Transactional Outbox + RabbitMQ remains a valid alternative if later measured throughput, fan-out,
or service-boundary needs justify the extra component and consistency machinery.

This is a stronger interview answer than either never considering a queue or adding a queue merely
for technology breadth.

### Strong evidence

Authoritative run:

- 36555800770
- backend full clean test PASS
- MariaDB 11.4 schema/repository validation PASS

Integrated 12-row scenarios include:

1. accepted DB commit then process loss
2. process loss before execution start
3. process loss after claim and lease-expiry reclaim
4. duplicate delivery
5. provider timeout schedules retry
6. retry then success
7. retry exhaustion
8. object write then DB finalization failure
9. failed compensation and later cleanup recovery
10. concurrent initial/reclaim claim on real MariaDB
11. stale-worker fencing
12. normal success/read-back

### Key mechanisms to explain

Durable eligibility:
The database decides which work is eligible after restart.

Lease:
RUNNING ownership expires rather than remaining permanent.

Generation fencing:
A stale worker from an old claim generation cannot commit current relational success/retry/failure
after ownership has moved.

Selective retry:
Only the approved provider-timeout signal is automatically rescheduled.

Deterministic result identity:
A retry/reclaim does not invent a new output key.

Durable cleanup accountability:
If compensation fails, the exact object intent remains recorded so cleanup can be retried without
rerunning the AI job.

### What to claim

Good:

I separated durable work ownership from the local executor and used transactional lease/fencing
state so restart, duplicate delivery, retry, stale workers, and partial result persistence could be
reasoned about explicitly.

Avoid:

I implemented exactly-once processing.

The case intentionally does not guarantee exactly-once external provider invocation.

### Residuals

- external provider call may already have happened before process loss;
- local executor remains bounded, not a distributed worker fleet;
- no production throughput/SLO claim;
- Transactional Outbox + message broker remains deferred.

Canonical document:
[Case B Integrated Closure](../evaluation/case-b-integrated-closure.md)

## 6. Case C — GCP production-representative runtime and immutable delivery

### Interview opening

The early GCP target was enough for AI evaluation but not enough to claim cloud infrastructure
experience. It did not prove the authenticated backend path, durable relational/object state, or
immutable backend delivery.

I therefore built the smallest representative runtime that exercised the actual application
boundaries.

### Selected runtime

~~~text
GitHub Actions
  -> OIDC / Workload Identity Federation
  -> Artifact Registry
  -> exact image digest
  -> GKE Standard
       |-- Spring Boot backend
       |-- OpenSearch
  -> Vertex AI
  -> MariaDB 11.4 on Compute Engine
       -> dedicated Persistent Disk
  -> GCS
  -> Secret Manager / Secret Sync
~~~

### Database hosting decision

Options considered:

1. Cloud SQL / MySQL
2. MariaDB in GKE
3. MariaDB on a dedicated Compute Engine VM

Cloud SQL/MySQL was deferred because MySQL 8.4 rejected the retained V003 Flyway migration contract.

MariaDB-in-GKE was rejected because database CPU/memory/I/O pressure would share the same measured
GKE worker boundary as backend/OpenSearch and weaken bottleneck attribution.

A dedicated MariaDB VM preserved the proven relational contract while separating database resource
pressure.

Do not describe this as database HA. It was a representative compatibility/isolation choice.

### Live evidence

Runtime dependencies:

- run 36851221194

Kubernetes prerequisites:

- run 36854218346

Integrated application path:

- run 36889896239 — PASS

Path:

~~~text
authenticated upload
  -> durable AnalysisJob
  -> Vertex fact extraction/generation
  -> Vertex embedding
  -> OpenSearch retrieval
  -> GCS source/result persistence
  -> terminal success
  -> Terraform read-back
~~~

Backend replacement:

- run 36891629279 — PASS
- pod replaced
- immutable image/source identity preserved
- AnalysisJob remained readable
- source/result/Terraform bytes remained readable
- Actuator/Prometheus remained reachable

Immutable publication:

- run 37015159218
- source b420291fa1534184d2a260883df718cc96511505
- digest sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419

Exact rollout:

- run 37015932695
- previous digest verified before mutation
- exact target digest deployed
- Ready=1
- Available=1
- embedded source revision verified
- backend health UP

### Capacity experiment and stopping decision

A capacity experiment reached an invalid workload prerequisite before it reached a trustworthy
saturation result.

Later repeated correctness evidence also failed before 5/5.

The correct infrastructure decision was not to tune GKE until a desired graph appeared. It was to
stop capacity attribution when the workload itself was not stable enough to support that inference.

What to say:

I refused to infer a CPU/OpenSearch/executor bottleneck from a workload whose executable-output
correctness was varying. I recorded the failed prerequisite and stopped the tuning claim.

Avoid:

I benchmarked and optimized the production saturation point.

### Residuals

Not claimed:

- production saturation limit
- HPA/replica/node tuning effectiveness
- multi-replica zero-downtime rollout
- faulty-release rollback closure
- MariaDB HA/failover
- full distributed tracing/dashboard/alerting
- unattended generated-Terraform apply

Canonical document:
[Case C Final Portfolio Closure](../evaluation/case-c-portfolio-closure.md)

## 7. Observability supporting evidence

### Initial gap

Before provider-neutral stage telemetry, a deterministic result-finalization failure exposed only:

- failed job
- global category other
- total duration
- free-text compensation behavior

That was not enough to distinguish where the lifecycle failed.

### Minimal correction

PR #88 added bounded stage telemetry so the same scenario became:

~~~text
analysis_execution -> success
result_finalize    -> failure / result_finalization
compensation       -> success
terminal job       -> FAILED
~~~

Current signal set also covers queue wait, claim, dispatch, lease, retry, cleanup, recovery, and
executor rejection.

analysisJobId stays in MDC/log context rather than becoming a high-cardinality metric label.

### Interview answer: why no Grafana/OTel stack?

The measured diagnostic gap was stage/failure/compensation identity, not the absence of a dashboard.
I added the smallest provider-neutral signals needed to explain that failure. I would add a tracing
backend only if cross-service request propagation or production troubleshooting requirements made
job/log/metric correlation insufficient.

### Accuracy boundary

Do not claim a completed distributed tracing system.

Historical PR #90, which proposed a stronger request-to-job correlation proof, was closed without
merge.

Current code does support job-scoped analysisJobId MDC and source-revision correlation, and Case C
proved Actuator/Prometheus reachability in the live runtime.

## 8. Final teardown / cost closure case

### Expected closure

The runtime teardown contract reviewed 29 Terraform-managed deletes.

### Partial destroy incident

Run 37029807484 deleted most managed resources but failed when MariaDB VM deletion and dedicated
data-disk detach overlapped, producing a GCE 409 conflicting operation.

The correct recovery was not to rerun the original 29-delete plan.

A fail-closed subset recovery was introduced. It required:

- exact current managed state count
- remaining addresses inside the original reviewed allowlist
- delete-only actions
- count-bound confirmation

### Recovery evidence

Run 37032634263 showed exactly one remaining managed resource:

google_compute_disk.mariadb_data[0]

The one-delete recovery passed and canonical Terraform state became empty.

Bootstrap cleanup then returned:

gcp_target_bootstrap_cleanup=passed

### Independent inventory finding

Terraform state empty was not treated as final cloud-zero proof.

A separate project inventory found:

- pvc-17c2bb7e-eb1e-4474-9531-41418c77c7df
- 15 GiB
- pd-standard
- READY

This matched the dynamically provisioned OpenSearch PVC disk.

It was deleted manually after confirming the runtime owner was gone.

Final disk inventory:

Listed 0 items.

Other final inventories also showed no Terraformers GKE cluster, VM, static address, GCS bucket, or
Artifact Registry repository.

### Interview lesson

Recommended:

The teardown taught me that Terraform state zero only proves Terraform-managed state is empty.
Kubernetes CSI/controllers can create state-external cloud resources, so final cost closure needs an
independent provider inventory. I found an orphan 15 GiB OpenSearch PD that the workflow summary had
incorrectly reported as absent and removed it before declaring closure.

This is stronger than saying the destroy workflow passed.

## 9. Three-case comparison

| Case | Problem | Core mechanism | Strongest evidence |
|---|---|---|---|
| A | retrieval PASS did not prove grounding | bounded evidence-role/resource coverage + frozen evaluation | N=3 canonical + holdout |
| B | local executor was not durable ownership | MariaDB eligibility + lease/fencing + bounded retry/cleanup | 12-row matrix + real MariaDB contention |
| C | evaluation target was not infrastructure evidence | GCP representative runtime + immutable source/digest delivery | live integrated path + replacement + exact rollout |

## 10. Recommended interview ordering

For an AI/data-oriented role:

1. Case A
2. Case B
3. Case C

For backend development:

1. Case B
2. Case A
3. Case C

For cloud/infrastructure/platform:

1. Case C
2. Case B
3. Case A

Do not try to explain all three in full in one answer. Start with the role-relevant case and use the
others as supporting breadth.

## 11. Short PAR-style statements

### AI/RAG

Problem:
Retrieval technically succeeded but required project/provider evidence could still be missing.

Action:
I measured grounding separately from generation success, changed bounded evidence selection, and
froze canonical/holdout evaluations.

Result:
Final N=3 produced zero grounding gaps across 12 applicable positives and the frozen holdout
preserved the behavior.

### Backend

Problem:
Accepted asynchronous work and result side effects were not durably owned across process loss,
duplicates, retry, and partial persistence.

Action:
I made MariaDB the durable ownership source with lease/generation fencing, timeout-only retry, and
durable result/cleanup intent.

Result:
A 12-scenario closure matrix and real MariaDB contention validation passed.

### Cloud

Problem:
The GCP evaluation target did not prove the real authenticated backend/persistence/delivery path.

Action:
I assembled a GKE/Vertex/OpenSearch/MariaDB/GCS representative runtime and used exact source/digest
delivery identities.

Result:
The integrated live path and backend replacement passed, and final teardown plus independent
inventory reduced known Terraformers billable resources to zero.

## 12. Questions to prepare

Case A:

- Why was generation validation not enough to prove RAG quality?
- Why not simply increase top-K?
- How did the holdout prevent fixture overfitting?
- Why did you not use LangGraph or LangSmith?

Case B:

- Why is an executor not a durable queue?
- What problem does a lease solve?
- What does generation fencing prevent?
- Why not RabbitMQ?
- Can the provider still be invoked twice?

Case C:

- Why GKE instead of only Cloud Run?
- Why a MariaDB VM instead of Cloud SQL?
- Why separate DB pressure from the GKE worker?
- Why immutable digest rather than latest?
- Why stop capacity testing?
- Why was Terraform state zero not enough for teardown?

Observability:

- Why avoid job IDs as metric labels?
- Why were stage metrics enough for the selected RCA?
- When would you add distributed tracing?

## 13. Claims that must remain bounded

Do not claim:

- all generated Terraform is deployable;
- the system automatically applies generated Terraform;
- exactly-once AI-provider execution;
- statistically proven production RAG quality at large scale;
- production HA;
- measured production saturation;
- completed autoscaling optimization;
- full rollback validation;
- full distributed tracing;
- zero-downtime deployment guarantees;
- full original team implementation as individual work.

## 14. Canonical evidence map

Current state:

[AI Project State](../AI_PROJECT_STATE.md)

Case A:

[Case A Final Closure](../evaluation/case-a-final-closure.md)

Case B:

[Case B Integrated Closure](../evaluation/case-b-integrated-closure.md)

Case C:

[Case C Final Portfolio Closure](../evaluation/case-c-portfolio-closure.md)

Observability:

[M7 Observability Closure](../plans/active/M7-observability.md)

GCP teardown:

[GCP Final Teardown](../runbooks/gcp-target-final-teardown.md)

Architecture:

[Target Architecture](../architecture/target-architecture.md)

## 15. Final project stopping boundary

The project does not need another implementation phase for portfolio closure.

Reopen implementation only if:

- new evidence contradicts a material accepted claim;
- a new operational requirement requires one of the deferred hardening items;
- a new portfolio requirement is explicitly approved.

Otherwise, the next work is presentation: portfolio pages, architecture visuals, application
materials, and interview practice.
