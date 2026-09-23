# M2 — Runtime Parity

## Status

**ACTIVE**

M0 and M1 are complete. M2 proves the existing cloud-neutral application contracts in a portable,
deterministic runtime; it is not a new-feature or GCP-deployment milestone. The first incomplete
task is **M2-1 — Portable runtime baseline and parity gap inventory**.

## Objective

Demonstrate that the current application can repeatably execute its core experience and persistence
semantics through this chain:

`architecture/image input → upload/source object → project/file → analysis job → retrieval/model → Terraform draft validation → result persistence`

The milestone also verifies user/project/comment parity. It reuses the existing domain and provider
contracts rather than redesigning them, and records what works, fails, or is not covered before any
implementation change.

## Current evidence and reusable runtime assets

Repository inspection at activation base `78f90a09ac9efa9c23a28dc3f5ecfbf770753e36`
confirms these reusable assets:

- Spring Boot backend and MariaDB + Flyway contract;
- neutral JWT/resource-server boundary and neutral external identity mapping;
- `ObjectReader` / `ObjectWriter`, `AnalysisProvider`, `EmbeddingProvider`, and
  `ReferenceRetriever` boundaries;
- `TerraformDraftValidator`, the `AnalysisJob` lifecycle, and project/file/comment domain flows;
- Kubernetes base workload, local-stub Kustomize overlay, and backend Docker image;
- deterministic backend tests and frontend tests/build;
- M1 closure workflow, MariaDB repository/schema smoke, runtime contract checks, and
  `scripts/checks/kind-local-stub-smoke.sh`.

The [M1 closure report](../../verification/m1-cloud-decoupling-closure.md) proves contract separation
and regression coverage. It explicitly does not prove portable end-to-end runtime parity.

## Known gaps at activation

These are source-inspection findings to reproduce and classify in M2-1, not assumptions that a
particular replacement technology is required:

1. **Kubernetes base scope.** `infra/kubernetes/base` contains a backend Deployment, Service,
   ConfigMap, and ServiceAccount (plus supporting Kustomize documentation/configuration), but no
   frontend Deployment or Service. This does not by itself establish a need for a frontend container.
2. **Local-stub limits.** The local-stub overlay selects `SPRING_PROFILES_ACTIVE=local`. The local
   profile uses H2 with Flyway disabled, `reader-provider=disabled`,
   `writer-provider=metadata-only`, `analysis-provider=stub`, `embedding-provider=disabled`,
   `retrieval-mode=disabled`, and `progress-publisher=logging`. Therefore that path alone does not
   prove MariaDB/Flyway runtime parity, actual object-byte persistence, active retrieval/model
   integration, or a production-like authenticated JWT path.
3. **Object persistence limits.** `StubObjectWriter` reports `provider=metadata-only` and
   `persisted=false`; it stores no source or result bytes. `StubObjectReader` is not a runtime store
   for uploaded bytes. M2 must first classify which persistence requirements existing contracts can
   prove. It does not preselect MinIO, a production filesystem adapter, GCS, or restored S3.
4. **Kind smoke/auth mismatch to reproduce.** `kind-local-stub-smoke.sh` calls `POST /api/upload`
   without a bearer JWT, while `AuthenticatedUserService.getOrCreate(jwt)` rejects a null JWT with
   `AuthenticationCredentialsNotFoundException`. M2-1 must execute the current smoke and record its
   actual startup, health, upload, project-tree, and Terraform-read behavior; source inspection is
   not evidence that it passes.
5. **Browser evidence.** Frontend unit regression and production build exist, but the component
   inventory records browser E2E and coverage criteria as `UNKNOWN/TBD`. Portable runtime browser
   E2E has not been established.

## Planning and evidence rules

- Every task begins `TODO`; work starts at the first incomplete task and does not absorb a later task.
- Current behavior is reproduced before it is changed. Results are classified `PASS`, `FAIL`, or
  `NOT COVERED` with commands, runtime configuration identity, base/head SHA, and first failure.
- A failure discovered during baseline collection is not fixed broadly in the same baseline task.
- Existing abstractions and deterministic checks are preferred. Any new runtime fixture must pass
  [ADR-004](../../architecture/decisions/ADR-004-change-gates.md): observed gap → reproducible
  evidence → smallest directly related change → same-condition validation.
- M2 makes no AI quality, performance, reliability-recovery, or production-readiness claim.

## Work sequence

### M2-1 — Portable runtime baseline and parity gap inventory

**Status: TODO**

**Problem / gap.** The repository has deterministic checks and a local-stub runtime, but there is no
single current-main record distinguishing working runtime paths, failures, and uncovered parity
requirements.

**Current evidence.** Backend and frontend regression assets, MariaDB/Flyway repository validation,
runtime-contract verification, and the Kind smoke exist. The source-inspected auth mismatch and the
local profile limits above remain execution hypotheses until reproduced.

