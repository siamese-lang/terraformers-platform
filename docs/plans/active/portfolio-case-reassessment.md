# Portfolio Case Reassessment

## Status

**PORTFOLIO CASE SET CLOSED — PRODUCTION HARDENING DEFERRED**

This plan temporarily supersedes milestone progression. Its purpose is to restore the modernization
project to its actual success criterion: creating a small number of technically defensible,
repository-backed engineering cases rather than consuming milestone TODOs.

No new product feature, architecture change, live GCP activation, or milestone advancement starts
outside the approved representative-case process.

The measurement and acceptance source of truth for all three cases is
[Portfolio Case Measurement & Acceptance Contract](portfolio-case-measurement-contract.md).

## Why this reassessment exists

The repository accumulated valid evidence, but M4 onward increasingly treated
"observed issue → minimal fix → green validation → next milestone" as sufficient progress.
That is not enough for the portfolio objective.

A representative case must show:

`operating scenario → failure/limitation → impact → reproduction → root mechanism →
alternatives → explicit technical decision → implementation → same-scenario before/after →
trade-off/residual risk`

A green test or cloud connection is supporting evidence, not the case itself.

## Foundation status

### M0 — Baseline / governance

**AUDITED — RETAIN**

Check only whether current scope, target architecture, source-of-truth ordering, and decision rules
still match the current project. Do not rebuild M0 verification infrastructure.

### M1 — Cloud decoupling

**AUDITED — RETAIN**

Check whether provider-neutral application boundaries still exist in current main and whether later
changes reintroduced vendor coupling. Do not rerun historical closure workflows unless current code
inspection identifies a concrete reason.

### M2 — Runtime parity

**RETAIN — FOUNDATION ONLY**

Current evidence is appropriate for a foundation milestone: portable Spring Boot/MariaDB/Flyway,
identity/ownership, upload→analysis→result flow, byte persistence/read-back, and frontend/business
regression were validated with explicit limitations. M2 is not a portfolio case and does not need to
be one.

The completed M0/M1 audit found no contradiction that invalidates these runtime-parity claims.
M2 remains foundation evidence and is not itself a representative portfolio case.

### M3 — AI evaluation baseline

**RETAIN — IMMUTABLE CASE A BEFORE-STATE**

The fixed six-case dataset, stage provenance, live Vertex/OpenSearch serving path, configuration
identity, and preserved failing baseline are valuable baseline assets. M3 is measurement
infrastructure for an AI/RAG case, not the case completion itself.

The reassessment confirmed that the canonical dataset/config/provenance identity remains usable.
Later runner diagnostics do not overwrite the canonical M3 source commit or baseline result.

## Case A — AI/RAG quality, performance and reliability

**Current status: PORTFOLIO-CLOSED / PASS.** The authoritative final evidence is
[Case A Final Closure](../../evaluation/case-a-final-closure.md). The sections below retain the
historical reassessment path that led to the final decision.

### Existing evidence worth retaining

- canonical M3 live baseline: fact extraction PASS 4/6, 2 first divergences;
- provider diagnostic improvement from M4-1;
- VPC reproduction as `RESPONSE_TRUNCATED`;
- M4 targeted LOW-thinking change;
- six-case post-change run with fact extraction PASS 6/6;
- newly visible VPC top-8 retrieval coverage miss;
- AOSS one-run fact-extraction latency outlier of 130489 ms.

### Why M4 is not considered portfolio-closed

The current evidence shows correlation between the targeted change and a successful rerun, but does
not yet provide a strong causal/trade-off comparison for the claimed mechanism.

Missing depth includes, as applicable:

- repeated same-fixture MEDIUM/800 versus LOW/800 behavior;
- truncation/schema-validity frequency rather than one before/after pair;
- latency and token-budget trade-off;
- whether a larger output budget is a credible alternative to lower thinking;
- whether extraction semantic quality changes;
- investigation of the newly exposed retrieval-grounding miss;
- decision on whether the latency outlier is material or merely recorded variability.

### Required decision gate before new AI implementation

The selected primary problem is **retrieval grounding / required-evidence coverage**, and its
[decision specification](case-a-retrieval-grounding-decision.md) is READY. The direct VPC evidence
is required decision coverage `0 / 1` and exact required resource-type coverage `2 / 4` at top-K 8,
despite generation producing `4 / 4` required Terraform resources and structural validation
passing. This grounding gap demonstrates that retrieval invocation success and downstream success
do not prove required-evidence coverage.

