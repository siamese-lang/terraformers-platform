# Case C Representative Benchmark Runtime Decision

## Status

**DECISION READY — USER APPROVAL REQUIRED BEFORE IMPLEMENTATION**

Decision source:

- measurement contract: `docs/plans/active/portfolio-case-measurement-contract.md`
- readiness audit: `docs/evaluation/case-c-measurement-readiness-audit.md`
- audited main: `1086165ae527a9e87ba3bf0a9b66c18e234b1275`

This document selects the smallest representative runtime for Case C measurement. It does **not**
authorize cloud resource creation, image publication, Kubernetes apply, load generation, rollout,
rollback, tuning, IAM changes or live Vertex calls.

## 1. Operating scenario

Case C must measure the real integrated service path under representative concurrent requests:

`authenticated API → durable AnalysisJob → dispatcher/worker → Vertex/OpenSearch → result persistence → terminal state`

The same runtime must then support:

- a healthy backend revision rollout while requests/jobs are active;
- one deterministic revision that cannot become ready;
- rollback to the last healthy revision;
- verification that accepted durable work is neither silently lost nor corrupted.

The purpose is to locate the first saturation bottleneck and demonstrate delivery safety. It is not
to finish every production deployment capability before measurement begins.

## 2. Observed problem

The live GCP target currently proves:

- the GKE Standard target foundation;
- Workload Identity to Vertex AI;
- Vertex generation and embedding;
- in-cluster OpenSearch;
- evaluation-only Java execution.

It does **not** yet provide a live full backend service path. Repository evidence confirms:

- the GCP overlay has intentionally not applied the backend Deployment because database, object
  storage and JWT dependencies were outside the M3 live boundary;
- the portable MariaDB fixture uses `emptyDir`;
- the filesystem object-store fixture uses backend-local `/tmp`;
- the target backend image remains a registry placeholder.

Therefore no valid Case C saturation or rollout baseline exists yet.

## 3. Impact

Running a load test before closing that runtime gap would produce misleading evidence.

An evaluation-pod load result cannot answer:

- API acceptance capacity;
- durable job queue wait;
- dispatcher/executor pressure;
- rejection behavior;
- result/source persistence;
- accepted-job survival across backend replacement;
- healthy rollout availability;
- failed-readiness rollback behavior.

Conversely, completing every still-gated GCP product choice first would expand Case C into a broad
migration/deployment project and make it harder to attribute measured bottlenecks to the service
under test.

## 4. Root mechanism

The blocker is not primarily missing load tooling.

The blocker is the absence of one **persistent, authenticated, immutable-release backend runtime
boundary** on the already selected GKE target.

Case A could use ephemeral evaluation pods because its unit of evidence was AI/RAG behavior.
Case C cannot, because its unit of evidence includes durable application state and Deployment
replacement semantics.

## 5. Alternatives

### Alternative 0 — keep the current target and load-test the evaluation pod

Shape:

- existing ephemeral evaluation runner;
- Vertex/OpenSearch only;
- no full backend Deployment.

Advantages:

- no new runtime work;
- fastest to execute.

Disadvantages:

- omits most Case C boundaries;
- cannot measure durable queue/worker behavior;
- cannot validate accepted-work survival;
- cannot validate backend rollout/rollback.

**Decision: REJECT.** It produces numbers but not Case C evidence.

### Alternative A — benchmark-only representative runtime on the existing GKE target

Reuse the current target and production application code, while adapting existing deterministic
fixtures only enough to make the benchmark persistent across backend rollout.

Selected shape:

1. **GKE / AI-RAG**
   - existing target GKE cluster;
   - existing one-node activation lifecycle;
   - existing Vertex and OpenSearch production adapters;
   - existing OpenSearch StatefulSet/PVC.

2. **Backend**
   - actual Spring Boot production application;
   - Case B durable AnalysisJob dispatcher/lease/retry/result-accountability implementation;
   - one backend replica for the first baseline;
   - existing `maxUnavailable: 1`, `maxSurge: 0` rollout strategy unchanged for the before-state.

3. **Benchmark MariaDB**
   - MariaDB 11.4 fixture pattern retained;
   - convert only the benchmark deployment to persistent storage using the already selected
     `terraformers-pd-standard` StorageClass;
   - one MariaDB instance;
   - Flyway and the current MariaDB application contract remain unchanged;
   - this is a benchmark dependency, **not** a claim that in-cluster MariaDB is the final production
     database hosting architecture.

4. **Benchmark object-byte persistence**
   - reuse the existing `FileSystemObjectStore` implementation;
   - mount its root on a dedicated persistent volume instead of pod-local `/tmp`;
   - source and result bytes must survive backend pod replacement;
   - this is a benchmark dependency, **not** the selected production object-storage architecture.

