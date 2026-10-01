# GCP Backend Immutable Image Delivery Bootstrap

## Status

**REPOSITORY CONTRACT ONLY — DO NOT EXECUTE WITHOUT SEPARATE LIVE APPROVAL**

This runbook implements the Case C immutable backend image-delivery decision while reusing the
canonical `gcp-target-runtime` Terraform root, state bucket and GitHub OIDC provider.

Repository implementation does not itself create Artifact Registry resources, mutate IAM, publish an
image, or deploy Kubernetes resources.

## 1. Fixed identities and resource contract

Artifact Registry:

- project: `terraformers-platform`
- location: `asia-northeast3`
- Docker repository: `terraformers-backend`

Dedicated publisher:

- service account: `terraformers-image-publish@terraformers-platform.iam.gserviceaccount.com`
- GitHub environment: `gcp-image-publish`
- authentication: existing `terraformers-github/terraformers-main` Workload Identity provider
- no service-account JSON key
- no direct project resource role
- repository role after Terraform apply: `roles/artifactregistry.writer`

Pull/read identities:

- GKE node service account: repository-scoped `roles/artifactregistry.reader`
- Terraform plan service account: repository-scoped `roles/artifactregistry.reader`

The Terraform apply identity receives `roles/artifactregistry.admin` at project scope because it
must create the repository and set repository IAM before the repository exists. This is a sensitive
infrastructure role. It remains behind the protected `gcp-target-apply` environment and the
repository-owned plan gate, which accepts only the reviewed five-resource delivery foundation.
The publisher and GKE pull identities do not receive this role.

## 2. Live execution order

Do not combine these checkpoints.

### A. Publisher identity bootstrap

After explicit approval, from an authorized Cloud Shell:

```bash
cd ~/terraformers-platform
git switch main
git pull --ff-only

bash scripts/deploy/bootstrap_gcp_image_publish.sh apply
bash scripts/deploy/bootstrap_github_image_publish_environment.sh apply
```

The first script may only:

- create the dedicated publisher service account when absent;
- bind the exact `gcp-image-publish` GitHub environment subject as
  `roles/iam.workloadIdentityUser`;
- verify no user-managed service-account key exists;
- verify the publisher has no direct project resource role.

It intentionally does not grant Artifact Registry writer.

The second script configures the protected GitHub environment and non-secret variables. It does not
dispatch any workflow.

### B. Refresh the Terraform apply identity contract

After separate IAM approval:

```bash
bash scripts/deploy/bootstrap_gcp_target_apply.sh apply
```

The change relevant to this delivery unit is `roles/artifactregistry.admin` on the existing,
protected Terraform apply identity. Do not grant this role to the image publisher.

### C. Delivery foundation apply

Review current main SHA, current Free Trial budget, and the exact preflight summary. Then dispatch:

```text
GCP Target Terraform Apply
operation: delivery-foundation
expected_sha: <exact current 40-character main SHA>
confirmation: APPLY_REVIEWED_GCP_DELIVERY_FOUNDATION_5
```

The preflight and apply gate require:

- the existing canonical 12-resource state;
- the current GKE node count to remain unchanged;
- the dedicated publisher service account to exist;
- exactly five create actions:
  1. Artifact Registry API service resource;
  2. `terraformers-backend` Docker repository;
  3. repository writer for the dedicated publisher;
  4. repository reader for the GKE node identity;
  5. repository reader for the Terraform plan identity;
- zero update, delete, replacement or additional managed actions.

The resulting canonical state is expected to contain 17 managed resources.

### D. Immutable backend image publication

Only after the delivery foundation passes, dispatch:

```text
GCP Backend Image Publish
expected_sha: <exact current 40-character main SHA>
confirmation: PUBLISH_REVIEWED_BACKEND_IMAGE
```

The workflow:

1. accepts `main` only;
2. authenticates only as the dedicated publisher through WIF;
3. confirms the Docker repository exists;
4. rejects publication if the full-source-SHA tag already exists;
5. builds `backend/Dockerfile` with
   `BUILD_SOURCE_REVISION=<full source SHA>`;
6. pushes only the full-SHA tag;
7. resolves the remote `sha256` digest with Artifact Registry;
8. verifies the digest-qualified image exists;
9. records the future deployment reference as
   `asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:...`.

It does not run Terraform, kubectl, or a backend Deployment.

## 3. Not authorized by this runbook

This delivery unit does not authorize:

- MariaDB VM/disk creation;
- application GCS bucket creation;
- database credential selection or delivery;
- Kubernetes backend Deployment;
- rollout/rollback tests;
- load generation;
- automatic image publication on every merge.

Each live mutation remains a separate user checkpoint.