M4 fact-extraction `RESPONSE_TRUNCATED` remediation remains a completed precursor rather than being
re-selected. The AOSS `130489 ms` fact-extraction result remains a secondary measurement item—not an
established bottleneck—because it is one outlier and the bounded reproduction passed at `9392 ms`.

The observed CloudFormation-shaped resource candidates, empty Terraform `resourceTypeFilters`, and
unfiltered semantic k-NN form a candidate mechanism, not causal proof or a selected fix. Repeated
baseline and controlled alternative evidence must distinguish filtering, cutoff, query, ranking,
and authority/priority effects before any production change.

## Case B — durable asynchronous AnalysisJob processing

### Existing evidence worth retaining

- process restart can strand `PENDING`/`RUNNING` work;
- duplicate delivery could re-enter a terminal job;
- object persistence can precede relational finalization failure;
- atomic `PENDING → RUNNING` claim from PR #82 is a useful primitive;
- restart-to-`FAILED` reconciliation from PR #83 is containment evidence, not accepted as the
  final durability solution;
- PR #84 compensation is a useful cross-resource failure primitive but is not a complete durable
  processing design;
- PR #86/#88 observability evidence may later deepen the same case.

### Operating requirement to decide

For an operation-like service scenario, decide explicitly whether an accepted analysis request must
survive application-process restart without requiring the user to create a new job.

If the answer is yes, "restart → FAILED" is not the target behavior; it is only a containment
strategy.

### Alternatives that must be compared fairly

At minimum:

1. current in-process executor + failure reconciliation;
2. MariaDB-backed durable job queue / lease / bounded retry;
3. RabbitMQ-backed durable delivery;
4. Transactional Outbox + message broker where publication atomicity is required.

Additional alternatives may be considered only when they solve the same observed problem.

The comparison must include:

- durability and delivery semantics;
- crash/restart behavior;
- duplicate delivery and idempotency;
- retry/dead-letter behavior;
- DB/object side effects;
- operational complexity;
- cloud portability;
- cost and project scale;
- implementation scope;
- portfolio explanation value.

RabbitMQ or Outbox may not be excluded merely because they were previously DEFER-listed.
Likewise they may not be selected merely for portfolio technology breadth.

## Case C — GCP production-representative runtime and immutable delivery

**Current status: PORTFOLIO-CLOSED / RESIDUAL RISKS ACCEPTED.**

Authoritative final evidence:
[Case C Final Portfolio Closure](../../evaluation/case-c-portfolio-closure.md).

Case C remains the project's **cloud infrastructure / operations** representative case.

The selected representative runtime combines:

- GKE backend + OpenSearch;
- Vertex AI;
- dedicated MariaDB 11.4 Compute Engine VM + Persistent Disk;
- GCS source/result persistence;
- Secret Manager + native GKE Secret Sync;
- Artifact Registry;
- GitHub OIDC/WIF delivery identities.

The major infrastructure decisions are evidence-backed:

- Cloud SQL/MySQL was deferred after unchanged Flyway compatibility failed on MySQL 8.4;
- MariaDB-in-GKE was rejected for the first representative runtime because shared DB pressure would
  weaken GKE bottleneck attribution;
- a dedicated MariaDB VM/PD retained the proven relational contract while separating resource and
  pod lifecycles;
- GitHub infrastructure mutation and image publication use separate short-lived identity boundaries;
- backend releases are source-bound and digest-pinned rather than dependent on mutable `latest`.

Live evidence includes:

- runtime dependencies `36851221194` and Kubernetes prerequisites `36854218346`;
- integrated authenticated runtime path `36889896239` — **PASS**;
- backend replacement durability `36891629279` — **PASS**;
- immutable image publication `37015159218`;
- exact-digest rollout `37015932695` — Ready/Available 1/1 with matching embedded source.

Observability supports this case rather than becoming a fourth portfolio case. Existing
Actuator/Prometheus signals cover bounded analysis job/failure/stage/queue/retry/recovery behavior,
while `analysisJobId` and source revision remain log-correlation dimensions instead of
high-cardinality metric labels. No completed distributed-tracing/dashboard platform is claimed.

The later capacity attempt is retained as an **operational stopping decision**, not the Case C
centerpiece. Run `36957682821` failed before a valid saturation result; after validation-boundary
corrections, run `37016993776` still showed real generated-Terraform validity variance. The project
therefore stopped capacity attribution rather than tuning GKE against an unstable workload.