5. **Authentication**
   - reuse the existing deterministic JWT/JWKS fixture pattern;
   - authenticated requests are generated inside the cluster;
   - no public ingress or permanent external IdP is required for Case C baseline measurement.

6. **Immutable backend image delivery**
   - publish the public-repository backend image to GitHub Container Registry (GHCR);
   - publish from GitHub Actions using the repository `GITHUB_TOKEN`;
   - package visibility for the benchmark image must be public so the GKE node does not need a
     long-lived registry secret or new registry IAM;
   - build with `BUILD_SOURCE_REVISION=<full commit SHA>`;
   - record image digest;
   - Kubernetes must deploy `ghcr.io/...@sha256:<digest>`, not a mutable tag.

   The backend repository is public and the current Dockerfile copies application artifacts and
   public tool/provider binaries only; runtime secrets remain external. If the image cannot be made
   public under repository/package policy, **STOP** and open a separate registry decision rather
   than adding a PAT/imagePullSecret workaround.

7. **Network**
   - keep the backend Service `ClusterIP`;
   - run the later load driver inside the same cluster;
   - do not select a public load balancer or ingress merely for benchmark traffic.

8. **Observability**
   - reuse Actuator/Prometheus application metrics;
   - reuse bounded analysis job/stage metrics;
   - use Kubernetes/GKE resource observations and OpenSearch node/query stats where available;
   - do not deploy a new monitoring platform before the first baseline.

9. **Failed revision**
   - use a deterministic Deployment/config revision that cannot become ready without changing or
     corrupting persistent data;
   - rollback must restore the last known healthy image/config revision;
   - do not add application failure code merely to manufacture the rollout scenario.

Advantages:

- directly exercises the real service path;
- preserves Case B semantics;
- reuses the single live target;
- avoids production product selection unrelated to the capacity question;
- permits deterministic rollout/rollback evidence;
- small enough to dismantle/idle after evidence;
- makes the existing one-replica rollout strategy measurable rather than declaring it defective.

Limitations:

- in-cluster benchmark MariaDB is not a managed production DB;
- filesystem-on-PD is not the final object-storage design;
- single-node storage/topology may itself appear in the first bottleneck;
- if later tuning requires a topology that the benchmark storage cannot support, that constraint
  must be reported and the storage boundary revisited rather than hidden.

### Alternative B — complete the broader GCP application runtime first

Select and implement permanent choices for:

- database hosting;
- object storage;
- external identity;
- image registry;
- public ingress / HTTPS;
- associated IAM/networking.

Advantages:

- closer to a complete deployable product;
- fewer benchmark-only components.

Disadvantages:

- introduces several architecture decisions before Case C has measured a bottleneck;
- higher cost and longer implementation path;
- confounds the capacity case with migration/product-selection work;
- external IdP and public ingress are unnecessary for an internal backend benchmark;
- the retained relational contract is MariaDB, while Google Cloud SQL currently offers MySQL,
  PostgreSQL and SQL Server rather than managed MariaDB; choosing Cloud SQL would therefore require
  a separate compatibility/migration decision rather than being a drop-in completion step.

**Decision: REJECT FOR CURRENT CASE C.** These capabilities may be selected later if production
requirements independently justify them.

## 6. Decision criteria

| Criterion | Alternative 0: evaluation pod | Alternative A: benchmark runtime | Alternative B: full GCP runtime |
|---|---|---|---|
| real API/durable-job path | FAIL | PASS | PASS |
| Case B semantics exercised | FAIL | PASS | PASS |
| persistence across backend rollout | FAIL | PASS by benchmark PVCs | PASS if completed |
| Vertex/OpenSearch real adapters | PASS | PASS | PASS |
| immutable backend revisions | FAIL | PASS via digest-pinned GHCR | PASS after registry selection |
| public ingress required | no | no | likely yes / separately designed |
| permanent DB/storage product decision | no | no | yes |
| implementation scope | low but invalid | bounded | high |
| cloud cost/operational expansion | low | bounded | high |
| attribution of first bottleneck | poor | strong enough for Case C | stronger realism but more confounders |
| portfolio explanation value | weak | strong: evidence-driven minimum runtime | broad but risks becoming migration narrative |

## 7. Selected decision

**Select Alternative A — benchmark-only representative runtime on the existing GKE target.**

The selection is based on one principle:

> Add only the runtime capabilities required to make the Case C evidence valid; do not complete
> unrelated production architecture before the baseline tells us what actually limits the system.

This is consistent with the single-live-target rule. It extends the same target rather than creating
a second cloud environment.

## 8. Why GHCR is selected for the benchmark image

The benchmark needs immutable backend images, but a permanent GCP registry is not itself the Case C
problem.

GHCR is selected because:

- the source repository and CI already live on GitHub;
- GitHub Actions can publish a repository-associated container package using `GITHUB_TOKEN`;
- public GHCR container packages can be pulled anonymously;
- deployment can be pinned to the immutable image digest;
- this avoids adding a GCP registry repository plus writer/reader IAM solely to enable the benchmark.

