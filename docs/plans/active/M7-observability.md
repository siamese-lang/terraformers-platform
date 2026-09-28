# M7 — Observability and Failure RCA

## Status

**ACTIVE — OBSERVABILITY BASELINE / CORRELATION GAP FIRST**

M6 is complete. M7 does not start by installing a dashboard or tracing backend. It first proves what
the current repository can and cannot explain about one real/injected failure, then adds only the
signals needed for reproducible RCA.

## Objective

Make at least one failure explainable from repository-owned signals:

`request/job identity → stage progression → failure category → resulting state → recovery/compensation`

Success means an operator can follow evidence to the root cause and resulting state. Dashboard
installation alone is not evidence.

## Current observability facts

Existing assets:

- Spring Boot Actuator;
- Prometheus Micrometer registry;
- CloudWatch registry retained for AWS compatibility but disabled in the GCP target profile;
- `terraformers.analysis.jobs{outcome=...}`;
- `terraformers.analysis.failures{category=...}`;
- `terraformers.analysis.duration`;
- executor rejection metric;
- Bedrock/AOSS compatibility metrics;
- console log pattern containing `trace_id`, `span_id`, `analysisJobId`, and source revision;
- `AnalysisLogCorrelation` that places `analysisJobId` in MDC while the async job runner executes.

Observed gaps:

1. the backend has no Micrometer tracing bridge/exporter dependency, so the configured
   `trace_id`/`span_id` fields are not currently proven to carry repository-owned trace context;
2. request context is not proven to propagate across the transaction/async-executor boundary;
3. the current active Vertex/OpenSearch path is not instrumented by the AWS-specific
   `recordBedrock`/`recordAoss` metrics;
4. Vertex facts/retrieval/generation/validation/finalization do not share one standardized
   provider-neutral stage telemetry contract;
5. no repository-owned evidence currently demonstrates RCA of one failure by joining logs and
   metrics for the same analysis job.

These are M7 baseline statements, not a requirement to deploy OpenTelemetry, Grafana, or another
backend.

## Non-goals

Do not add these merely because M7 has started:

- Grafana;
- Prometheus server deployment;
- OpenTelemetry Collector;
- Jaeger;
- Tempo;
- Cloud Trace;
- Elasticsearch/Loki logging stack;
- arbitrary SLOs or alert thresholds;
- a new GitHub Actions workflow;
- a second GCP observability environment.

A tracing bridge/exporter is eligible only if job/log/metric correlation cannot meet the RCA exit
condition without it.

## Signal design rules

- Never use `analysisJobId`, user id, project id, prompt content, object key, or error message as a
  metric tag. Those are high-cardinality or sensitive dimensions.
- Job-specific correlation belongs in structured/log MDC context.
- Metrics use bounded dimensions such as `stage`, `outcome`, `category`, and provider family.
- Provider-neutral application metrics must not be named `bedrock` or `aoss`.
- Existing AWS compatibility metrics may remain for historical adapters.
- Failure logs must preserve category and stage without exposing provider payloads, prompts, image
  bytes, credentials, or internal exception messages.
- Reuse `BUILD_SOURCE_REVISION` for release/source correlation.

## M7-1 — Current signal baseline

**Status: TODO**

Use one existing deterministic failure path to capture what current logs and metrics can explain
without adding instrumentation.

Preferred scenario:

- M6-3 relational finalization failure after successful object write, because it traverses analysis,
  validation, storage, relational finalization, compensation, and terminal state.

Record:

- job state transitions;
- existing log lines and MDC fields;
- current metrics/counters before and after;
- which exact stage can/cannot be identified;
- whether `trace_id`/`span_id` are populated;
- whether compensation success is machine-identifiable.

The purpose is to locate the observability gap, not re-test M6 reliability.

## M7-2 — Provider-neutral stage telemetry

**Status: BLOCKED ON M7-1**

If M7-1 confirms the expected gap, add one low-cardinality stage contract covering the active
analysis lifecycle. Candidate stage names are limited to repository-owned boundaries such as:

- `source_read`;
- `fact_extraction`;
- `retrieval`;
- `generation`;
- `validation`;
- `result_store`;
- `result_finalize`;
- `compensation`.

The exact list must follow actual code boundaries rather than create synthetic stages.

Required signal shape:

- stage duration;
- stage outcome;
- bounded failure category;
- logs inherit `analysisJobId` and source revision;
- active Vertex/OpenSearch execution uses the same contract;
- no per-job metric labels.

Prefer extending `AnalysisObservability` over creating a parallel telemetry subsystem.

## M7-3 — Async correlation boundary

**Status: BLOCKED ON M7-1**

Determine whether `analysisJobId` plus source revision is sufficient to connect the accepted
request to async execution and resulting state.

If request-to-job correlation is incomplete, implement the smallest propagation mechanism:

- preserve a bounded correlation id/job id across the executor handoff; or
- add Micrometer tracing context propagation only if it materially improves the RCA evidence.

Do not add a trace exporter/backend merely to make `trace_id` non-empty.

## M7-4 — Failure RCA evidence

**Status: TODO**

Re-run one failure scenario after the minimum instrumentation change and produce a repository-owned
RCA record containing:

1. trigger/reproduction;
2. job/correlation identity;
3. ordered stage signals;
4. first failed stage/category;
5. persisted job/object result;
6. recovery/compensation signal;
7. exact source revision;
8. operator conclusion.

The evidence must be derivable from emitted signals, not from reading source code after the fact.

## M7-5 — Observability closure

**Status: TODO**

M7 is complete when:

1. one real/injected failure has a reproducible RCA from repository-owned metrics/logs/trace signals;
2. the same job can be followed through the async lifecycle using bounded correlation context;
3. the active Vertex/OpenSearch path has provider-neutral stage visibility where required by the
   RCA;
4. failure category and resulting state/recovery are visible without sensitive payloads;
5. no high-cardinality metric tags were introduced;
6. any tracing dependency/backend addition is justified by evidence rather than convention.

## Runtime / cost rule

M7 should begin entirely with local deterministic evidence. Do not reactivate the GCP target merely
to prove log/metric plumbing. A bounded live target run is eligible only if M7 local evidence cannot
validate the active Vertex/OpenSearch signal path.

## Immediate next single task

Execute M7-1 using the existing M6-3 partial-success failure harness. Capture current emitted
metrics/log correlation and identify the smallest missing signal before changing instrumentation.
