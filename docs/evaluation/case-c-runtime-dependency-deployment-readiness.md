# Case C Runtime Dependency / Deployment Readiness Evidence

## Status

**LIVE PREREQUISITES COMPLETE / FINAL RUNTIME LIVE ACCEPTANCE IMPLEMENTED — RESTART NOT YET EXECUTED**

Original implementation base:

`8ada2368b85c210c2c50b0d42370454efe0d499c`

Current live-acceptance implementation base:

`9baaa62244ce9304490bf2c36f6b42bb030fdbd0`

Selected decision:

`docs/plans/active/case-c-runtime-dependency-deployment-readiness-decision.md`

Work Package:

`.agents/work-packages/case-c-runtime-dependency-deployment-readiness-v1.yml`

The backend Deployment is still absent. No load generation, rollout/rollback experiment, replica/HPA
change, executor tuning, node resize, OpenSearch resize, or external IdP has been started.

## Capability and IAM gates

The read-only GKE capability gate observed control plane and node pool
`1.35.8-gke.1225000`, satisfying the native Secret Sync >=1.33 requirement without an upgrade.

The Case C runtime IAM bootstrap is complete. The final read-only check passed for:

Apply identity:
- `roles/compute.instanceAdmin.v1`;
- `roles/compute.securityAdmin`;
- `roles/secretmanager.admin`;
- `roles/storage.admin`.

Plan identity:
- `roles/browser`;
- `roles/compute.viewer`;
- `roles/container.viewer`;
- `roles/iam.serviceAccountViewer`;
- `roles/iam.securityReviewer`;
- `roles/serviceusage.serviceUsageConsumer`;
- `roles/secretmanager.viewer`;
- `roles/storage.bucketViewer`.

PR #151 hardened the bootstrap after a Cloud Resource Manager 429 exposed that repeated
`get-iam-policy` calls could be charged to a shared Cloud SDK quota project and incorrectly
classified as missing roles. The correction pins `terraformers-platform` as the quota project,
caches the IAM policy, and fails closed on policy-read errors. Terraform Static Verification run
`36846676527` passed.

## Completed live checkpoints

### 1. Secret container foundation — PASS

Protected workflow run `36847201560` executed on source
`9baaa62244ce9304490bf2c36f6b42bb030fdbd0`.

The exact gated plan and apply were:

`3 added, 0 changed, 0 destroyed`

Created:
- Secret Manager API enablement;
- `terraformers-mariadb-root-password` secret container;
- `terraformers-mariadb-app-password` secret container.

Terraform created no secret version or payload.

### 2. Initial secret versions — PASS

The separately approved operator bootstrap created version `1` for both MariaDB secrets.
Payload values were generated in memory and were not printed, committed, uploaded, or written into
Terraform state. The bootstrap is intentionally non-rotating and refuses an already-enabled version.

### 3. Runtime dependencies — PASS

Protected workflow run `36851221194` executed the exact MariaDB image:

`mariadb:11.4@sha256:70cc072b29b4a89ae07abb2d4da2c64678a7f2dfe092751bb51c87d67dc1338b`

The gated plan and apply were:

`9 added, 1 changed, 0 destroyed`

The only existing-resource update enabled native GKE Secret Sync. The creates were the reviewed
private runtime GCS bucket and backend bucket IAM member, dedicated MariaDB service account,
three secret-access IAM members, 20 GiB `pd-balanced` data disk, `e2-medium` MariaDB VM, and
TCP/3306 firewall limited to the existing GKE source boundary.

The workflow verified after apply:
- Secret Sync enabled;
- runtime bucket private with UBLA and public access prevention enforced;
- MariaDB VM `RUNNING`;
- backend Deployment not applied;
- secret payloads not printed or uploaded.

### 4. Kubernetes prerequisites — PASS

Protected workflow run `36854218346` applied only the isolated prerequisite surface.

Created:
- `terraformers-secret-sync` ServiceAccount;
- internal JWKS ConfigMap, Service, and Deployment;
- selector-less `terraformers-mariadb` Service;
- `SecretSync`;
- `SecretProviderClass`;
- MariaDB EndpointSlice populated from the live VM private IP.

The run then proved:
- internal JWKS Deployment rolled out;
- synchronized Kubernetes Secret contains non-empty `SPRING_DATASOURCE_PASSWORD` without
  printing its value;
- MariaDB Service/EndpointSlice exists;
- backend Deployment remains absent.

## Final runtime live-acceptance operation

The existing manual-only `.github/workflows/gcp-target-runtime-dependencies.yml` is extended with
one additional operation, `runtime-live-acceptance`. A new workflow is deliberately not created.

The operation is protected by the existing `gcp-target-apply` environment and requires:
- exact current main SHA;
- exact confirmation token `RUN_REVIEWED_GCP_RUNTIME_LIVE_ACCEPTANCE_RESTART`;
- exact `mariadb:11.4@sha256:<digest>` matching the VM startup script.

Before mutation it fails closed unless:
- GKE Secret Sync is enabled;
- MariaDB VM is `RUNNING`;
- the immutable MariaDB digest matches the installed startup script;
- the startup script still binds `/var/lib/mysql` and recreates the MariaDB container;
- the runtime bucket keeps UBLA and public access prevention and has no public IAM principal;
- Secret Sync produced the expected password key;
- MariaDB EndpointSlice exists;
- backend Deployment is absent.

### GCS acceptance

The operation applies only the existing base `terraformers-backend` ServiceAccount, not the
backend Deployment.

It then starts a temporary probe Pod using the already-published immutable backend image:

`asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:a9331bc8026075390cedd8bfcdc8625b5cc69cdf16cd3799e2029beff6f857ae`

The Pod uses `serviceAccountName: terraformers-backend` and obtains a token from the GKE metadata
server. Against `terraformers-runtime-objects-21647422237` it must complete:

`write -> read exact payload -> delete -> confirm 404`

The temporary object and Pod are removed.

### MariaDB persistence/restart acceptance

A temporary MariaDB client Pod consumes the synchronized application password through
`secretKeyRef`, never through a GitHub runner environment variable.

Before restart it:
- connects over the private Kubernetes Service/EndpointSlice path;
- creates a temporary marker table/row;
- records the MariaDB container hostname.

The protected apply identity then performs one Compute Engine VM stop/start. The workflow requires
the VM private IP to remain unchanged.

After startup, a second client Pod waits until:
- the exact marker row is still present; and
- MariaDB reports a different container hostname.

Because the VM startup script removes/recreates the MariaDB container and mounts the dedicated PD at
`/var/lib/mysql`, the combination of marker survival and changed container identity is the live
evidence for both container-replacement persistence and VM-restart persistence. The temporary marker
table is dropped after verification.

No SSH/IAP ingress or additional management path is introduced.

## Final boundary after acceptance

A successful live-acceptance run must still prove:
- runtime bucket PAP/UBLA remain enforced;
- Secret Sync password key remains present without printing the value;
- backend Workload Identity ServiceAccount exists;
- MariaDB EndpointSlice exists;
- backend Deployment remains absent;
- temporary acceptance Pods and objects are cleaned.

## Remaining gate

The workflow implementation itself must pass PR CI and independent review first.

The actual VM stop/start is a separate live checkpoint and has **not** been executed by this
implementation branch.

Only after that reviewed live-acceptance run passes can this runtime-dependency/deployment-readiness
unit be closed and the later backend Deployment Work Package be considered.
