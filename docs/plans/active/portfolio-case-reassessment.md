# Portfolio Case Reassessment

## Status

**ACTIVE — FEATURE WORK PAUSED**

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

## Case C — cloud runtime capacity and safe delivery

Case C is the independent cloud/operations representative case.

It must measure the integrated GKE/Vertex/OpenSearch/backend system under representative workload,
identify the first saturation bottleneck, and validate healthy rollout plus faulty-release rollback
without inventing arbitrary capacity targets.

Current implementation facts such as one backend replica, executor sizing, OpenSearch single-node
resources, and the current rolling-update strategy are baseline inputs, not defects by declaration.

Capacity tuning, replica changes, HPA, node sizing, OpenSearch sizing, or rollout-strategy changes
require a measured bottleneck and the Case Decision Gate.

Case C begins only after Cases B and A are sufficiently settled for its load results to represent the
integrated system rather than unresolved durability or AI-behavior ambiguity.

The current [Case C Measurement Readiness Audit](../../evaluation/case-c-measurement-readiness-audit.md)
is **COMPLETE / NOT READY FOR LIVE BASELINE**. The GKE/Vertex/OpenSearch target foundation is
available, but the full authenticated backend service is not deployed on that target: persistent
MariaDB/object-byte dependencies and immutable backend image delivery are unresolved. Existing
portable MariaDB/JWKS/filesystem fixtures can reduce scope, but their current `emptyDir`/`/tmp`
forms are not valid safe-rollout persistence evidence. Therefore the evaluation pod must not be
substituted for the Case C system under test.

The representative benchmark-runtime decision is now documented in
[Case C Representative Benchmark Runtime Decision](case-c-representative-benchmark-runtime-decision.md)
and is **READY / AWAITING_USER_DECISION_APPROVAL**.

It selects benchmark-only persistent reuse on the same GKE target: real Spring Boot + Case B durable
jobs + real Vertex/OpenSearch, persistent benchmark MariaDB and filesystem object bytes, deterministic
JWT/JWKS authentication, internal-only traffic, and digest-pinned GHCR images. It rejects both
load-testing the evaluation pod and completing the full GCP application runtime before the baseline.
No implementation, live load or tuning is authorized until the user approves the selected direction.

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

This reassessment is complete only when:

1. M0/M1 lightweight foundation audit is complete;
2. M2/M3 are retained with explicit limitations;
3. M4/M5/M6 claims are reclassified without relying on historical COMPLETE labels;
4. exactly three representative cases are fixed: AI/RAG, durable async backend, and cloud runtime capacity/safe delivery;
5. each case has a technical decision gap rather than merely a list of fixes;
6. the Measurement & Acceptance Contract governs before/after evidence and acceptance;
7. implementation starts only after the selected case passes Measurement Readiness and Case Decision gates.

## Immediate next single task

Case B and Case A are now portfolio-closed. The next representative case is **Case C — cloud runtime
capacity and safe delivery**.

The Case C representative benchmark-runtime decision is
**READY / AWAITING_USER_DECISION_APPROVAL**. The selected direction is the minimum benchmark-only
persistent/authenticated runtime on the existing GKE target, not completion of the broader GCP
application runtime.

If the user approves the direction, the next single task is to create one bounded implementation Work
Package whose only outcome is: one authenticated end-to-end analysis on the real backend survives a
backend pod replacement with MariaDB state and source/result bytes intact. Do not include load testing
in that implementation unit.

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

