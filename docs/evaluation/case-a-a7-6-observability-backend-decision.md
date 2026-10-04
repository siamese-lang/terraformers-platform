# Case A A7-6 — Observability backend decision

Status: **DECISION COMPLETE — AWAITING REVIEW — NO PRODUCT ADOPTION**

Execution base:

`175de19a32d4aac906eabc0e222090cf0ddf9f69`

## Decision

Keep the current **repository-native observability stack** for the Terraformers portfolio scope.

Do not add Langfuse, OpenTelemetry SDK/agent/Collector, or another observability backend in A7-6.

OpenTelemetry is retained as the preferred future instrumentation/transport standard if a real
cross-service or cross-stage trace-correlation requirement appears. Langfuse remains a conditional
future LLM-observability backend if an operator actually needs one interactive product for LLM
trace/score/release exploration.

This is a decision against **unjustified adoption**, not a claim that the external tools are weak.

## Current repository-native surface

A7-0 through A7-5 already leave the service with four complementary evidence surfaces.

### Runtime metrics

`AnalysisObservability` records:

- AnalysisJob start/success/failure;
- claim/dispatch/lease/retry/cleanup mechanics;
- queue/recovery timing;
- bounded stage duration/outcome and failure categories;
- Bedrock/AOSS outcome and latency metrics;
- retrieval hit counts;
- persisted-quality outcome/reason metrics;
- bounded Terraform diagnostic class and count metrics.

Metric labels are intentionally bounded and do not use AnalysisJob/project/resource identifiers.

### Runtime correlation

`AnalysisLogCorrelation` places `analysisJobId` in MDC for a claimed job.

The application log pattern also includes:

- `trace_id`;
- `span_id`;
- `analysisJobId`;
- `BUILD_SOURCE_REVISION`.

A7-3 through A7-5 added bounded provider and executable-diagnostic events rather than raw payload
logging.

### Durable result/quality state

The existing AnalysisJob persistence/API retains:

- job/result identity;
- terminal state;
- provider;
- bounded failure semantics;
- `evidence-quality-v1` technical/knowledge/quality/project-decision/runtime-boundary state;
- bounded quality reasons.

This survives backend replacement and does not depend on a tracing product retaining historical
meaning.

### Offline evaluation

`EvaluationTrace` and A7-2 calibration retain:

- dataset/run/case identity;
- configuration fingerprint;
- per-stage latency/status;
- retrieval/generation/validation evidence;
- first divergence;
- frozen labeled-quality comparison and false-green semantics.

Therefore the quality oracle remains Terraformers-owned deterministic evidence plus frozen labels,
not an observability vendor score.

## Actual remaining gap

The repository does **not** currently provide one interactive UI showing a nested request trace with
provider/retrieval observations, release comparison, and quality scores in one place.

That is useful operationally, but it is not presently a demonstrated correctness or portfolio
acceptance blocker.

Terraformers is currently centered on one Spring Boot backend. The important failure classes that
previously lacked evidence have been addressed directly:

- provider partial failures: A7-3;
- durable runtime quality: A7-4;
- executable diagnostic subtype: A7-5.

Adding a tracing product now would primarily improve exploration ergonomics rather than close a
measured evidence gap.

## Candidate comparison

| Dimension | Repository-native | OpenTelemetry | Langfuse |
| --- | --- | --- | --- |
| Request/stage diagnosis | Existing stage metrics + MDC logs + durable job state | Strong span model and propagation | Strong LLM-oriented trace/observation UI |
| Quality score attachment | Durable `evidence-quality-v1` + evaluation artifacts | Generic attributes/events; Terraformers still owns semantics | Native scores can attach to traces/observations |
| Job correlation | `analysisJobId` MDC + persisted job | Trace/baggage attributes possible | Trace metadata/tags possible |
| Release/config correlation | source revision logs + evaluation configuration fingerprint | resource/span attributes | release/version/environment concepts |
| Java integration | Already implemented | Mature Java SDK/agent; traces/metrics/logs stable | Current dedicated SDKs are Python/JS; Java uses OTel ingestion |
| Sensitive-data control | Existing bounded contracts | Configurable, but new export boundary must be designed | New external/product data surface must be governed |
| New runtime footprint | None | Agent/SDK plus exporter; Collector/backend if used operationally | Managed service or substantial self-host stack |
| Current portfolio blocker removed | Already covers observed blockers | None demonstrated beyond richer trace UI | None demonstrated beyond richer LLM trace/score UI |

## OpenTelemetry assessment

Official OpenTelemetry Java documentation reviewed on 2026-10-05 states that traces, metrics, and
logs are stable. The Java agent supports zero-code instrumentation for common inbound/outbound,
database, and library boundaries. The Collector provides a vendor-neutral receive/process/export
layer.

This makes OpenTelemetry the best **future instrumentation standard** of the compared external
options.

It is still deferred now because:

1. the current architecture does not have a demonstrated distributed-trace blocker;
2. adding telemetry generation alone does not provide an operational UI/backend;
3. a Collector/export destination would create another runtime and data-governance decision;
4. current A7-0 through A7-5 evidence is already diagnosable through bounded native state.

Future adoption trigger:

- Terraformers becomes multi-service/process;
- A7-7 shows job-MDC/stage metrics cannot isolate a failure;
- vendor-neutral trace export becomes an explicit operational requirement.

Official references:

- https://opentelemetry.io/docs/languages/java/
- https://opentelemetry.io/docs/zero-code/java/agent/
- https://opentelemetry.io/docs/collector/

## Langfuse assessment

Langfuse provides a useful LLM-specific data model of traces, observations, and sessions, along with
score attachment and release/environment analysis.

Current Langfuse documentation reviewed on 2026-10-05 documents dedicated Python and JS/TS SDKs and
OpenTelemetry ingestion for other languages. Therefore a Java Terraformers integration naturally
introduces an OpenTelemetry boundary rather than avoiding one.

For self-hosted Langfuse v4, official deployment documentation describes a multi-component runtime
including Langfuse Web/Worker, PostgreSQL, Redis/Valkey, ClickHouse, and blob storage, with ClickHouse
required for self-hosting.

Terraformers already owns deterministic quality scoring and offline experiment/calibration
artifacts, so Langfuse score features would currently duplicate rather than replace that authority.

Future adoption trigger:

- one interactive LLM trace/score/release UI becomes a demonstrated operator requirement;
- the Java-to-OpenTelemetry ingestion boundary is accepted;
- managed versus self-hosted privacy/cost is explicitly reviewed;
- the product is used as an observability surface, never the quality oracle.

Official references:

- https://langfuse.com/docs/observability/data-model
- https://langfuse.com/docs/observability/sdk/overview
- https://langfuse.com/docs/evaluation/evaluation-methods/scores-via-sdk
- https://langfuse.com/self-hosting/configuration/scaling

## Selected result

**Selected now: repository-native observability.**

**Deferred with explicit trigger: OpenTelemetry.**

**Deferred with explicit trigger: Langfuse.**

No dependency, runtime service, cloud resource, telemetry exporter, or external data path is added
by A7-6.

This decision is consistent with ADR-008's evidence gate: new observability technology is adopted
only when a measured requirement justifies it.

## Next gate

A7-7 does not start automatically.

A7-7 Representative Live Validation still requires separate user approval. Its purpose is to decide
whether repository-only evidence is sufficient or whether a representative live proof is worth
recreating under the existing Case C topology and teardown contract.