The following remain **deferred production-hardening work**, not portfolio prerequisites:

- another C2 run solely to obtain 5/5;
- executor-aware saturation-harness repair and a complete capacity curve;
- HPA/replica/node/OpenSearch capacity tuning;
- declarative/live image convergence;
- multi-replica zero-downtime rollout optimization;
- integrated-path readiness/canary redesign;
- faulty-release rollback;
- MariaDB HA/failover;
- Grafana/OpenTelemetry/tracing/SLO platform work.

This closure is governed by
[Case C Portfolio Sufficiency Closure Decision](case-c-portfolio-sufficiency-decision.md).

## Case B measurement-readiness checkpoint

Case B measurement readiness is now recorded in
[Case B Measurement Readiness Evidence](../../evaluation/case-b-measurement-readiness.md).

PR #94 closed the pre-decision measurement gaps without selecting a durable-processing
architecture. The repository now has deterministic evidence for accepted-but-not-started process
loss, claimed-RUNNING process loss, transient failure with no retry, compensation cleanup failure,
duplicate delivery, and real MariaDB concurrent claim behavior.

Case B is therefore **ready for the Architecture Decision Gate**, but production implementation
remains unauthorized until the user approves the selected direction and before/after acceptance
experiment.

## Case B architecture decision checkpoint

The Case B Architecture Decision Gate is complete and recorded in
[ADR-007: Use MariaDB as the durable source of truth for AnalysisJob execution](../../architecture/decisions/ADR-007-durable-analysis-job-processing.md).

Decision:

- **ACCEPT:** MariaDB-backed durable eligibility + lease/fencing + bounded selective retry;
- **REJECT as final design:** current process-memory executor/restart-to-FAILED containment;
- **REJECT as current final design:** RabbitMQ without an Outbox because DB commit and broker publish
  are not atomic;
- **DEFER:** Transactional Outbox + RabbitMQ until measured requirements justify an independent
  broker/outbox layer.

The existing executor remains a bounded local execution pool. Existing REST result retrieval and
frontend polling remain unchanged.

Implementation is governed by
[Case B Durable Processing — Bounded Implementation Plan](case-b-durable-processing-implementation.md)
and is split into B1 through B5. No stage authorizes automatic progression to the next.

## Case B B1 implementation checkpoint

B1 is complete and recorded in
[Case B B1 Durable State and Fencing Evidence](../../evaluation/case-b-b1-durable-state-fencing.md).

PR #97 merged as `237c97f35184312cac3af56aee438da830f4ff8a` and added only the durable
state/fencing substrate required by ADR-007:

- additive AnalysisJob attempt/lease/fencing state;
- strict lease/retry-time transition invariants;
- stale-generation rejection;
- future retry eligibility representation without retry execution;
- result-object accountability representation without object-flow rewiring;
- real MariaDB single-winner initial claim and expired-lease reclaim validation.

Those after-commit-only delivery and restart-to-FAILED semantics were the B1 checkpoint and changed
in B2.

## Case B B2 implementation checkpoint

B2 is complete and recorded in
[Case B B2 Durable Dispatcher and Restart Recovery Evidence](../../evaluation/case-b-b2-durable-dispatch-recovery.md).

PR #99 head `b7ef9ec77d74d936a28068d64386d2331aa05b46` merged as
`0f18e437af3aaf3c16c3ed075e8963c32cdab240`. Durable dispatcher and restart recovery now recover
accepted work after a lost immediate handoff and reclaim RUNNING work after lease expiry. Provider
execution and terminal finalization are guarded by durable claim/fencing ownership. Provider retry
remains intentionally deferred to B3, and deterministic result identity plus durable object cleanup
safety remain intentionally deferred to B4. Case B is not portfolio-closed until the remaining
stages and integrated evidence are complete.

## Case B B3 implementation checkpoint

B3 is complete and recorded in
[Case B B3 Selective Bounded Retry Evidence](../../evaluation/case-b-b3-selective-bounded-retry.md).

PR #101 final head `f3aa4e488e1858f84688d7b848a4b1af07538656` merged as
`fb1bc3f7b64a0a0274114f1e29ac9f28f2ae652d`. B3 enables selective durable retry only for the
approved provider-timeout signal: another retry is scheduled only while current
`attempt_count < 3`, with a fixed `10s` delay. B2 expired-lease reclaim is not capped by that B3
retry-scheduling threshold, although reclaim increments the shared counter and can reduce later
timeout-retry headroom. Deterministic evidence demonstrates timeout → retry → timeout → retry →
timeout → `FAILED` without a fourth timeout-scheduled execution, as well as rejection of retry
mutation from a stale generation. Explicit semantic provider failures and storage/finalization
failures remain terminal. B4 subsequently closed result identity and durable cleanup accountability;
Case B remains open for B5 integrated closure.