Artifact Registry remains a valid future production candidate. Google documents that a private
Artifact Registry repository pulled by GKE requires the node service account to have
`roles/artifactregistry.reader`; that is reasonable for a production selection but unnecessary
for this benchmark-only boundary.

## 9. Baseline invariants before any tuning

The first valid Case C baseline must retain the current runtime behavior rather than pre-fix it:

- backend replicas: `1`;
- backend CPU request/limit: `250m / 1 CPU`;
- backend memory request/limit: `512Mi / 1Gi`;
- executor core/max: `2 / 4`;
- executor queue capacity: `50`;
- dispatcher poll interval: `2s`;
- dispatcher batch size: `4`;
- OpenSearch replicas: `1`;
- OpenSearch heap: `1 GiB`;
- OpenSearch CPU request/limit: `1 / 2`;
- OpenSearch memory request/limit: `2Gi / 4Gi`;
- Deployment strategy: `maxUnavailable: 1`, `maxSurge: 0`;
- target node shape: unchanged from the existing target activation contract.

Do not add HPA, more replicas, larger nodes, executor changes, OpenSearch changes or rollout changes
before the first baseline.

If the current node cannot schedule the representative benchmark runtime, record that as a
**pre-baseline capacity blocker** and stop. Do not silently resize the node and call the resized
runtime the before-state.

## 10. Data durability boundary

The benchmark must verify before load testing that:

1. an authenticated upload creates source bytes and relational records;
2. the source bytes can be read by the worker;
3. a successful analysis stores result bytes and relational result identity;
4. backend pod replacement preserves source bytes, result bytes and MariaDB state;
5. a restarted backend can continue to observe and process durable job state according to Case B.

This is a readiness proof for the benchmark runtime, not the capacity experiment itself.

## 11. Safe-delivery boundary

The later Case C delivery baseline must measure the existing rollout behavior first.

Healthy revision:

- deploy a different immutable backend image digest;
- preserve runtime configuration and persistent dependencies;
- observe ready replica count, request failures, accepted jobs and completion/recovery.

Faulty revision:

- use a deterministic revision/configuration that cannot become ready;
- do not corrupt the database or object volume;
- record unavailable interval and accepted-work state;
- explicitly roll back to the prior known-good digest/configuration;
- confirm health and durable-job processing after rollback.

Do not change the rollout strategy until this before-state exists.

## 12. Validation plan after implementation approval

Implementation is not accepted merely because pods become Ready.

The representative benchmark runtime must demonstrate, before load-harness work begins:

- backend image built from the exact implementation source SHA;
- GHCR digest resolved and Kubernetes image pinned by digest;
- authenticated internal request succeeds;
- MariaDB state survives backend pod replacement;
- source/result bytes survive backend pod replacement;
- real Vertex/OpenSearch adapters are active;
- one normal analysis reaches terminal `SUCCEEDED`;
- the same job/result identity remains valid after backend replacement;
- Actuator/Prometheus metrics are reachable internally;
- no public ingress is created;
- no second GKE environment is created.

Only after these gates pass may a separate Work Package freeze the load profile, concurrency steps,
step duration, resource collection and stopping rule.

## 13. Explicit non-decisions

This decision does **not** select:

- production MariaDB hosting;
- Cloud SQL migration;
- production object storage;
- GCS adapter implementation;
- production external identity;
- public ingress;
- Artifact Registry for production;
- production monitoring backend;
- replicas > 1;
- HPA;
- executor sizing changes;
- OpenSearch sizing changes;
- node sizing changes;
- capacity thresholds;
- latency SLOs.

## 14. Residual risks

- benchmark-local database/storage topology can influence measured saturation;
- public GHCR availability becomes a deployment dependency for the benchmark;
- a one-node target cannot establish multi-node scheduling/HA behavior;
- external Vertex latency remains stochastic and must be separated from repository-owned saturation;
- the first valid baseline may show that the current node cannot host the representative runtime;
- later multi-node or multi-replica tuning may require replacing the benchmark filesystem boundary.

These are reasons to label the evidence correctly, not reasons to complete every production product
decision in advance.

## 15. Approval boundary

**Implementation status: AWAITING_USER_DECISION_APPROVAL**

User approval of this selected direction is required before any implementation Work Package is
created or any of the following occurs:

- GHCR image publication;
- persistent MariaDB/object-store manifest changes;
- backend GCP-target Deployment enablement;
- Kubernetes apply;
- node activation for this benchmark;
- load generation;
- rollout/rollback experiment.

After approval, the first implementation unit should be only:

> assemble and deterministically verify the representative benchmark runtime until one authenticated
> end-to-end analysis survives backend pod replacement.

Do not start the load baseline in the same implementation unit.
