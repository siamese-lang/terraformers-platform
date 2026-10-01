# Case C Runtime Dependency / Deployment Readiness Decision

## Status

**SELECTED / IMPLEMENTATION AUTHORIZED — NO LIVE CLOUD OR KUBERNETES MUTATION**

Decision base:

`3d4c254d24d9fcf65a602da40e759b956a368662`

This decision prepares the dependencies required to make the already-published immutable backend image
deployable on the existing GCP target runtime. It does not deploy the backend, start load generation,
change rollout strategy, or tune capacity.

## 1. Operating scenario

Case C must exercise one production-representative path:

`authenticated upload -> durable AnalysisJob -> Vertex/OpenSearch -> MariaDB/GCS persistence -> result retrieval`

The same runtime must later support healthy rollout, faulty-revision rollback, and accepted-work
survival evidence.

The immutable backend image is already published, but the full backend Deployment cannot start yet
because the target runtime still lacks:

- the selected MariaDB 11.4 VM + Persistent Disk;
- an application GCS bucket and bucket-scoped backend authority;
- a concrete DB-secret authority/delivery path;
- benchmark JWT/JWKS values for an authenticated internal request.

## 2. Root mechanism

This is not an image-delivery problem anymore. The blocker is that application state authorities are
not yet present outside the backend Pod lifetime.

A production-shaped backend Pod must not own:

- relational durability;
- uploaded source bytes;
- generated result bytes;
- database credentials.

Those dependencies must exist independently of backend replacement before rollout or load evidence
can be meaningful.

## 3. Object-storage decision

### Selected: one private GCS runtime-object bucket

Use one bucket for both uploaded source objects and generated analysis results.

Reason:

- the backend already supports `ANALYSIS_RESULT_BUCKET_NAME` falling back to
  `UPLOAD_SOURCE_BUCKET`;
- source and result objects already use separate key prefixes
  (`browser-uploads/` and `analysis-results/`);
- Case C requires persistence across backend replacement, not separate bucket failure domains;
- a second bucket would add IAM/resources without proving an additional Case C requirement.

Proposed bucket identity:

`terraformers-runtime-objects-21647422237`

Required controls:

- location: `asia-northeast3`;
- uniform bucket-level access;
- public access prevention enforced;
- no public ACL path;
- backend Workload Identity principal receives bucket-scoped
  `roles/storage.objectUser` only.

No object versioning, retention policy, lifecycle policy, or second result bucket is selected for the
first baseline. Those require separate evidence.

Google's current `roles/storage.objectUser` contract includes create/read/update/delete object
permissions, which covers the current GCS reader/writer/remover adapter without granting bucket
administration.

## 4. Secret authority / delivery alternatives

### A. Direct Kubernetes Secret injection from an operator or GitHub secret

Advantages:

- smallest initial implementation;
- no Secret Manager or controller feature.

Problems:

- the MariaDB VM and GKE backend need the same application DB credential;
- the credential authority becomes duplicated across deployment surfaces;
- VM/container recreation becomes dependent on operator-held state;
- it weakens the restart/rebuild contract.

**Decision: REJECT as the primary Case C path.**

### B. Google Secret Manager + native GKE Secret Sync

Advantages:

- one durable cloud authority for DB credentials;
- no third-party controller/CRDs;
- the existing backend `envFrom.secretRef` contract remains unchanged;
- a dedicated sync Kubernetes ServiceAccount can receive access to only the application DB password;
- the MariaDB VM identity can independently read only the DB secrets it needs;
- no service-account key is required.

Costs:

- Secret Manager becomes one additional GCP product;
- the existing GKE cluster must enable Secret Sync;
- current GKE Secret Sync requires GKE 1.33 or later;
- synchronized values become native Kubernetes Secrets and therefore inherit Kubernetes Secret/RBAC
  security semantics.

**Decision: SELECT, subject to a read-only GKE-version capability gate.**

