# Case C Capacity Baseline Harness Readiness

## Status

**HARNESS IMPLEMENTED / STATIC VALIDATION PASS — LIVE BASELINE NOT YET EXECUTED**

This document records readiness of the bounded Case C capacity experiment, not capacity evidence.
No load result, saturation mechanism, bottleneck, throughput limit, or performance threshold is
claimed here. Running the protected workflow operation requires a separate explicit user approval.

## Operating scenario and fixed workload

The future experiment exercises the production-representative authenticated path:

`POST /api/upload → durable AnalysisJob → Vertex analysis → Vertex embedding → REQUIRED OpenSearch retrieval → GCS result`

It uses only
`evaluation/terraformers-eval-v1/fixtures/case-01-vpc-three-tier.webp`, SHA-256
`a254981735b1060513251cfd9d6dcab82de8a818730f10b128d1c3904635c8c8`. Each project name contains
the GitHub run ID, concurrency step, slot, and sequence number, so no earlier project is reused.

The client model is closed loop. Each slot uploads once, captures the returned project and exact
AnalysisJob ID, polls that job to a terminal state, records it, and only then submits another upload
while the active window remains open. It is not a TPS/request-rate generator.

## Frozen load profile

| Property | Frozen value |
|---|---|
| Concurrency steps | `1 → 2 → 4 → 6 → 8` |
| Metrics pre-sample | 30 seconds per step |
| Active step | 240 seconds |
| Drain timeout | 600 seconds |
| Metrics interval | 5 seconds |
| Extension beyond 8 | Forbidden |

## Fail-closed runtime identity

Before any upload, the harness verifies and records:

- one backend replica; CPU request/limit `250m / 1`; memory request/limit `512Mi / 1Gi`;
- executor core/max `2 / 4`, queue capacity `50`, dispatcher poll interval `2s`, batch size `4`;
- one OpenSearch replica; `1Gi` heap; CPU request/limit `1 / 2`; memory request/limit `2Gi / 4Gi`;
- backend Deployment strategy `maxUnavailable=1`, `maxSurge=0`;
- two `e2-standard-2` GKE nodes and one `e2-medium` MariaDB VM;
- the workflow-supplied immutable backend digest and embedded backend source SHA;
- backend health, backend Pod UID/restart count, and exact fixture SHA-256;
- Vertex analysis/embedding, REQUIRED retrieval, `terraformers-reference-v3`, and GCS storage config;
- working Metrics API CPU/memory collection, required executor/analysis Prometheus series, and
  reachable OpenSearch native node stats.

Any mismatch or unavailable required resource signal fails before load. No runtime value is tuned.

## Authentication and protection

The manual-only `capacity-baseline` operation in the existing GCP runtime dependency workflow
requires all of:

- dispatch from `main`;
- an input SHA exactly equal to checked-out `main`;
- an exact immutable backend image and exact embedded source SHA;
- confirmation token `RUN_REVIEWED_CASE_C_CAPACITY_BASELINE_1`;
- the existing protected `gcp-target-apply` environment.

It reuses the proven ephemeral RS256 JWT/JWKS pattern, refuses to replace non-placeholder JWKS,
verifies the exact public key through the backend-visible Service path, and restores the placeholder
JWKS in an `always()` step. Private key and token material are neither logged nor uploaded.

## Artifact contract

The sanitized run-local artifact directory contains:

- `runtime-identity.json` — workflow/runtime/workload identity and request count;
- `requests.jsonl` — timestamps, deterministic project name, project ID, exact AnalysisJob ID,
  HTTP status, acceptance latency, terminal state, end-to-end time, and failure classification;
- `steps/concurrency-<n>-metrics.jsonl` — 5-second backend/OpenSearch CPU and memory, executor
  active/queue/rejection signals, job counters, Pod identity/restarts, JVM heap, thread-pool
  rejection, and query totals/timing;
- `steps/concurrency-<n>-aggregate.json` — request/outcome counts, observed latency distributions,
  queue-pressure sample count, metric deltas, and saturation result;
- `saturation-classification.json` — first saturation step, one-step-beyond stop boundary, and final
  classification;
- `summary.json` — bounded run summary and real analysis request count.

Exact per-job queue wait is not exposed by the job API; each request therefore records it as `null`
with `queue_wait_source=aggregate_prometheus_only`, while aggregate queue-wait/stage/failure series
remain in sampled Prometheus evidence. OpenSearch request latency is recorded only where its native
cumulative query counters are available; the harness does not fabricate per-request OpenSearch
latency.

## Saturation and stopping classification

Hard signals are executor rejection, capacity-attributable terminal failure, backend restart/OOM,
OpenSearch rejection, and provider `RESOURCE_EXHAUSTED`/quota failure. Provider quota is classified
as `EXTERNAL_PROVIDER_SATURATION`, never backend saturation.

Queue pressure requires executor active workers equal to four and queue depth greater than zero for
at least half of active 5-second samples. After the first signal, exactly one next frozen step runs
when available, then execution stops. If concurrency 8 finishes without a signal, the result is
`INCONCLUSIVE_FOR_SATURATION_EXTENSION`. No latency threshold is invented.

## Readiness boundary and next checkpoint

Static validation can establish only that the harness encodes the reviewed contract. It cannot
establish runtime metric availability, a measured bottleneck, or capacity. The next single action is
a separately approved protected live execution. **The live capacity baseline has not been run by
this implementation and must not be started without explicit user approval.**
