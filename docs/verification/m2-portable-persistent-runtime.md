# M2 Portable Persistent Runtime

**Status: PENDING**

## Evidence identity

- Repository: `siamese-lang/terraformers-platform`
- Source/base SHA: `e0379b901042e301bb3498ec039c2f564517394a`
- Authoritative runner: `.github/workflows/m2-portable-persistent-runtime-verification.yml`
- Evidence directory: `artifacts/m2-portable-persistent-runtime/`

The expected base SHA matched the checked-out repository. Direct GitHub-main lookup was attempted
before implementation, but this environment had neither an `origin` remote nor authenticated GitHub
CLI access; authoritative CI will check out current GitHub `main`/the PR merge commit. This report
must remain `PENDING`, and M2-2 remains `TODO`, until that workflow succeeds and the evidence is
reviewed.

## Gap and smallest directly related fixture

M2-1 proved backend/frontend regression, MariaDB 11.4 schema/repository validation, deterministic
runtime contracts, Kind creation, and backend image build/load. Its first target-runtime failure was
`kubectl apply -k infra/kubernetes/overlays/local-stub`: namespace `terraformers-local` did not
exist. Backend rollout and health were consequently not covered.

The new, separate `portable-persistent` overlay keeps that baseline unchanged. It is self-contained:
it declares namespace `terraformers-portable`, MariaDB `11.4` plus a Service and readiness probe,
fixture-only credentials, the existing backend base, and a local image with
`imagePullPolicy: Never`. MariaDB uses `emptyDir` because this clean-run fixture proves Flyway-created
schema and repository read/write/query semantics, not node/cluster survival or a production storage
topology.

## Runtime and configuration identity

| Property | Fixture value |
| --- | --- |
| Namespace | `terraformers-portable` |
| Database | `mariadb:11.4`, database/user `terraformers` |
| Backend image | existing `backend/Dockerfile`, `terraformers-backend:portable-persistent` |
| Spring profile | `prod` |
| Database URL | `jdbc:mariadb://terraformers-mariadb:3306/terraformers` |
| Object reader/writer | `disabled` / `metadata-only` |
| Analysis/embedding | `stub` / `disabled` |
| Retrieval/progress | `DISABLED` / `logging` |
| Upload bucket | logical fixture value only; no bucket is created |
| CloudWatch export | explicitly disabled with `MANAGEMENT_CLOUDWATCH_METRICS_EXPORT_ENABLED=false` |

The deterministic JWT issuer, unreachable JWK URI, `JWT_PROVIDER=cognito`, and fixture client ID
only satisfy the current prod startup configuration. They do not select a production IdP, restore
Cognito as an active target, provide a bearer token/JWK server, or prove authenticated runtime
parity. No AWS/GCP credentials, cloud role, or live cloud call is required.

## Reproduction and evidence

Run from a clean checkout:

```bash
bash scripts/checks/kind-portable-persistent-smoke.sh
```

The verifier creates or reuses Kind, builds and loads the backend image, deletes any prior fixture
namespace to obtain a clean ephemeral database, applies the overlay in one command, waits for
MariaDB and backend rollouts, and reads `/actuator/health`. It queries
`flyway_schema_history` for successful migration rows, then port-forwards that same in-cluster
MariaDB and runs the existing `MariaDbRepositorySmokeTest` under the prod profile. That test is
`@Transactional`, so it proves repository read/write/query semantics while rolling back its test
data; Flyway schema existence is established separately after backend startup.

Expected machine-readable `summary.txt` after authoritative success:

```text
namespace_apply=PASS
mariadb_ready=PASS
backend_rollout=PASS
backend_health=PASS
flyway_schema=PASS
repository_smoke=PASS
cloud_credentials_required=false
```

Artifacts include redacted rendered manifests, pod/service inventories, backend health, backend and
MariaDB logs, successful Flyway history, repository smoke output, port-forward diagnostics, and the
summary. Rendered Secret values are redacted.

## Same-condition comparison and pending results

Before (M2-1): cluster/image **PASS** → namespace/workload apply **FAIL**.

After (M2-2 target): namespace/apply → MariaDB readiness → prod backend rollout → health →
Flyway/schema → repository smoke. Results remain **PENDING** until authoritative CI executes the
fixture; this initial PR does not claim those runtime stages as passed.

## Explicit limitations

This fixture does not prove authentication/ownership, protected upload/project/comment/Terraform
flows, source or result object-byte persistence, active retrieval/model behavior, browser E2E,
restart recovery, node or cluster recreation durability, backup/restore, failover, HA, a PVC,
managed database, or production readiness. Those remain later-task or M5 concerns. No cloud product
or production topology is selected.
