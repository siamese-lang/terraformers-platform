# Case C Portfolio Sufficiency Closure Decision

## Status

**USER-APPROVED DECISION — PORTFOLIO CLOSURE IMPLEMENTATION**

Approved on 2026-10-02.

## Decision driver

The repository success criterion is not a perfectly reliable production service. It is a small number
of technically defensible engineering cases that show evidence-based judgment.

Case C had expanded from a cloud/operations case into an open-ended attempt to eliminate every
remaining correctness, capacity, rollout, and observability defect before closure. That no longer
serves the portfolio objective.

The user explicitly selected portfolio sufficiency over continued system perfection.

## Evidence already obtained

The retained Case C evidence is sufficient to support an operations/reliability decision story:

1. Capacity run `36957682821` stopped at concurrency 1 before any valid saturation result because
   the integrated path failed Terraform executable correctness. This proved that a load result would
   have been misleading until functional correctness was separated from capacity.
2. The architecture audit then separated repository-owned false correctness boundaries from actual
   Terraform correctness. Request-local schema allowlisting, policy-only credential/account/
   placeholder rejection, and sensitive-credential regeneration were removed.
3. PR #190 merged as `ca214ef5dfd23f76477cfef0aa4a9137c4cd1706`; final static acceptance
   passed Terraform Static Verification `37012674794` and Backend Local Verification
   `37012674780`.
4. Immutable backend image publication run `37015159218` built exact source
   `b420291fa1534184d2a260883df718cc96511505` and resolved digest
   `sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419`.
5. Exact-digest rollout run `37015932695` verified the previous deployed digest before mutation,
   rolled out the new immutable image, reached Ready/Available 1/1, preserved embedded source
   identity, and reported backend health UP.
6. Repeated correctness gate run `37016993776` used the same source/image/fixture/runtime identity.
   Attempt 1 passed the complete integrated path; attempt 2 failed with terminal
   `terraform_validate_configuration`. The gate stopped at 1/5 with no automatic whole-gate rerun.
7. The corrected failure parser preserved the terminal Terraform category. The observed remaining
   instability is therefore not the removed request-schema/sensitive policy boundary, not runtime
   identity drift, and not proven external provider variance.

## Selected portfolio case

Case C is reframed from:

> Cloud Runtime Capacity & Safe Delivery

to the narrower evidence-backed case:

> **Cloud Runtime Measurement Guardrails & Immutable Delivery**

The engineering claim is:

> Before interpreting load/capacity results, the integrated request path must satisfy a bounded
> correctness gate under an exact runtime identity. When the first capacity attempt failed before
> saturation, the project stopped tuning infrastructure, audited the correctness boundary, removed
> false repository-owned rejection rules, deployed an immutable revision with source/digest
> provenance, and reran the same gated path. The rerun showed that actual AI-generated Terraform
> validity still varies, so capacity conclusions were intentionally deferred rather than fabricated.

This is a technical-decision case, not a claim that the service is production-perfect.

## Portfolio closure acceptance

Case C is portfolio-sufficient when the repository can explain all of the following from retained
evidence:

1. **Operating scenario** — real authenticated analysis runs on the GKE target using Vertex,
   OpenSearch, persistent dependencies, and immutable backend delivery.
2. **Observed failure** — a supposed capacity baseline failed at concurrency 1 before saturation.
3. **Impact** — continuing load tuning would have attributed functional correctness failures to
   runtime capacity and produced misleading conclusions.
4. **Root mechanism separation** — false repository-owned validation policy was separated from actual
   Terraform executable validity.
5. **Alternatives/decision** — do not widen the corpus blindly, do not remove Terraform CLI
   validation, do not rerun until lucky, and do not tune capacity while the correctness prerequisite
   is unstable.
6. **Implementation** — simplified validation ownership, exact local provider boundary, terminal
   failure classification, immutable image publication, and exact-digest rollout.
7. **Same-scenario after evidence** — the revised path passed attempt 1 and then exposed a genuine
   `terraform validate` failure on attempt 2 instead of the previous policy-only rejection.
8. **Residual risk** — stochastic generated-Terraform validity remains; capacity saturation,
   declarative image convergence, rollout-under-load availability, and full faulty-release rollback
   are not claimed as solved.

These criteria are satisfied by the evidence above.

## Explicitly deferred work

The following are no longer required for portfolio closure:

- another C2 rerun solely to obtain 5/5;
- finer Terraform validation diagnostics solely to chase the next invalid generated draft;
- executor-aware saturation harness repair;
- a valid capacity saturation curve;
- capacity tuning or HPA/replica/resource optimization;
- repository/live image desired-state convergence;
- two-node control-plane cleanup;
- rollout-under-load availability optimization;
- faulty-release rollback experiment;
- integrated-path Kubernetes readiness redesign.

They may be reopened only for a new operational requirement, a real deployment need, or a later
portfolio revision with explicit user approval.

## Case A / Case B reopening rule

This Case C decision does **not** automatically reopen Case A or Case B.

Case A's final closure explicitly states that Terraform structural validation does not prove
deployment correctness and does not claim generated Terraform was plan/applied. Its selected claim
is retrieval grounding/generalization and negative-control behavior. The Case C
`terraform_validate_configuration` observation is downstream executable-validity evidence and does
not contradict those retrieval conclusions.

Case B concerns durable AnalysisJob ownership, fencing, retry, restart, and result-accountability
semantics. The Case C validation failure does not contradict those durability invariants.

A closed case is reopened only when new evidence directly contradicts a material claim in that
case, or when its operating requirement changes. A stricter downstream validator by itself is not a
reason to repeat an already-closed case.

## Non-claims

Portfolio closure does not claim:

- 5/5 generated Terraform reliability;
- statistically reliable AI output;
- production capacity or saturation limits;
- an optimized GKE/backend/OpenSearch configuration;
- zero-downtime rollout;
- completed faulty-release rollback;
- fully converged declarative/live image state;
- production-ready unattended Terraform apply.

These are preserved as explicit residual/deferred risks rather than hidden behind a PASS label.
