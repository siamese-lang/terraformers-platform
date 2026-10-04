# M7 — Observability and Failure RCA

## Status

SUPERSEDED / ABSORBED AS SUPPORTING EVIDENCE — CLOSED FOR PORTFOLIO PURPOSE

M7 is no longer an active independent milestone.

The project was later reorganized around three representative engineering cases:

- Case A — AI/RAG retrieval grounding and evaluation
- Case B — backend durable asynchronous processing
- Case C — GCP production-representative runtime and immutable delivery

Observability is retained as supporting evidence across those cases rather than as a fourth case or
as a requirement to deploy a full monitoring platform.

No unfinished item in the historical M7 checklist authorizes automatic implementation.

## Original objective

The useful M7 objective was:

~~~text
request/job identity
  -> stage progression
  -> failure category
  -> resulting state
  -> recovery/compensation
~~~

The goal was to make at least one failure explainable from repository-owned signals. Dashboard
installation by itself was never an acceptance criterion.

That objective produced useful telemetry changes before milestone-driven progression was stopped.

## Baseline evidence retained

PR #86, merged as 46b87be8284e127d8d7fa070d04fb6561a1948a5, captured the deterministic
relational-finalization failure before adding new telemetry.

The baseline proved:

- analysisJobId was present in the failure/compensation log context;
- trace_id/span_id were not backed by a proven tracing implementation;
- terraformers.analysis.jobs with outcome=failed incremented;
- the global failure category was only other;
- total analysis duration was recorded;
- compensation success was visible only as free-text log behavior.

The important gap was therefore not the absence of Grafana or a tracing backend. The gap was that
the application could not machine-distinguish the exact stage and compensation outcome of the same
known failure.

## Minimal telemetry correction retained

PR #88, merged as 294d630d1610f801830091d43ec41100f3172747, introduced provider-neutral
stage telemetry.

The retained signal contract includes:

- terraformers.analysis.stage.duration with bounded stage/outcome dimensions;
- terraformers.analysis.stage.failures with bounded stage/category dimensions;
- provider-neutral result_finalization failure classification;
- stage-correlated logs under analysisJobId MDC.

For the same deterministic failure, the repository can distinguish:

1. analysis_execution — success
2. result_finalize — failure / result_finalization
3. compensation — success
4. terminal job state — FAILED

This is the required before/after observability improvement.

## Current repository-owned signal set

The later Case B/Case C implementation expanded the bounded operational signal set.

Current code includes:

- terraformers.analysis.jobs
- terraformers.analysis.failures
- terraformers.analysis.duration
- terraformers.analysis.stage.duration
- terraformers.analysis.stage.failures
- terraformers.analysis.claims
- terraformers.analysis.dispatch
- terraformers.analysis.lease.renewals
- terraformers.analysis.retries
- terraformers.analysis.cleanup
- terraformers.analysis.cleanup.scan.candidates
- terraformers.analysis.dispatch.scan.candidates
- terraformers.analysis.recovery.delay
- terraformers.analysis.queue.wait
- terraformers.analysis.executor.rejections

Historical Bedrock/AOSS compatibility metrics also remain, but they are not the provider-neutral
current portfolio boundary.

Current AnalysisTelemetryStage values are intentionally limited to repository-owned boundaries:

- analysis_execution
- result_finalize
- compensation
- cleanup_recovery

Job-specific identity belongs in logs/MDC rather than metric labels. The metrics use bounded
dimensions such as stage, outcome, and category.

## Correlation boundary

The current repository includes AnalysisLogCorrelation, which scopes analysisJobId into MDC for
asynchronous job execution and restores the previous MDC value afterwards.

The project does not claim a completed distributed tracing system.

Historical PR #90 proposed a stronger request-to-job correlation proof but was closed without merge.
It must not be cited as merged evidence. Current portfolio claims are therefore limited to the
job-scoped MDC and bounded metric/log evidence that exists on main.

Case C additionally proved Actuator/Prometheus reachability across backend pod replacement. That is
runtime visibility evidence, not a distributed-tracing claim.

## Supporting role in Case B

Observability supports the durable-processing case by exposing bounded signals for:

- claim outcomes
- dispatch outcomes
- lease renewal/loss
- retry scheduling/exhaustion
- cleanup outcomes
- recovery delay
- queue wait
- executor rejection
- final failure category

The correctness claim itself still comes from durable MariaDB state transitions, fencing, tests, and
the integrated B5 matrix. Metrics do not replace those state invariants.

## Supporting role in Case C

Observability supports Case C by allowing the live runtime to classify failures and correlate them
with the deployed source revision.

The final infrastructure case deliberately does not claim:

- a Grafana dashboard platform;
- OpenTelemetry Collector;
- Jaeger or Tempo;
- Cloud Trace;
- fully populated trace_id/span_id;
- production SLOs or alert thresholds.

Those are deferred production-hardening options, not hidden prerequisites for the selected
portfolio claim.

## Signal design rules retained

The following rules remain valid:

- do not use analysisJobId, user id, project id, prompt content, object key, raw exception message,
  or generated Terraform content as metric tags;
- keep job-specific identity in bounded logs/MDC;
- use bounded metric dimensions such as stage, outcome, category, and provider family;
- preserve failure category/stage without logging provider payloads, prompts, image bytes,
  credentials, or secret values;
- preserve build/source revision for release correlation.

## Closure decision

The observability problem identified at M7 activation has enough evidence for the portfolio:

- a concrete before-state exists;
- the missing diagnostic dimension was isolated;
- the correction was minimal and provider-neutral;
- the same failure became stage- and compensation-explainable;
- the resulting signals were reused by later backend/cloud work;
- no evidence demonstrated that a larger tracing/dashboard stack was necessary.

Therefore M7 should not remain ACTIVE or TODO.

Any future monitoring/tracing work requires a new explicit operational requirement or a new
portfolio claim. It must not restart from the old M7 checklist automatically.