## Case B B4 implementation checkpoint

B4 is complete; evidence is recorded in
[Case B B4 Result Idempotency and Durable Cleanup Accountability](../../evaluation/case-b-b4-result-idempotency-cleanup.md).
PR #103 implemented deterministic identity, durable intent, stale-owner canonical-write fencing,
in-lock compensation accountability, and bounded cleanup recovery. PR #105 / merge
`035b950975ef90c292856bd720ed23a729e12971` directly proves unchanged canonical identity when the
same job's mutable retry/reclaim timing crosses a UTC date boundary. Accountable removal failures
advance `updated_at` so later bounded candidates can progress; this is not a generic fairness claim.

## Case B B5 integrated closure checkpoint

Case B is **portfolio-closed**. The
[Case B Integrated Durable-Processing Closure](../../evaluation/case-b-integrated-closure.md)
records B5 PASS on execution SHA `164fe2f5a83c9d9eba44eca351db604368940598` using fresh manual
GitHub Actions run `36555800770`. The full backend Maven suite and the real-MariaDB repository smoke
both passed. All twelve matrix rows have current direct assertions and all eight hard correctness
gates pass.

The closure remains bounded: it does not prove exactly-once provider invocation, zero provider calls
under every crash timing, production-scale throughput, queue-wait/recovery SLOs, multi-service
distributed durability, universal rejection of RabbitMQ/Transactional Outbox, or generic cleanup
fairness. MariaDB remains the durable source of truth and the executor remains a bounded local
concurrency pool. Case C retains capacity and bottleneck validation.

## Observability and failure/load work

M7/M8 are **PAUSED AS INDEPENDENT MILESTONES**.

Existing PR #86/#88 evidence is retained, but future observability/load work must deepen Case A,
Case B, or Case C according to the Measurement & Acceptance Contract. Do not create an unrelated
dashboard, tracing stack, load test, or failure demo just to consume a milestone.

## Execution protocol

1. Work on one case/decision only.
2. Before production/architecture implementation, present the Case Decision Gate to the user.
3. Do not implement until the user approves the selected direction.
4. Do not automatically start the next subtask after a green CI run or merged PR.
5. Do not automatically merge a production/architecture PR unless merge is within the explicitly
   approved task.
6. If CI is still running, report the current state and stop; do not poll for long periods in one
   response.
7. If another unexpected commit/PR changes main, stop and report scope drift.
8. Prefer modifying/removing weak prior conclusions over preserving an incorrect milestone status.

## Reassessment exit condition

**SATISFIED.**

The reassessment closes with three representative engineering cases:

1. Case A — AI/RAG retrieval grounding and evaluation: **PORTFOLIO-CLOSED / PASS**;
2. Case B — durable asynchronous AnalysisJob processing: **PORTFOLIO-CLOSED / PASS**;
3. Case C — GCP production-representative runtime and immutable delivery:
   **PORTFOLIO-CLOSED / RESIDUAL RISKS ACCEPTED**.

Future work is no longer driven by incomplete milestone or Case C phase lists. It requires a new
operational requirement, a concrete reproduced defect, or an explicitly approved portfolio revision.

## Immediate next single task

There is **no automatic next implementation task**.

The representative portfolio case set is closed. Case A and Case B are retained as closed; Case C
is closed with explicit residual/deferred engineering. Do not resume C2, capacity baseline, C3–C7,
or historical milestone progression merely because those items remain technically incomplete.

A new task must begin from one of:

- portfolio document/material production using the three closed cases;
- a real newly reproduced defect;
- an actual deployment/operations requirement;
- an explicit user-approved hardening goal.

## Case A A1 measurement-readiness checkpoint

At the A1 checkpoint, Case A remained **OPEN** around retrieval grounding / required-evidence coverage. A1 measurement readiness is **COMPLETE** with exact deterministic scoring, complete configuration provenance, per-run and multi-run reports, an evaluation-only fixed-facts probe, and frozen `terraformers-eval-holdout-v1`; see [Case A Retrieval-Grounding Measurement Readiness](../../evaluation/case-a-retrieval-grounding-measurement-readiness.md). This is measurement support, not a retrieval-quality improvement claim. At A1 closure, production retrieval change was **NOT AUTHORIZED / NOT PERFORMED** and A2 was the next candidate. A2 has since completed; see the A2 checkpoint below. Production retrieval behavior remains unchanged, and the current next candidate is **A3 fixed-facts retrieval alternative comparison / decision**, requiring separate user approval.


