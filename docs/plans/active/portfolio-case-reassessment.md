# Portfolio Case Reassessment

## Status

**ACTIVE — FEATURE WORK PAUSED**

This plan temporarily supersedes milestone progression. Its purpose is to restore the modernization
project to its actual success criterion: creating a small number of technically defensible,
repository-backed engineering cases rather than consuming milestone TODOs.

No new product feature, architecture change, live GCP activation, or milestone advancement starts
until this reassessment produces an approved case direction.

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

**REVIEW LIGHTLY**

Check only whether current scope, target architecture, source-of-truth ordering, and decision rules
still match the current project. Do not rebuild M0 verification infrastructure.

### M1 — Cloud decoupling

**REVIEW LIGHTLY**

Check whether provider-neutral application boundaries still exist in current main and whether later
changes reintroduced vendor coupling. Do not rerun historical closure workflows unless current code
inspection identifies a concrete reason.

### M2 — Runtime parity

**PROVISIONALLY RETAIN**

Current evidence is appropriate for a foundation milestone: portable Spring Boot/MariaDB/Flyway,
identity/ownership, upload→analysis→result flow, byte persistence/read-back, and frontend/business
regression were validated with explicit limitations. M2 is not a portfolio case and does not need to
be one.

Reopen only if the M0/M1 review finds a contradiction that invalidates its runtime claims.

### M3 — AI evaluation baseline

**PROVISIONALLY RETAIN**

The fixed six-case dataset, stage provenance, live Vertex/OpenSearch serving path, configuration
identity, and preserved failing baseline are valuable baseline assets. M3 is measurement
infrastructure for an AI/RAG case, not the case completion itself.

Reopen only if dataset/config/provenance integrity is found invalid.

## Case A candidate — AI/RAG quality and reliability

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

Produce an AI case decision brief that identifies the strongest problem to deepen:
fact-extraction truncation/reasoning-budget behavior, retrieval grounding, or another measured
failure. Compare credible alternatives and define the repeated same-condition experiment before
changing production behavior.

## Case B candidate — durable asynchronous AnalysisJob processing

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

## Observability and failure/load work

M7/M8 are **PAUSED AS INDEPENDENT MILESTONES**.

Existing PR #86/#88 evidence is retained, but future observability/load work should primarily deepen
an approved Case A or Case B. Do not create an unrelated dashboard, tracing stack, load test, or
failure demo just to consume the milestone.

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
2. M2/M3 are either retained with clear limitations or explicitly reopened for a concrete reason;
3. M4/M5/M6 claims are reclassified without relying on historical COMPLETE labels;
4. exactly 2–3 candidate portfolio cases are identified;
5. each candidate has a technical decision gap rather than merely a list of fixes;
6. the user approves which case is implemented first and its decision/validation approach.

## Immediate next single task

Perform the lightweight M0/M1 foundation audit and return a concise keep/reopen judgment. Do not
change production code or start either case implementation as part of that audit.