If the current cluster is below GKE 1.33, implementation must **STOP** and reopen only the secret
delivery mechanism. Do not upgrade the cluster merely to preserve this decision.

Automatic secret rotation is not selected for the first baseline. Updating a Kubernetes Secret does
not cause the current env-var-based backend process to reload the value, so enabling rotation before a
restart/reload contract exists would create misleading semantics.

### C. Secret Manager managed CSI volume

Advantages:

- Google-managed integration;
- secret value can remain volume-backed rather than synchronized into a Kubernetes Secret.

Problem:

- the current application contract expects environment variables through
  `terraformers-backend-runtime-secrets`;
- selecting CSI volume delivery would require an application/runtime-config change unrelated to the
  Case C bottleneck.

**Decision: DEFER.**

### D. External Secrets Operator + Secret Manager

Advantages:

- repository experience exists from the historical AWS runtime;
- supports synchronization into the existing Kubernetes Secret contract.

Problems:

- adds a third-party controller, webhook, CRDs, lifecycle and resource usage;
- that operational layer is unnecessary when GKE provides native Secret Sync;
- additional resident workloads would become part of the single-node capacity baseline.

**Decision: REJECT for the first Case C baseline.**

## 5. Secret scope

Secret Manager is used only for material that is actually secret.

Proposed secret containers:

- `terraformers-mariadb-root-password`;
- `terraformers-mariadb-app-password`.

Secret **versions are not managed by Terraform** so plaintext payloads are not intentionally placed
in Terraform configuration/state.

A separately approved bootstrap action must add the initial secret versions without printing values.

Access boundary:

- dedicated MariaDB VM service account: accessor on root + application DB password secrets;
- dedicated `terraformers-secret-sync` Kubernetes ServiceAccount: accessor on the application DB
  password secret only;
- backend Kubernetes ServiceAccount: no direct Secret Manager role.

Non-secret runtime values remain ConfigMap data:

- JDBC URL;
- DB username;
- upload/result bucket name;
- JWT issuer/JWK URI/client ID;
- existing Vertex/OpenSearch configuration.

## 6. MariaDB runtime boundary

Retain the already selected relational decision:

- MariaDB 11.4;
- one `e2-medium` Compute Engine VM;
- one 20 GiB `pd-balanced` data disk mounted for `/var/lib/mysql`;
- MariaDB container pinned to an exact digest before live apply;
- private VPC address for backend traffic;
- no public MariaDB ingress;
- no replication, failover or HA claim.

The VM may use the smallest outbound bootstrap path needed to obtain OS/container artifacts.
An ephemeral external IPv4 address is acceptable for outbound bootstrap **only if** no Internet
ingress rule is created and TCP/3306 remains limited to the target GKE source ranges.

Do not add Cloud NAT solely to avoid that bounded bootstrap path unless live evidence shows it is
required.

The MariaDB VM uses a dedicated GCP service account with no user-managed key. It may read only the
two approved Secret Manager secrets.

## 7. Authentication boundary

Case C needs an authenticated API request, but selecting a new external identity product is not a
Case C requirement.

Reuse the existing deterministic JWT/JWKS fixture pattern to exercise the real Spring Security JWT
path with a Cognito-shaped test token:

- issuer and client ID are non-secret benchmark configuration;
- JWKS is internal to the target namespace;
- the signing key exists only in the bounded test/deployment workflow;
- this is **not** a production IdP selection and does not authorize Cognito, Keycloak, Identity
  Platform, or another external identity product.

## 8. Kubernetes / execution authority

Do not create a new deployment identity.

Reuse the existing protected `terraformers-apply` GitHub WIF identity for the narrowly bounded
Kubernetes prerequisite apply. Existing repository workflows already use this identity for approved
GKE/OpenSearch and serving-path mutations.

This reuse does not authorize unrestricted Kubernetes deployment. The future workflow must check its
exact resource/verb boundary before applying runtime prerequisites.

