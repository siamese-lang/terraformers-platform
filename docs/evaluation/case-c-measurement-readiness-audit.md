# Case C Measurement Readiness Audit

## Status

**NOT READY FOR LIVE BASELINE — REPRESENTATIVE RUNTIME GAP CONFIRMED**

Case C should not start with a load generator, arbitrary concurrency values, HPA, replicas, node
sizing, OpenSearch sizing, executor tuning, or rollout-strategy changes.

The current repository has enough AI/RAG and durable-job implementation to define a meaningful Case C
scenario, but the live GCP target does not yet expose the full service path that Case C is supposed to
measure.

Source audited:

`a6b082ae83377f98c2ea9d82bd6eb7a63948a92a`

## Case C question

The portfolio case is not:

> How many requests can the Case A evaluation pod execute?

The intended question is:

> Under representative concurrent user requests, where does the integrated
> `API → durable AnalysisJob → worker → Vertex/OpenSearch → result persistence → terminal state`
> path first saturate, and can releases/rollback preserve accepted work?

That distinction matters because Case B durability semantics and Case A AI behavior are dependencies of
the Case C result.

## Existing reusable target assets

The following pieces already exist and should be reused rather than rebuilt:

- one GKE Standard target foundation;
- Workload Identity for GKE workloads calling Vertex AI;
- live-proven Vertex `gemini-3.8-flash` generation and `gemini-embedding-001` retrieval embedding;
- live-proven in-cluster OpenSearch OSS target path;
- the production Spring Boot backend and Kubernetes base Deployment;
- Case B's portfolio-closed MariaDB-backed durable AnalysisJob claim/lease/retry/result-accountability
  semantics;
- Actuator/Prometheus exposure;
- low-cardinality analysis metrics including job outcomes, dispatch outcomes, executor rejection,
  queue wait, recovery delay, stage duration and stage failure;
- existing portable MariaDB, authenticated JWKS, and filesystem object-store fixtures.

These are substantial reusable assets. Case C does not need a new service architecture or a new
observability platform before baseline measurement.

## Confirmed representative-runtime gap

### 1. Full backend Deployment is not part of the live GCP target yet

The canonical GCP overlay README explicitly records that the M3 target live path did **not** apply the
full backend overlay because the backend still depends on database, object-storage and JWT runtime
dependencies outside the M3 boundary.

The live Case A workflow therefore proved AI/RAG behavior through bounded Java evaluation pods, not
through the complete authenticated API/durable-job/persistence service path.

That was correct for Case A but is insufficient for Case C.

### 2. Database hosting is not ready for a rollout-durability baseline

The production profile requires:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

MariaDB + Flyway remains the accepted relational contract, but the GCP target has no selected/committed
MariaDB hosting implementation.

The existing portable MariaDB fixture is reusable as a functional pattern, but its database volume is
`emptyDir`. Its own manifest states that it verifies clean-run schema/repository semantics rather
than restart durability.

Using that fixture unchanged would invalidate the Case C safe-delivery question because a workload
restart could destroy the database independently of application behavior.

### 3. Object-byte persistence is not ready for rollout testing

The GCP target has no selected GCP object-storage adapter/product.

The repository has a JDK-only filesystem ObjectReader/ObjectWriter implementation, but the existing
portable fixture points it at:

`/tmp/terraformers-object-store`

That implementation is explicitly a single-runtime fixture. Used unchanged in a backend pod, source
and result bytes would disappear with pod replacement.

A Case C rollout/rollback experiment must not confuse object loss from an intentionally ephemeral
fixture with a release-safety defect.

### 4. Immutable backend image delivery is still unresolved

The target Kubernetes overlay still contains a registry/image placeholder.

ADR-006 deliberately selected GCP Terraform state and GitHub delivery identity but explicitly did
**not** select an image registry.

Case C needs at least two immutable backend revisions to observe healthy rollout and a controlled
failed-readiness revision. Without a real immutable image delivery path, the release experiment is
not representative.

### 5. Public ingress and production IdP are not baseline prerequisites

These gaps should not be inflated into a requirement to finish the entire production platform first.

For the initial Case C benchmark:

- the load driver may run inside the same GKE cluster;
- the backend Service can remain internal;
- the existing portable authenticated JWKS/token pattern can provide deterministic authenticated
  requests.

Therefore public HTTPS ingress, frontend delivery and a final external identity provider do not need
to be selected merely to measure backend capacity and safe delivery.

This keeps the case focused on the measured engineering question.

