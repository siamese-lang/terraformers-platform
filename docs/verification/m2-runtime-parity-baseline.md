# M2 Runtime Parity Baseline

## Status

**PENDING**

M2-1 remains `TODO`. The local measurements below are an environment-limited draft; the
authoritative GitHub Actions evidence and its review are still required before M2-1 can be closed.

## Source revision

- Expected and locally checked-out base `main` SHA:
  `0dba5192e2b52e758c3eaa7e026a442d174a4ee5`.
- GitHub current `main` could not be independently queried from the Codex environment: the checkout
  has no configured remote, unauthenticated GitHub CLI access was unavailable, and outbound GitHub
  access returned HTTP 403. The local SHA matches the task's expected SHA.
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

`NOT COVERED` below means that the requested application behavior was not reached in this Codex
environment. Dependency/network/tool availability is not classified as an application failure.

| Area | Command / workflow | Runtime/config | Result | First failure / evidence | M2 implication |
| --- | --- | --- | --- | --- | --- |
| Backend full regression | `bash scripts/checks/backend-local-verification.sh` | Local Maven; intended test/package baseline | NOT COVERED | Maven could not resolve the Spring Boot parent because Maven Central returned HTTP 403; no test ran. | Re-run in authoritative CI before drawing an application conclusion. |
| MariaDB/Flyway | `bash scripts/checks/mariadb-schema-validation.sh` | Intended MariaDB 11.4 + neutral `prod` profile | NOT COVERED | No container runtime or MariaDB service/configuration was available; the script stopped at its required `SPRING_DATASOURCE_URL` precondition. | Authoritative CI must establish connectivity, migrations, Hibernate validation, and queries. A pass will remain standalone database-contract evidence, not Kind persistence evidence. |
| Frontend dependency install | `npm --prefix frontend ci --legacy-peer-deps --no-audit --no-fund` | Local Node 24/npm | NOT COVERED | The install made no progress under restricted package-network access and was interrupted; tests and build were therefore not started. | Re-run the complete four-command chain in CI. |
| Frontend tests/build | `CI=true npm --prefix frontend test -- --runInBand`; `npm --prefix frontend run build`; `test -s frontend/build/index.html` | Node unit/session/build contract | NOT COVERED | Blocked by the dependency-install environment limitation above. | Unit/build parity remains unmeasured locally. |
| Browser runtime E2E | No repository-owned browser E2E was executed | Browser runtime | NOT COVERED | The frontend commands cover unit/session/build only. | Do not infer browser parity from frontend CI. |
| Runtime contract | `bash scripts/checks/runtime-contract-verification.sh` | Neutral canonical configuration plus historical AWS compatibility separation | NOT COVERED | Required command `kubectl` was unavailable, so the script stopped before contract checks. | Re-run in CI; even a pass is deterministic contract evidence, not live-cloud evidence. |
| Kind cluster/runtime startup | `bash scripts/checks/kind-local-stub-smoke.sh`; `M2 Runtime Parity Baseline` workflow | Kind + `local-stub` | NOT COVERED | Required command `docker` was unavailable before cluster creation. | The added workflow records authoritative step-level evidence without converting an observed runtime failure into a harness failure. |
| Kind backend rollout | Same as above | Kind + `local-stub` | NOT COVERED | Cluster creation was not reached locally. | Classify from `pods.txt` and cluster diagnostics in CI. |
| Kind health | Same as above | Kind + `local-stub` | NOT COVERED | Backend rollout was not reached locally; no `health.json` exists. | Classify independently from the health artifact in CI. |
| Kind upload | Same as above | Kind + `local-stub` | NOT COVERED | Health/upload were not reached locally; no `upload-response.json` exists. | CI must record the first observable upload boundary rather than presupposing the suspected auth mismatch. |
| Kind project tree | Same as above | Kind + `local-stub` | NOT COVERED | No successful upload/project ID exists locally. | Remains downstream of upload evidence. |
| Kind Terraform draft read | Same as above | Kind + `local-stub` | NOT COVERED | No successful upload/project ID exists locally. | Remains downstream of project-flow evidence. |
| Authenticated portable runtime | No authenticated local-stub scenario was executed | Production-like IdP/JWT path | NOT COVERED | The current smoke sends no bearer JWT and the local execution did not reach upload. | Identity parity needs evidence after the first observed Kind boundary is reviewed. |
| Object-byte persistence | No byte-persistence scenario exists in this run | Local-stub metadata-only writer / disabled reader | NOT COVERED | The selected stubs do not exercise byte persistence. | A local-stub smoke pass could not close this parity gap. |
| Active retrieval/model provider | No active-provider scenario exists in this run | Stub analysis; embedding/retrieval disabled | NOT COVERED | Active generation, embedding, and retrieval were not selected. | A local-stub smoke pass could not establish active-provider parity. |

## Evidence collection workflow

`.github/workflows/m2-runtime-parity-baseline.yml` reuses the existing Kind smoke unchanged. It
captures its exit code and artifacts, then calculates startup, health, upload, project-tree, and
Terraform-read results from the artifacts actually produced. A target runtime `FAIL` therefore does
not make evidence collection itself fail when logs and classifications were successfully captured.
The existing repository workflows remain the authoritative harnesses for backend, MariaDB,
frontend, and runtime-contract regressions, avoiding duplicate implementations.

## Known gaps

- No application failure was established locally because every executable baseline was stopped by
  an environment prerequisite before the relevant behavior ran.
- The local-stub runtime, by configuration, does not cover MariaDB/Flyway integration, object-byte
  persistence, an active retrieval/model provider, or a production IdP.
- Browser runtime E2E has no executed evidence in this baseline.
- The suspected unauthenticated-upload mismatch remains a hypothesis until the Kind workflow
  reaches upload and records its HTTP/runtime evidence.

## Next-task input

Before selecting M2-2, review the authoritative workflow results and record the PR/head SHA, exact
run links, and the first observed Kind boundary in this document. Passes and failures should then be
handed forward as evidence: database-contract evidence separately from Kind runtime evidence, and
the first actual Kind failure (if any) separately from all downstream `NOT COVERED` steps. No runtime
or product selection is made by this baseline.