## 9. Implementation shape

The proposed implementation Work Package may prepare, but not live-apply:

### Terraform desired state

- Secret Manager API;
- two Secret Manager secret containers, no secret versions;
- native GKE Secret Sync enabled only after the version gate;
- one private GCS runtime-object bucket;
- bucket-scoped backend `roles/storage.objectUser`;
- dedicated MariaDB VM service account;
- secret-level accessor bindings for that VM identity;
- one 20 GiB balanced data disk;
- one `e2-medium` MariaDB VM;
- a firewall rule allowing MariaDB only from the required existing GKE source boundary.

### Kubernetes prerequisite manifests

A separate prerequisite surface must avoid accidentally applying the backend Deployment.

It may contain:

- `terraformers-secret-sync` ServiceAccount;
- SecretProviderClass / SecretSync mapping only the application DB password into
  `terraformers-backend-runtime-secrets`;
- deterministic internal JWKS service/config needed by the later authenticated smoke.

The current full `gcp-target` overlay must not be applied during dependency readiness because it
already includes the backend Deployment through the base.

### Bootstrap / live gates

Separate explicit approvals remain required for:

1. Terraform secret-container foundation apply (Secret Manager API + the two empty secret containers only);
2. secret-version bootstrap into those existing containers;
3. Terraform runtime-dependency foundation apply;
4. Kubernetes prerequisite apply;
5. backend Deployment in the later Work Package.

This ordering is required because Terraform owns the secret containers while secret payloads are
intentionally excluded from Terraform state. Secret versions cannot be bootstrapped before their
containers exist.

## 10. Validation plan

Before backend deployment, dependency readiness must prove:

- current GKE version satisfies the Secret Sync feature gate;
- Terraform plan contains only the reviewed dependency changes;
- no secret payload appears in Terraform plan/state evidence or workflow summary;
- MariaDB listens on the private runtime path and not through an Internet ingress rule;
- MariaDB data directory is backed by the dedicated PD;
- MariaDB container replacement preserves a marker row;
- VM restart preserves the same marker row;
- Secret Sync creates the expected Kubernetes Secret key without printing its value;
- GCS upload/read/delete succeeds through the backend workload principal;
- bucket is private and public access prevention remains enforced;
- no backend Deployment exists as a result of this unit.

Only after those pass may a separate Work Package deploy the immutable backend digest and prove the
full authenticated API -> durable job -> AI/RAG -> persistence path.

## 11. Residual risk

This decision does not solve:

- MariaDB HA or automated failover;
- database backup/restore closure;
- secret rotation without application restart;
- external production identity;
- public frontend/API ingress;
- rollout strategy;
- capacity/latency thresholds;
- HPA, replica, executor, node or OpenSearch tuning.

Those remain outside this dependency-readiness unit.

## 12. Decision gate

The user approved this selected direction by merging PR #149 and issuing `다음 작업 진행` on 2026-10-01. Repository implementation is authorized under the bounded Work Package; live mutation remains separately approval-gated.

In particular, approval must cover:

- single GCS bucket;
- Secret Manager as DB-secret authority;
- native GKE Secret Sync with a >=1.33 capability gate;
- no External Secrets Operator;
- one dedicated MariaDB VM/PD;
- no public MariaDB ingress;
- deterministic internal JWKS for Case C authentication;
- no backend Deployment in this unit.

## References

- Google Secret Manager / GKE Secret Sync:
  https://docs.cloud.google.com/secret-manager/docs/sync-k8-secrets
- Google Secret Manager GKE add-on:
  https://docs.cloud.google.com/secret-manager/docs/secret-manager-managed-csi-component
- Google Cloud Storage IAM roles:
  https://docs.cloud.google.com/storage/docs/access-control/iam-roles
- Terraform Google provider GKE cluster secret-sync configuration:
  https://registry.terraform.io/providers/hashicorp/google/latest/docs/resources/container_cluster
