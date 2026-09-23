# M2 Runtime Parity Baseline

## Status

**PENDING**

M2-1 remains `TODO`. M2 baseline run #1 established the results below at the previous PR head, but
the evidence harness fix in the current head still requires authoritative GitHub Actions validation
before M2-1 can be closed.

## Source revision

- Expected and locally checked-out base `main` SHA:
  `0dba5192e2b52e758c3eaa7e026a442d174a4ee5`.
- Validated previous PR head: `149fe5c3c6cb85feffc6b7075ebc438076ca8e61`.
- Authoritative evidence set: **M2 baseline run #1** and the M1 Cloud Decoupling Closure workflow at
  that previous head.
- PR/head SHA: to be recorded with the authoritative evidence after this change is pushed.

## Runtime identities

These are separate validation identities. Their results must not be combined into a claim about one
end-to-end environment.

- **Backend local regression:** repository Maven tests and package/local contract through
  `backend-local-verification.sh`.
- **MariaDB 11.4 + prod profile validation:** a standalone MariaDB service, canonical neutral `prod`
  profile, Flyway, Hibernate validation, and repository smoke queries. It does not use
  `prod,aws-compat` or cloud credentials.
- **Frontend Node test/build:** Node 24 dependency install, Jest tests, production build, and built
  entrypoint check. This is not browser E2E evidence.
- **Deterministic runtime-contract validation:** static/configuration and selected API regression
  checks for the neutral canonical runtime and separated historical AWS compatibility configuration.
  It makes no live cloud call.
- **Kind + local-stub profile:** a local Kubernetes cluster running the `local` profile. This is H2
  with Flyway disabled, metadata-only/disabled object storage, stub analysis, disabled embedding and
  retrieval, and logging progress publication.

## Baseline matrix

`NOT COVERED` means that the requested behavior was not exercised. The four regression results come
from their distinct M1 closure jobs; they are not evidence that those capabilities ran inside Kind.

