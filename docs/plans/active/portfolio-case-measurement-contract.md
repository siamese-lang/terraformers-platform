# Portfolio Case Measurement & Acceptance Contract

## Status

**PORTFOLIO CASE SET CLOSED — FUTURE HARDENING REQUIRES NEW DECISION**

This document defines the measurement and acceptance contract for the three representative
engineering cases selected during Portfolio Case Reassessment.

It does not preselect an implementation technology. Production/runtime architecture changes remain
subject to the repository Case Decision Gate and explicit user approval.

Representative cases:

1. **Case A — AI/RAG Quality, Performance & Reliability**
2. **Case B — Durable Asynchronous AnalysisJob Processing**
3. **Case C — Cloud Runtime Measurement Guardrails & Immutable Delivery**

The project must optimize for explainable, repository-backed engineering decisions under realistic
operating scenarios rather than milestone count, technology breadth, or production-system
perfection.

## 2026-10-02 portfolio-sufficiency amendment

This amendment overrides the original Case C completion requirements in sections 4, 5, 6, and 7
where they require a full saturation/tuning/rollback program before portfolio closure.

The original requirements remain useful as a future production-hardening backlog, but they are no
longer mandatory for the representative portfolio case.

Case C is portfolio-sufficient when retained evidence shows:

- a real cloud-runtime measurement attempt was invalidated by a correctness failure before
  saturation;
- the project distinguished correctness from capacity instead of tuning infrastructure blindly;
- false repository-owned correctness boundaries were removed without disabling Terraform's own
  executable validation;
- one immutable source-bound image was published and rolled out by exact digest with runtime
  identity verification;
- the same integrated scenario was rerun under the frozen runtime identity;
- the rerun exposed genuine remaining Terraform executable-validity variance and stopped fail-closed;
- unresolved capacity/rollout/reliability work is recorded as residual/deferred rather than claimed
  as solved.

A 5/5 C2 pass, a complete saturation curve, capacity tuning, rollout-under-load optimization, and a
faulty-release rollback experiment are **not required for portfolio closure** after this amendment.

This is not permission to weaken or misreport technical evidence. It narrows the claim to what the
repository has actually demonstrated.

Closed cases are not automatically reopened when a later case adds a stricter downstream validator.
A case is reopened only when new evidence directly contradicts one of its material accepted claims
or its operating requirement changes. In particular, Case C Terraform CLI validity variance does
not by itself invalidate Case A's retrieval-grounding/generalization claims or Case B's durable-job
semantics.

The project must optimize for explainable, repository-backed engineering decisions under realistic
operating scenarios rather than milestone count or technology breadth.

---

## 1. Common measurement rules

### 1.1 Required evidence sequence

Every representative case must follow:

`operating scenario → current baseline → impact → root mechanism → alternatives → decision →
implementation → same-condition after measurement → trade-off / residual risk`

A green workflow, cloud connection, or successful implementation is supporting evidence only.

### 1.2 Correctness versus performance thresholds

Correctness invariants may be fixed before measurement when the required behavior is already clear,
for example:

- accepted work must not disappear silently;
- a terminal logical job must not execute its logical side effects twice;
- cross-resource partial success must be recovered or durably accounted for;
- negative-control AI inputs must not regress;
- a failed rollout must not corrupt persistent state.

Latency, throughput, queue-wait, CPU, memory, OpenSearch latency, or recovery-time thresholds must
**not** be invented without a baseline.

For a metric without an accepted baseline:

1. measure the current system first;
2. record sample count, workload/configuration identity, and observed distribution;
3. freeze the acceptance threshold before the production change;
4. do not move the threshold afterward to fit the implementation result.

### 1.3 Same-condition comparison

Before/after claims must preserve, where applicable:

- fixed input/dataset;
- runtime identity;
- workload shape;
- failure injection;
- configuration;
- measurement method.

If a condition must change, record why and do not present the result as a direct comparison.

### 1.4 Observability is a dependency, not a fourth portfolio case

Reuse current repository-owned signals first:

- `analysisJobId` MDC correlation;
- `terraformers.analysis.jobs`;
- `terraformers.analysis.failures`;
- `terraformers.analysis.duration`;
- `terraformers.analysis.stage.duration`;
- `terraformers.analysis.stage.failures`;
- executor rejection metrics;
- M3 `EvaluationRunner` and stage provenance;
- M5 deterministic failure tests;
- Actuator/Prometheus endpoint.

Add only the smallest signal needed to decide or validate a representative case. OpenTelemetry,
Collector, Grafana, Cloud Trace, or another observability stack requires evidence that existing
signals are insufficient.

---

# 2. Case A — AI/RAG Quality, Performance & Reliability

## 2.1 Operating scenario

A user uploads a cloud architecture image. The service executes:

`image → architecture fact extraction → retrieval → Terraform generation → validation`

A normal input should not fail unpredictably because a provider response is truncated or because a
required retrieval source does not enter the evidence set.

The service must expose enough stage evidence to distinguish extraction, retrieval, generation, and
validation failures.

## 2.2 Existing baseline

Canonical M3 before-state:

- dataset: `terraformers-eval-v1`;
- cases: 6;
- workflow run: `36379633596`;
- fact extraction PASS: 4/6;
- fact extraction FAIL: 2/6;
- first divergence: 2 × `FACT_EXTRACTION / PROVIDER_RUNTIME`.

Retained M4 evidence:

- VPC failure reproduced as `OUTPUT_TRUNCATED / RESPONSE_TRUNCATED`;
- Vertex fact extraction used an 800-token output bound;
- explicit LOW thinking produced one six-case run with:
  - fact extraction 6/6;
  - retrieval 6/6;
  - generation 6/6;
  - architecture validation 4/4;
  - negative controls 2/2 correct;
  - zero first divergence;
- VPC top-8 retrieval missed part of the fixed required evidence;
- AOSS fact extraction recorded one 130489 ms latency outlier.

The single M3/M4 before/after pair is retained evidence but is insufficient by itself for a final
causal/performance claim.

## 2.3 User/system impact

Potential impact includes:

- normal architecture inputs failing non-deterministically;
- unnecessary user retry;
- provider failure being misread as retrieval/generation failure;
- unpredictable response latency;
- syntactically valid Terraform being produced from incomplete grounding evidence;
- overfitting improvements to the six known fixtures.

## 2.4 Target operating contract

Case A must demonstrate that:

1. AI behavior changes are reproducibly measured on fixed inputs.
2. Fact-extraction failures are classified at least at truncation, format, timeout/runtime boundaries
   where evidence permits.
3. Existing successful cases and negative controls do not regress.
4. Required retrieval evidence coverage is measured for applicable architecture cases.
5. Latency and token/cost trade-offs are recorded rather than hidden.
6. A separate holdout evaluation set checks whether the selected improvement generalizes.
7. A single successful rerun is not sufficient to claim reliability improvement.

## 2.5 Required measurements

Quality/reliability:

- fact extraction PASS/FAIL;
- truncation and response-format failure counts/rates;
- first-divergence stage/category;
- expected classification match;
- required component/relationship coverage where deterministically expressible;
- required retrieval evidence coverage and rank;
- required/forbidden Terraform resource coverage;
- Terraform validation;
- negative-control correctness.

Performance:

- fact-extraction latency;
- retrieval latency;
- generation latency;
- end-to-end latency;
- sample count and latency distribution.

When available without invention:

- input tokens;
- output tokens;
- retry occurrence;
- provider usage/cost.

## 2.6 Acceptance criteria

Hard gates:

- canonical M3 dataset remains unchanged;
- negative-control classification regression = 0;
- deterministic Terraform validation regression = 0 for previously successful required cases;
- the selected change must not introduce a new systematic first-divergence class.

Repeated-experiment gates:

Before the next production AI behavior change, repeated baseline measurement must freeze:

- acceptable fact-extraction failure behavior;
- latency comparison method and threshold;
- retrieval-coverage acceptance;
- token/cost comparison boundary.

The after-state must beat or satisfy the frozen criteria under the same experiment shape.

Generalization gate:

A holdout dataset must preserve the material improvement. A change that improves only the canonical
six fixtures and regresses the holdout is not accepted as the final Case A improvement.

## 2.7 Measurement/test gaps to resolve before implementation decision

