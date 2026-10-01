# Case C Runtime Dependency / Deployment Readiness Evidence

## Status

**IMPLEMENTED / CI PENDING — NO LIVE CLOUD, SECRET, VM, BUCKET OR KUBERNETES MUTATION**

Execution base:

`8ada2368b85c210c2c50b0d42370454efe0d499c`

Selected decision:

`docs/plans/active/case-c-runtime-dependency-deployment-readiness-decision.md`

Work Package:

`.agents/work-packages/case-c-runtime-dependency-deployment-readiness-v1.yml`

## Capability gate

The user ran the required read-only cluster capability check before Work Package activation.

Observed on 2026-10-01:

- GKE control plane: `1.35.8-gke.1225000`;
- GKE node pool: `1.35.8-gke.1225000`;
- release channel: `REGULAR`;
- cluster status: `RUNNING`;
- node pool status: `RUNNING`.

The selected native GKE Secret Sync path therefore satisfies the >=1.33 gate. No cluster upgrade was
performed.

## Implemented desired state

The existing canonical `gcp-target-runtime` Terraform root now contains opt-in Case C runtime
dependencies.

### Secret authority

- Secret Manager API;
- `terraformers-mariadb-root-password` secret container;
- `terraformers-mariadb-app-password` secret container;
- no `google_secret_manager_secret_version` resource;
- secret payloads remain outside Terraform configuration/state.

The original execution ordering was corrected during implementation. Because Terraform owns the
secret containers but must not own the values, live execution is:

`empty secret containers -> operator secret versions -> remaining runtime dependencies`.

This consumed the Work Package's one bounded repair.

### GCS

One bucket is declared:

`terraformers-runtime-objects-21647422237`

Contract:

- location `asia-northeast3`;
- uniform bucket-level access enabled;
- public access prevention enforced;
- `force_destroy=false`;
- backend Workload Identity principal receives bucket-scoped
  `roles/storage.objectUser`;
- no second result bucket.

### MariaDB

The selected relational shape is represented as:

- one dedicated `terraformers-mariadb` service account;
- one `e2-medium` zonal VM;
- one 20 GiB `pd-balanced` data disk;
- data disk mounted into `/var/lib/mysql`;
- MariaDB container input must match
  `mariadb:11.4@sha256:<64 lowercase hex>`;
- root/app passwords are retrieved at VM startup from Secret Manager through the VM identity;
- no service-account key;
- no public TCP/3306 firewall;
- MariaDB ingress limited to the existing GKE subnet and Pod CIDR;
- the VM may have an ephemeral outbound IPv4 bootstrap path, but no Internet database ingress rule.

### GKE Secret Sync

The cluster update is limited to native Secret Sync enablement. Automatic rotation is not selected.

A dedicated Kubernetes ServiceAccount named `terraformers-secret-sync` receives access to only the
application DB password secret. The backend ServiceAccount does not receive direct Secret Manager
access.

The isolated Kubernetes prerequisite surface contains:

- `terraformers-secret-sync` ServiceAccount;
- GKE `SecretProviderClass`;
- `SecretSync` targeting existing Secret name
  `terraformers-backend-runtime-secrets`;
- only `SPRING_DATASOURCE_PASSWORD` is synchronized;
- a selector-less `terraformers-mariadb` Service;
- EndpointSlice population from the live VM private IP in the protected workflow;
- internal `terraformers-jwks` fixture ConfigMap/Deployment/Service.

The prerequisite kustomization does not reference the backend base/Deployment.

## Workflow boundaries

A new manual-only workflow,
`.github/workflows/gcp-target-runtime-dependencies.yml`, defines three independently approved
operations:

1. `runtime-secret-foundation`
   - exact contract: 3 creates;
   - Secret Manager API + two empty secret containers only.
2. `runtime-dependencies`
   - exact contract: 9 creates + 1 existing GKE cluster update;
   - requires an exact MariaDB image digest;
   - requires both secret containers and enabled secret versions;
   - rechecks GKE >=1.33.
3. `kubernetes-prerequisites`
   - applies only the isolated runtime dependency kustomization;
   - creates the MariaDB EndpointSlice from the private VM IP;
   - verifies the synchronized DB password key exists without printing it;
   - verifies the internal JWKS Deployment is ready;
   - fails if the backend Deployment already exists or is created.

No operation is automatic or PR-triggered.

## Plan/apply identity boundary

`scripts/deploy/bootstrap_gcp_runtime_secrets.sh` defines a separate IAM checkpoint.

The protected Terraform apply identity needs:

- `roles/compute.instanceAdmin.v1`;
- `roles/compute.securityAdmin`;
- `roles/secretmanager.admin`;
- `roles/storage.admin`.

The Terraform plan identity receives read-only additions:

- `roles/secretmanager.viewer`;
- `roles/storage.bucketViewer`.

The plan workflow also asserts that the plan identity has none of the checked compute/container/
Secret Manager/Storage mutation permissions and no Secret Manager payload access.

The IAM bootstrap is repository-defined but **has not been executed**.

## Existing target workflow compatibility

The existing `GCP Target Terraform Apply` runtime-check/activate/idle paths now derive the Case C
runtime enable flags from canonical Terraform state. If MariaDB exists, they recover the exact
immutable MariaDB image from the stored startup-script value before planning.

This prevents a routine target scale/check operation from interpreting opt-in defaults as a request
to delete the newly created Case C dependencies.

## Static acceptance

Pending CI must prove:

- Terraform init/fmt/validate for `gcp-target-runtime`;
- exact-plan gate positive tests for both new Terraform operations;
- rejection of an extra resource;
- rejection of broad GCS IAM;
- rejection of public MariaDB firewall;
- rejection of mutable MariaDB image;
- rejection of Secret Sync automatic rotation;
- rejection of Terraform-managed secret versions;
- shell syntax for the bounded IAM/secret bootstrap;
- runtime workflow remains manual-only;
- Kubernetes prerequisite kustomization remains isolated from backend Deployment.

## Live gates still unapproved

Nothing in this implementation authorizes live mutation.

Separate user approval remains required for:

1. runtime IAM bootstrap;
2. `runtime-secret-foundation` apply;
3. initial secret-version bootstrap;
4. `runtime-dependencies` apply;
5. Kubernetes prerequisite apply.

A later Work Package is required for the backend Deployment.

## Live acceptance still pending

After the above live checkpoints are separately approved, this Work Package still must prove:

- MariaDB starts on the private runtime path;
- the dedicated PD backs `/var/lib/mysql`;
- container replacement preserves a marker row;
- VM restart preserves the same marker row;
- Secret Sync creates the expected Kubernetes Secret key without printing its value;
- GCS write/read/delete works through the backend workload principal;
- bucket remains private;
- backend Deployment remains absent.

Those are runtime evidence, not static-CI substitutes.