| Area | Command / workflow | Runtime/config | Result | First failure / evidence | M2 implication |
| --- | --- | --- | --- | --- | --- |
| Backend full regression | `bash scripts/checks/backend-local-verification.sh` via M1 closure workflow | Java 17 / Maven test and package baseline | PASS | Authoritative backend regression job succeeded at the validated previous head. | Backend regression is established independently of the Kind deployment. |
| MariaDB/Flyway | `bash scripts/checks/mariadb-schema-validation.sh` via M1 closure workflow | MariaDB 11.4 + canonical neutral `prod` profile | PASS | Authoritative MariaDB job completed connectivity, Flyway/schema validation, Hibernate validation, and canonical repository queries. | This is database-contract evidence, not evidence of MariaDB integration in Kind. |
| Frontend tests/build | `npm --prefix frontend ci --legacy-peer-deps --no-audit --no-fund`; `CI=true npm --prefix frontend test -- --runInBand`; `npm --prefix frontend run build`; `test -s frontend/build/index.html` via M1 closure workflow | Node 24 unit/session/build contract | PASS | Authoritative frontend regression job succeeded at the validated previous head. | Unit/session/build parity is established; browser parity is not. |
| Browser runtime E2E | No repository-owned browser E2E was executed | Browser runtime | NOT COVERED | The frontend commands cover unit/session/build only. | Do not infer browser parity from frontend CI. |
| Runtime contract | `bash scripts/checks/runtime-contract-verification.sh` via M1 closure workflow | Neutral canonical configuration plus historical AWS compatibility separation | PASS | Authoritative runtime-contract job succeeded at the validated previous head. | This is deterministic contract evidence, not live-cloud evidence. |
| Kind cluster creation and image build/load | `bash scripts/checks/kind-local-stub-smoke.sh`; M2 baseline run #1 | GitHub runner, Kind + `local-stub` image | PASS | Runner setup, checkout, Kind cluster creation, backend Docker image build, and image load completed. | The cluster and image boundary is not the first runtime gap. |
| Kind workload apply/runtime startup | Same as above | Kind + `local-stub` | FAIL | First confirmed failure: `kubectl apply -k infra/kubernetes/overlays/local-stub` returned `namespaces "terraformers-local" not found`. | Portable Kind workload application failed before backend startup because the target namespace was absent. No runtime fix is made in M2-1. |
| Kind backend rollout | Same as above | Kind + `local-stub` | NOT COVERED | Manifest application failed before a backend workload was created. | Rollout remains downstream of the confirmed namespace/apply gap. |
| Kind health | Same as above | Kind + `local-stub` | NOT COVERED | Backend startup was not reached; no successful health artifact exists. | Health remains downstream of workload application and rollout. |
| Kind upload | Same as above | Kind + `local-stub` | NOT COVERED | Backend startup and health were not reached. | The JWT/upload mismatch is not a confirmed runtime failure. |
| Kind project tree | Same as above | Kind + `local-stub` | NOT COVERED | No successful upload/project ID exists locally. | Remains downstream of upload evidence. |
| Kind Terraform draft read | Same as above | Kind + `local-stub` | NOT COVERED | No successful upload/project ID exists locally. | Remains downstream of project-flow evidence. |
| Authenticated portable runtime | No authenticated local-stub scenario was executed | Production-like IdP/JWT path | NOT COVERED | The current smoke sends no bearer JWT and the local execution did not reach upload. | Identity parity needs evidence after the first observed Kind boundary is reviewed. |
| Object-byte persistence | No byte-persistence scenario exists in this run | Local-stub metadata-only writer / disabled reader | NOT COVERED | The selected stubs do not exercise byte persistence. | A local-stub smoke pass could not close this parity gap. |
| Active retrieval/model provider | No active-provider scenario exists in this run | Stub analysis; embedding/retrieval disabled | NOT COVERED | Active generation, embedding, and retrieval were not selected. | A local-stub smoke pass could not establish active-provider parity. |

## Evidence collection workflow

`.github/workflows/m2-runtime-parity-baseline.yml` reuses the existing Kind smoke unchanged. It
captures its exit code and artifacts, then calculates startup, health, upload, project-tree, and
Terraform-read results from the artifacts actually produced. Run #1 correctly produced
`kind_smoke_exit_code=1`, `kind_startup=FAIL`, and downstream `NOT_COVERED` results. However, the
best-effort diagnostics step inherited exit status 1 from its final `kubectl logs` command against
the unavailable workload despite using `set +e`, making the workflow fail after successful evidence
collection.
The diagnostics step now explicitly exits 0. Thus a workflow `SUCCESS` means **baseline evidence
collection completed**, not **runtime parity passed**. The existing repository workflows remain the
authoritative harnesses for backend, MariaDB, frontend, and runtime-contract regressions.

## Known gaps

- The first confirmed portable Kind gap is workload manifest application: the target namespace
  `terraformers-local` did not exist. Cluster creation and image build/load had already passed.
- Backend rollout and every HTTP scenario were not reached after the apply failure. The suspected
  unauthenticated-upload mismatch therefore remains a source-inspection hypothesis, not a confirmed
  runtime failure.
- The local-stub runtime, by configuration, does not cover MariaDB/Flyway integration, object-byte
  persistence, an active retrieval/model provider, or a production IdP.
- Browser runtime E2E has no executed evidence in this baseline.
- The evidence workflow fix itself remains pending authoritative validation at the new PR head.

## Next-task input

Before selecting M2-2, review the authoritative workflow results and record the PR/head SHA, exact
run links, and the first observed Kind boundary in this document. Passes and failures should then be
handed forward as evidence: database-contract evidence separately from Kind runtime evidence, and
the first actual Kind failure (if any) separately from all downstream `NOT COVERED` steps. No runtime
or product selection is made by this baseline.