**Change boundary.** Execute the current portable/local runtime without implementation changes.
Collect backend full regression, MariaDB/Flyway repository validation, frontend test/build,
runtime-contract verification, and `kind-local-stub-smoke.sh` evidence. Record Kind startup, health,
upload, project tree, and Terraform read separately. Do not fold broad fixes into this task.

**Validation.** Run each applicable repository-owned command from a clean checkout at the recorded
SHA and classify each relevant path as `PASS`, `FAIL`, or `NOT COVERED`.

**Completion evidence.** Create `docs/verification/m2-runtime-parity-baseline.md` containing exact
commands, environment/runtime identity, base/head SHA, first errors, and a parity gap inventory that
determines the smallest subsequent work without selecting products.

### M2-2 — Portable persistent runtime substrate

**Status: TODO**

**Problem / gap.** H2 with Flyway disabled cannot by itself prove the reusable MariaDB + Flyway
persistence contract in a portable runtime.

**Current evidence.** The production configuration and existing schema/repository smoke preserve a
MariaDB/Flyway contract, while M2-1 will determine which startup and persistence paths are actually
covered.

**Change boundary.** Based on M2-1 evidence, select the smallest deterministic fixture—such as a CI
service MariaDB, a Kind test fixture, or an equivalent runtime fixture—that uses canonical
provider-neutral configuration, MariaDB + Flyway, backend startup/readiness, and repository
persistence. Require no AWS/GCP credentials and make no live cloud calls. Do not choose a managed
database or production topology. Restart/in-flight recovery remains M5 scope.

**Validation.** Reproduce startup/readiness, migration application, and repository persistence from
a clean state and rerun under the same documented conditions.

**Completion evidence.** Preserve the chosen fixture's rationale, exact runtime/config identity,
migration/schema result, persistence proof, and repeatable commands, tied to the M2-1 observed gap.

### M2-3 — Authenticated identity and ownership parity

**Status: TODO**

**Problem / gap.** The local smoke does not yet prove the authenticated application path from an
external identity through internal user and ownership semantics.

**Current evidence.** M1 established neutral JWT/resource-server and external identity boundaries;
the Kind smoke currently sends no bearer JWT to its protected upload call.

**Change boundary.** Validate authenticated external identity, internal user creation/resolution,
project ownership, private-resource access, unauthorized rejection, and display-name/user semantics.
A deterministic test JWT/JWK fixture or equivalent repository-owned mechanism may be used, but it is
not a production IdP. Do not restore Cognito as the active target or select a new IdP.

**Validation.** Exercise positive identity/ownership paths and negative unauthenticated/unauthorized
paths in the portable persistent runtime, preserving provider-plus-subject and internal numeric user
semantics.

**Completion evidence.** Record repeatable authenticated-runtime commands/tests, identity/config
fingerprints without secrets, ownership assertions, and rejection results.

### M2-4 — Upload → analysis → Terraform result parity

**Status: TODO**

**Problem / gap.** The complete application chain and its source/result persistence semantics have
not been proven in one portable deterministic runtime; metadata-only storage cannot prove byte
read-back.

**Current evidence.** Existing upload, project/file, analysis lifecycle, provider, validation, and
result-storage contracts are covered independently. Stub analysis output is deterministic, while
the metadata-only writer explicitly reports that bytes are not persisted.

**Change boundary.** Verify authenticated image upload → project creation → source-file registration
→ analysis-job creation → provider invocation → `TerraformDraftValidator` → analysis result →
project result-file metadata → result read-back. Stub analysis output is allowed; M2 makes no model
quality claim. If M2-1/M2-2 show that byte persistence blocks parity, evaluate the smallest
deterministic test-runtime mechanism through ADR-004. Do not automatically select GCS, restored S3,
MinIO, or an arbitrary production filesystem adapter.

**Validation.** Run the full chain under the recorded portable configuration; assert lifecycle
transitions and terminal result, Terraform validation, source/result metadata, required persisted
bytes, ownership, and read-back. Revalidate any evidence-driven fixture change under the same
conditions.

**Completion evidence.** Preserve a reproducible end-to-end result with job transitions, validation
outcome, database records, object persistence classification/read-back, and configuration identity.

### M2-5 — User/project/comment and frontend experience parity

**Status: TODO**

**Problem / gap.** Existing controller and frontend regression tests do not yet constitute portable
runtime evidence for the complete core user experience, and the need for browser-level smoke remains
unclassified.

**Current evidence.** Backend domain/controller tests cover project/file/comment contracts;
provider-neutral frontend auth/session behavior, frontend tests, and production build are reusable.
No browser E2E suite or coverage criterion is confirmed.