Potential minimum additions:

- repeated execution of one fixed configuration;
- multi-run aggregate report;
- failure-frequency calculation;
- latency distribution;
- fact-extraction thinking/output-budget identity in experiment records;
- token usage capture where provider data supports it;
- required retrieval evidence rank/coverage reporting;
- separate holdout evaluation dataset.

Prefer extending the current M3 evaluation contract and runner.

## 2.8 Alternatives that may enter the Case Decision Gate

Fact extraction:

- current/default reasoning with current output budget;
- LOW thinking with current output budget;
- larger output budget;
- minimal prompt/schema change only if evidence supports it.

Retrieval:

- current query construction;
- metadata/resource filtering;
- top-K adjustment;
- reranking.

Do not add a reranker or framework merely because it is common in RAG systems.

---

# 3. Case B — Durable Asynchronous AnalysisJob Processing

## 3.1 Operating scenario

After the API successfully accepts an analysis request, the user should not have to submit the same
request again merely because the application process restarts.

Accepted work must have explicit durable ownership and a bounded path to completion or terminal
failure.

## 3.2 Existing baseline

M5 reproduced three reliability gaps:

1. process restart could leave persisted `PENDING` / `RUNNING` jobs stranded;
2. duplicate delivery could re-enter a terminal job and repeat provider/storage side-effect attempts;
3. result-object write could succeed before relational finalization failed, leaving untracked
   persistent residue.

Retained M6 primitives:

- PR #82: atomic `PENDING → RUNNING` claim / terminal-state guard;
- PR #83: startup reconciliation to `FAILED`, retained as containment rather than accepted final
  durability semantics;
- PR #84: rollback-safe object compensation, useful but incomplete when cleanup fails or commit
  outcome is ambiguous.

## 3.3 User/system impact

Potential impact includes:

- accepted work never reaching a terminal state;
- users manually resubmitting work;
- duplicate AI calls and cost;
- duplicate object/DB side effects;
- inconsistent state across database and object storage;
- manual operator cleanup.

## 3.4 Target operating contract

For a successfully accepted analysis request:

> application-process restart by itself must not require the user to create a new job and must not
> be treated as sufficient reason to mark the accepted job terminally failed. The system must
> automatically resume, reclaim, or retry that same accepted job according to bounded, durable
> semantics.

A terminal `FAILED` state is permitted only when the actual processing failure is explicitly
non-retryable or when the selected durable recovery/retry policy has been exhausted. Loss of
previous-process ownership alone is not a final failure condition.

Additional invariants:

1. terminal jobs do not re-enter logical execution;
2. duplicate delivery may occur, but duplicate logical side effects do not;
3. transient failure can use bounded retry when the selected design supports and justifies it;
4. retry exhaustion ends in an explicit terminal state;
5. accepted jobs do not remain indefinitely non-terminal;
6. DB/object partial success is automatically recovered or leaves a durable accountability record;
7. recovery does not depend only on process memory.

## 3.5 Required measurements

Correctness:

- accepted job count;
- terminal job count;
- stranded job count;
- duplicate delivery count;
- duplicate logical execution count;
- duplicate persistent side-effect count;
- retry-attempt count;
- retry-exhausted count;
- unaccounted object count;
- compensation/cleanup success and failure.

Timing/capacity:

- accepted/enqueued → first claim;
- queue wait;
- processing duration;
- restart → reclaim/recovery;
- retry delay;
- accepted → terminal completion;
- queue depth;
- active workers/consumers;
- saturation/rejection where applicable.

## 3.6 Required failure matrix

The final selected design must address, or explicitly justify non-applicability of:

1. request DB commit followed by process crash;
2. crash before dispatch;
3. crash after job claim;
4. duplicate delivery of the same job;
5. transient downstream failure;
6. retry followed by success;
7. retry exhaustion;
8. result object write followed by DB finalization failure;
9. cleanup/compensation failure;
10. two or more workers attempting to claim the same job.

## 3.7 Acceptance criteria

Hard correctness gates:

- stranded accepted jobs = 0;
- terminal-job duplicate re-execution = 0;
- duplicate logical side effect = 0;
- unbounded retry = 0;
- retry exhaustion leaving a non-terminal job = 0;
- silent/unaccounted DB/object partial success = 0.