## Case A A2 repeated current baseline checkpoint

A2 is **COMPLETE** on source commit `96c6442026f6c57a30a9b248a8af115df1e7f2e4`; see
[Case A A2 Repeated Current Baseline Evidence](../../evaluation/case-a-retrieval-grounding-a2-baseline.md).
The VPC grounding gap reproduced `3 / 3` with production retrieval behavior **UNCHANGED**. Every VPC
run had non-empty relevant resource filters, so the earlier filter-loss mechanism is insufficient to
explain A2. Variable retrieval coverage/ranking and variable upstream fact/query wording remain to be
isolated; root cause is **NOT YET SELECTED**, and vector ranking is not proven causal. The historical
AOSS `130489 ms` latency outlier was **NOT reproduced in N=3**, so no latency/model/timeout/topology
work is authorized. At the A2 checkpoint, Case A remained **OPEN**. The next candidate then was **A3 fixed-facts retrieval
alternative comparison / decision**, requiring separate user approval.

## Case A A3 probe-readiness checkpoint

A3 probe readiness is **COMPLETE**; see
[Case A A3 Fixed-Facts Retrieval Probe Readiness](../../evaluation/case-a-a3-retrieval-probe-readiness.md).
Protected run `36592590562` aborted before comparison on Vertex embedding `429 RESOURCE_EXHAUSTED`
under the applied 5 requests/minute `gemini-embedding` quota, and no machine-readable comparison
artifact was produced. Evaluation-only 13-second pacing is implemented, but the live comparison is
**INCOMPLETE**. No retrieval alternative is selected, production retrieval is **UNCHANGED**, and
At the A3 readiness checkpoint, Case A remained **OPEN**. The next task then was one separately
approved protected A3 live probe rerun; A4 was not yet authorized.

## Case A final closure checkpoint

Case A is **PORTFOLIO-CLOSED / PASS** on source
`32d62e21821ed303555ade5e26b03cb669db94ef`.

Final canonical runs `36803174654`, `36803781744`, and `36804600570` passed the frozen N=3
contract: fact extraction `18/18`, retrieval `18/18`, VPC project-decision coverage `3/3`, VPC
required-resource coverage `4/4 × 3`, grounding gaps `0/12`, positive validation `12/12`,
negative controls `6/6`, and no new first divergence.

The frozen holdout then ran exactly once as `36805478708` on the same source/configuration.
`holdout-eks-irsa` retrieved `tfref-v2-eks-irsa` at rank 1 with resource coverage `3/3`;
`holdout-workload-rds-sg` retrieved `tfref-v2-sg-relations` at rank 1 with resource coverage
`2/2`; both positive cases passed generation requirements and Terraform validation, and both
negative controls classified correctly with empty Terraform. Grounding gaps and first divergence
remained zero.

The retained provider-latency side investigation remains closed with FACT_REUSE adoption **HOLD**;
Case A closure does not claim latency optimization. See
[Case A Final Closure](../../evaluation/case-a-final-closure.md) for the full evidence chain,
trade-offs, and residual risks.

<!-- CASE_C_ARCHITECTURE_CLOSURE:START -->
## Case C portfolio-closure checkpoint

Case C is **PORTFOLIO_CLOSED_WITH_RESIDUALS** and remains the project's cloud infrastructure / operations case. The selected case is **GCP Production-Representative Runtime & Immutable Delivery**.

Retained live evidence includes the integrated authenticated runtime path (run 36889896239), persistence and identity across backend replacement (run 36891629279), source-bound immutable image publication (run 37015159218), and exact-digest rollout with Ready/Available 1/1 (run 37015932695). Actuator/Prometheus plus bounded job/stage metrics and job/source-correlated logs are supporting observability evidence, not a separate fourth case.

Capacity run 36957682821 and C2 run 37016993776 are retained as operational stopping evidence: workload correctness varied before trustworthy saturation attribution. Capacity tuning, HA, rollout-under-load, faulty-release rollback, desired-state convergence, and a full tracing/dashboard platform are deferred production-hardening work rather than portfolio prerequisites.
<!-- CASE_C_ARCHITECTURE_CLOSURE:END -->