**Change boundary.** Verify project list/get, private/public visibility, Terraform draft read/update,
project tree, comment create/list, authenticated attribution, and unauthorized/forbidden behavior,
reusing existing controller/integration tests. Reuse the frontend auth/session boundary and build.
Add the smallest browser smoke only if M2-1 evidence shows it is required for an exit criterion. Do
not add a frontend container or Kubernetes workload merely to increase deployment components.

**Validation.** Run backend domain/controller regressions and frontend tests/build against their
documented contracts; if browser evidence is required, execute the minimal user-flow smoke
repeatably against the portable runtime.

**Completion evidence.** Record the contract matrix and exact tests for backend core flows plus the
frontend/browser evidence required by the baseline classification, including negative access cases.

### M2-6 — Runtime parity closure

**Status: TODO**

**Problem / gap.** M2 requires one consolidated, repeatable proof rather than disconnected task
artifacts.

**Current evidence.** M2-1 through M2-5 will provide baseline, persistent runtime, authentication,
core analysis flow, and user-experience evidence.

**Change boundary.** Consolidate those artifacts in
`docs/verification/m2-runtime-parity-closure.md`. Add a dedicated deterministic **M2 Runtime Parity
Verification** workflow only if the preceding evidence shows it is the smallest way to keep closure
repeatable. If closure finds a real regression, apply only the smallest attributable fix and rerun
the same conditions. Do not include M3 implementation.

**Validation.** From recorded runtime configuration and a clean state, verify reproducible startup,
authenticated runtime, MariaDB/Flyway, upload/analysis/result flow, user/project/comment flow,
persistence semantics, required frontend/browser flow, and absence of live cloud credentials or
arbitrary GCP selection.

**Completion evidence.** A consolidated closure report records commands, base/head SHA, runtime and
identity configuration, results, residual limitations, and all M2 exit-criterion mappings. After M2
closure, the immediate next single task is **Create M3 — AI Evaluation Baseline active plan**.

## Exit criteria

M2 is **COMPLETE** only when all of the following have evidence:

1. Portable runtime startup is repeatable.
2. The MariaDB + Flyway persistence contract is verified in an actual runtime.
3. Authenticated identity → internal user → ownership semantics are preserved.
4. Architecture/image upload leads to the project/source-file contract.
5. An analysis job follows the existing lifecycle and produces a terminal result.
6. Terraform draft validation, result registration, and read-back are verified.
7. Core user/project/comment contracts are preserved.
8. Required frontend user-flow evidence is repeatable.
9. Runtime configuration and identity evidence is preserved.
10. The provider-neutral application boundary remains intact.
11. Historical AWS implementation is not restored as the active target.
12. No arbitrary GCP product or topology is selected.
13. Residual limitations are explicit.
14. Consolidated M2 closure evidence exists.

Performance TPS, AI quality thresholds, and reliability recovery targets are not M2 exit criteria.

## Explicitly out of scope

- **M3:** fixed AI evaluation dataset, AI quality scoring, and failure taxonomy.
- **M4:** prompt/model/retrieval quality improvement.
- **M5/M6:** process-restart recovery, executor saturation, duplicate execution, transactional
  consistency failure injection, RabbitMQ, and outbox decisions.
- **M7:** tracing backend and observability topology modernization.
- **M8:** combined load/failure verification.
- **M9:** actual GCP deploy, rollback, and teardown closure.
- **M10:** portfolio production.

## Gated deployment decisions

M2 does not select GKE use or mode/topology, node count or VM type, a managed database, GCP object
storage, exact IdP, OpenSearch hosting/auth, generation or embedding provider/model, ingress/load
balancer, secret product, workload identity product, or observability backend. GCP delivery closure
belongs to M9.

The following capacity inputs remain `UNKNOWN/TBD`: billing/Free Trial constraints; project and
region; CPU and persistent-disk quota; IP/load-balancer constraints; GKE and database-hosting
feasibility; OpenSearch and observability capacity; and model API availability/quota. M2 activation
does not authorize assumptions or automatic Cloud Shell discovery. Product selection, when needed,
requires a separate evidence task.

RabbitMQ, Transactional Outbox, LangGraph, a persistent Python AI worker/service, Keycloak, Redis,
Kafka, and multi-agent architecture remain `DEFER` under ADR-004.

## Evidence and references

- [Repository working contract](../../../AGENTS.md)
- [AI project state](../../AI_PROJECT_STATE.md)
- [Modernization master plan](../MASTER_PLAN.md)
- [Completed M1 plan](M1-cloud-decoupling.md)
- [M1 closure verification](../../verification/m1-cloud-decoupling-closure.md)
- [Component inventory](../../architecture/component-inventory.md)
- [Target architecture](../../architecture/target-architecture.md)
- [GCP deployment architecture](../../architecture/deployment-gcp.md)
- [ADR-001](../../architecture/decisions/ADR-001-project-scope.md)
- [ADR-002](../../architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003](../../architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004](../../architecture/decisions/ADR-004-change-gates.md)