Immediate cleanup failure may be tolerated only when the residue is durably identifiable and has a
bounded recovery/cleanup path.

Performance thresholds for queue wait, recovery latency, and throughput are frozen only after the
relevant before baseline exists.

## 3.8 Measurement/test gaps to audit before architecture selection

Audit whether current tests/signals can already measure:

- durable claim/queue state;
- attempt/retry count;
- reclaim/recovery duration;
- queue wait;
- cleanup-failure accountability;
- multi-worker claim behavior;
- delivery/redelivery behavior for the selected mechanism.

Add only missing measurements before comparing architectures. Full distributed tracing is not a
prerequisite if job correlation plus bounded stage/queue signals answer the case.

## 3.9 Alternatives that must be compared fairly

At minimum:

1. current in-process executor + reconciliation;
2. MariaDB-backed durable job queue / claim / lease / bounded retry;
3. RabbitMQ-backed durable delivery;
4. Transactional Outbox + RabbitMQ where DB-commit/publication atomicity is required.

Comparison criteria:

- accepted-work durability;
- DB commit / publication failure window;
- crash/restart semantics;
- duplicate/redelivery semantics;
- idempotency;
- retry/dead-letter behavior;
- DB/object side effects;
- operational complexity;
- cloud portability;
- cost and project scale;
- implementation scope.

No candidate is selected merely for portfolio breadth or rejected merely because it was previously
DEFER-listed.

---

# 4. Case C — Cloud Runtime Capacity & Safe Delivery

## 4.1 Operating scenario

The service receives real analysis requests while releases and workload variation occur.

Operators must know:

- where the current runtime saturates;
- whether the first bottleneck is backend execution, Vertex, OpenSearch, DB, CPU, or memory;
- whether healthy releases preserve required availability;
- whether a faulty release can be rolled back without corrupting accepted work.

## 4.2 Existing runtime baseline

Current target foundation:

- GKE Standard;
- one reusable GCP target runtime;
- Vertex AI;
- in-cluster single-node OpenSearch;
- Workload Identity;
- Terraform-managed target foundation.

Current backend shape:

- replicas: 1;
- CPU request/limit: 250m / 1 CPU;
- memory request/limit: 512Mi / 1Gi;
- executor core/max threads: 2 / 4;
- executor queue capacity: 50.

Current Deployment strategy:

- `maxUnavailable: 1`;
- `maxSurge: 0`.

Current OpenSearch shape:

- replicas: 1;
- JVM heap: 1 GiB;
- CPU request/limit: 1 / 2;
- memory request/limit: 2Gi / 4Gi.

Functional readiness has been demonstrated, but representative capacity and rollout-under-load
availability have not yet been measured.

## 4.3 User/system impact

Without measured capacity and rollout behavior:

- latency may rise sharply under modest concurrency;
- executor queue pressure may be invisible until rejection;
- application bottlenecks may be confused with Vertex/OpenSearch latency;
- a one-replica rollout may temporarily reduce service availability;
- accepted work can be affected by rollout/restart semantics;
- adding replicas/HPA/resources without a measured bottleneck can increase cost without solving the
  limiting stage.

## 4.4 Target operating contract

Case C must demonstrate that:

1. representative workload reveals the saturation point and first bottleneck;
2. resource/deployment changes are justified by that bottleneck;
3. healthy deployment meets the frozen availability target under representative load;
4. a faulty revision that cannot become ready does not corrupt persistent state;
5. rollback restores service and accepted-work processing according to Case B semantics;
6. capacity improvement records resource/cost trade-offs;
7. external AI latency is distinguishable from repository-owned runtime saturation.

## 4.5 Required measurements

API/job:

- request acceptance success/failure;
- HTTP 4xx/5xx;
- acceptance latency;
- job queue wait;
- job end-to-end duration;
- job success/failure;
- executor rejection.

Backend:

- CPU and memory;
- active analysis workers;
- executor queue depth;
- stage latency.

AI/RAG:

- fact-extraction latency;
- retrieval latency;
- generation latency;
- provider failure rate;
- OpenSearch request latency.

OpenSearch, where available:

- CPU;
- memory/heap pressure;
- query latency;
- failed requests.

Deployment:

- rollout start/end;
- ready replica count;
- unavailable interval;
- failed request count during rollout;
- accepted job loss/stranding;
- rollback duration;
- post-rollback health.

## 4.6 Load methodology

Do not invent an arbitrary TPS target.

Establish the saturation curve by increasing concurrency from a low level through the first observed
saturation point and one controlled step beyond it.

Before execution, freeze:

- concurrency steps;
- input/workload profile;
- per-step duration;
- target revision;
- runtime configuration;
- collection method.

Add burst testing only if the sustained baseline shows that burst behavior is a distinct operational
risk.

## 4.7 Acceptance criteria

Hard gates:

- correctness regression after capacity tuning = 0;
- persistent database/result corruption caused by deployment/rollback = 0;
- healthy rollback leaving stranded accepted work = 0 under the finalized Case B contract;
- faulty release leaving the service irrecoverably unhealthy = 0.

Performance gates must be frozen after the first load baseline and before tuning:

- representative concurrency;
- acceptable request failure rate;
- acceptable p95 acceptance latency;
- acceptable p95 end-to-end latency;
- acceptable queue wait;
- rollout availability target;
- rollback recovery target.

If additional compute/replicas are used to meet the gates, record the resource/cost delta.

## 4.8 Measurement/test gaps to resolve before tuning

Likely minimum assets:

- reproducible load driver;
- workload/concurrency identity;
- request/job correlation;
- executor active/queue measurement;
- aggregate stage latency;
- rollout-under-load procedure or harness;
- safe failed-readiness release fixture;
- rollback evidence collection;
- GKE/backend/OpenSearch resource-usage collection.

Use existing Prometheus and GKE-native evidence where sufficient. Do not create a new monitoring
platform merely to satisfy this case.

## 4.9 Alternatives after bottleneck measurement

Candidates may include:

- retain one replica but change rollout strategy;
- two or more backend replicas;
- executor sizing;
- backend CPU/memory requests and limits;
- OpenSearch resource sizing;
- GKE node sizing;
- HPA.

The observed first bottleneck, not technology breadth, determines which alternatives proceed to the
Case Decision Gate.

---

# 5. Case sequencing and dependency

Implementation sequence:

1. **Case B**
2. **Case A**
3. **Case C**

Case B comes first because restart/delivery semantics must be reliable before Case C can distinguish
capacity failure from job-loss behavior.

Case A follows so Case C exercises an AI/RAG path that is close to its final measured behavior.

Case C is last because it validates capacity, delivery, rollout, and rollback against the integrated
system after Cases A and B.

This ordering does not authorize automatic progression. Each case remains a separate user-approved
decision/implementation sequence.

---

# 6. Measurement Readiness Gate

Before a production/architecture implementation for a case, all answers must be YES:

1. Is the operating scenario fixed?
2. Is the failure/limitation reproducible?
3. Is user/system impact explicit?
4. Can the before-state be recorded as metrics or invariants?
5. Do metrics exist that can judge the proposed improvement?
6. Have missing measurements/tests been added at minimum scope?
7. Are acceptance thresholds frozen before implementation where a threshold is required?
8. Are at least two realistic alternatives available for comparison?

If any answer is NO, close the measurement gap before production implementation.

---

# 7. Required final portfolio evidence

## Case A

- fixed before baseline;
- repeated experiment report;
- alternatives and decision;
- same-condition after measurement;
- holdout result;
- latency/token/quality trade-off;
- residual failure/risk.

## Case B

- deterministic failure matrix;
- architecture alternatives and decision;
- durability implementation;
- restart/duplicate/retry before-after evidence;
- queue/recovery metrics;
- residual consistency/operational risk.

## Case C

- capacity baseline;
- identified first bottleneck;
- tuning/deployment alternatives and decision;
- same-load after result;
- rollout-under-load result;
- faulty-release rollback result;
- resource/cost delta;
- residual availability/capacity risk.

A representative case is complete only when it can answer:

> What operating problem was observed, what evidence established the mechanism, which alternatives
> were compared, why was one selected, how did the same scenario change afterward, and what risk
> remains?