## Current Measurement Readiness Gate

| Gate | Result | Reason |
|---|---|---|
| Operating scenario fixed | PASS | Case C contract already fixes the integrated request/job/AI/persistence scenario |
| Failure/limitation reproducible | FAIL | No live representative service exists yet on the GCP target to establish saturation/rollout behavior |
| User/system impact explicit | PASS | queueing, rejection, latency, availability, accepted-work safety are defined |
| Before-state measurable | FAIL | evaluation pods do not expose API/job/persistence/rollout behavior |
| Metrics can judge a change | PARTIAL | many backend/job/stage signals exist, but representative runtime/resource evidence is not yet available |
| Minimum missing measurements added | FAIL | no representative load driver/runtime measurement package exists yet |
| Threshold frozen after baseline | NOT APPLICABLE YET | performance thresholds must come from the first valid baseline |
| At least two realistic alternatives | PASS | benchmark-only representative runtime vs completing broader GCP application-runtime product selection |

**Overall: NOT READY FOR LIVE CASE C BASELINE.**

## Why not load-test the existing evaluation pod

Doing so would measure only a subset of the system:

`evaluation runner → Vertex/OpenSearch`

It would omit:

- authenticated API acceptance;
- MariaDB-backed durable job creation;
- queue wait / dispatch ownership;
- executor saturation and rejection;
- source/result persistence;
- terminal job state;
- backend Deployment rollout;
- accepted-work survival;
- rollback behavior.

A large number of requests to that pod could produce latency charts, but those charts would not answer
the Case C portfolio question.

## Smallest next decision

The next decision is **Case C representative benchmark runtime**, not "which tuning should we apply?"

It must compare at least:

### Alternative A — benchmark-only reuse on the existing GKE target

Reuse existing repository components and fixture patterns, adapting only what is required for
representative persistence and delivery:

- existing GKE/Vertex/OpenSearch target;
- real Spring Boot backend and Case B durable job implementation;
- MariaDB 11.4 fixture pattern, but with persistent storage suitable for the benchmark;
- filesystem object-store implementation, but with persistent storage suitable for backend pod
  replacement;
- portable JWT/JWKS pattern for deterministic authenticated requests;
- internal Kubernetes load driver;
- an immutable backend image delivery capability.

This is a **benchmark runtime**, not a claim that in-cluster MariaDB, filesystem object storage or
fixture identity are the selected production hosting architecture.

### Alternative B — complete the broader GCP application-runtime product selections first

Select and integrate permanent database hosting, object storage, external identity, image registry and
possibly ingress before measuring Case C.

This may be more production-like, but it also introduces multiple independent architecture/product
decisions, more cost, and more operational variables before the capacity question has been measured.

## Decision criteria for the next checkpoint

The representative runtime must:

1. execute the real authenticated upload/analysis API path;
2. persist MariaDB state across backend rollout;
3. persist source/result object bytes across backend rollout;
4. use the real Case B dispatcher/lease/retry semantics;
5. use the real Vertex/OpenSearch production adapters;
6. preserve immutable source/release identity;
7. support at least two backend revisions for rollout/rollback evidence;
8. allow an internal load driver without requiring public ingress;
9. expose repository-owned API/job/stage metrics plus Kubernetes resource observations;
10. keep cost and topology narrow enough that the baseline itself does not become a new infrastructure
    project.

## What is deliberately not selected here

This audit does not select:

- Artifact Registry, GHCR or another image registry;
- Cloud SQL or another database hosting product;
- GCS or another object-storage product;
- a production external IdP;
- a public ingress/load balancer;
- Prometheus/Grafana/Cloud Monitoring deployment;
- HPA;
- replicas > 1;
- a new GKE node shape;
- new OpenSearch resources;
- executor thread/queue changes;
- load concurrency values;
- p95 thresholds;
- rollout availability thresholds.

Those choices require either the next representative-runtime decision or the first valid baseline.

## Stopping rule

Do **not** start a live load test or rollout experiment against the current evaluation-only target
path.

Do **not** build a load harness before the representative runtime boundary is approved, because the
harness inputs and observations depend on that boundary.

Do **not** complete every still-gated production capability merely because Case C has started.

## Next single task

Prepare one bounded **Case C representative benchmark-runtime decision** that compares Alternative A
and Alternative B against the criteria above and selects the smallest runtime that can produce valid
capacity and safe-delivery evidence.

No cloud resource creation or live load is authorized by this audit.
