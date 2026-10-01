# Case C Immutable Backend Image Delivery Readiness

## Status

**LIVE-CLOSED / PASS — IMMUTABLE IMAGE PUBLISHED, BACKEND NOT DEPLOYED**

Execution base:

`488f9c2b216f7f863ebb1d09bedf9716ccdef2b9`

Work Package:

`.agents/work-packages/case-c-immutable-backend-image-delivery-readiness-v1.yml`

## Problem

Case C requires a real backend Deployment to support capacity measurement and healthy/faulty
rollout evidence. Before this unit, the GCP target overlay still referenced a registry placeholder,
Artifact Registry did not exist in the canonical Terraform root, and no dedicated GitHub image
publisher existed.

Reusing the broad Terraform apply identity to build/push images would collapse infrastructure and
artifact-publishing trust boundaries. Creating a second Terraform root/state would also split the
single target runtime.

## Selected implementation

The repository now prepares one bounded path:

`existing gcp-target-runtime state -> delivery-foundation -> Artifact Registry -> dedicated WIF publisher -> source-SHA image -> remote sha256 digest`

The implementation keeps the existing canonical Terraform root/state.

### Delivery foundation

The canonical root declares exactly five new resources:

1. `google_project_service.artifact_registry`;
2. `google_artifact_registry_repository.backend`;
3. repository `writer` for `terraformers-image-publish`;
4. repository `reader` for the GKE node identity;
5. repository `reader` for the Terraform plan identity.

The fail-closed plan gate accepts those five addresses only, as create-only actions. It rejects
extra resources, updates, deletes, replacements, wrong repository/location/format, or a broader
publisher repository role.

The operation preserves the current target node count rather than coupling registry creation to
GKE activation or idling. One bounded repository-side repair before PR creation introduced
conditional delivery resources so the historical foundation operation remains unchanged.

After the first CI pass, independent review found that the workflow also used total Terraform
state-resource counts as a proxy for delivery readiness. The user explicitly rejected that brittle
verification pattern. The correction removes all delivery/runtime total-count gates. The workflow
now checks only the foundation addresses it actually depends on, lets the exact saved-plan contract
decide what may change, and selects the delivery-foundation variable for activate/idle from the
Artifact Registry repository address already present in state. No additional verification layer was
added.

### Publisher identity

The publisher bootstrap:

- reuses the existing GitHub OIDC/WIF provider;
- binds only the exact `gcp-image-publish` environment subject;
- creates no user-managed key;
- grants no direct project resource role.

Repository writer access is owned by Terraform and scoped to `terraformers-backend`.

### Immutable publication

The manual-only `GCP Backend Image Publish` workflow:

- runs only from trusted `main`;
- requires the exact full source SHA and explicit confirmation;
- authenticates as the dedicated publisher;
- builds the existing backend Dockerfile with `BUILD_SOURCE_REVISION=<full SHA>`;
- refuses to move an already-existing source-SHA tag;
- publishes no `latest` tag;
- resolves and validates the remote Artifact Registry `sha256` digest;
- records a digest-qualified deployment reference;
- performs no Terraform or Kubernetes mutation.

## Security trade-off retained explicitly

Artifact Registry repository creation and repository IAM management require infrastructure
administration before a repository-scoped boundary exists. The protected Terraform apply identity
therefore adds Artifact Registry administration for this infrastructure path. The actual image
publisher and GKE puller remain repository-scoped, and the delivery-foundation plan gate limits the
reviewed Terraform mutation to the exact five resources above.

## Acceptance evidence

Repository/static acceptance passed before live execution. PR #147 was independently reviewed and
merged as `0774c80bb3cf9f684567e30c1e329fc27dc05d04`. Terraform Static Verification run
`36832747254` passed on the final PR head after the brittle total-state-count gates were removed.

The user then separately approved each live checkpoint on 2026-10-01.

1. **Publisher identity and GitHub environment bootstrap — PASS**
   - `terraformers-image-publish@terraformers-platform.iam.gserviceaccount.com` was created;
   - the exact `gcp-image-publish` GitHub environment WIF subject was bound;
   - the publisher had no direct project resource role and no user-managed service-account key;
   - the GitHub environment was restricted to `main` and the required non-secret variables matched.
2. **Terraform apply identity refresh — PASS**
   - the existing protected `terraformers-apply` identity received
     `roles/artifactregistry.admin` for repository creation/IAM management;
   - the existing apply roles and state-bucket roles remained present;
   - no forbidden Owner/Editor/key/token-creator role was present.
3. **Delivery foundation — PASS**
   - workflow run `36833957446` used source
     `0774c80bb3cf9f684567e30c1e329fc27dc05d04`;
   - the reviewed plan was exactly `5 to add, 0 to change, 0 to destroy`;
   - the exact saved plan applied successfully: `5 added, 0 changed, 0 destroyed`;
   - Artifact Registry API, the Seoul `terraformers-backend` Docker repository, publisher writer,
     GKE node reader, and Terraform plan reader were created;
   - the post-apply runtime boundary passed and repository format was `DOCKER`.
4. **First immutable backend image publication — PASS**
   - workflow run `36834457170` used the same source SHA;
   - `BUILD_SOURCE_REVISION` inside the built image matched that exact source SHA;
   - only the full-source-SHA tag was pushed; no mutable `latest` tag was published;
   - the remote digest was resolved and revalidated as
     `sha256:a9331bc8026075390cedd8bfcdc8625b5cc69cdf16cd3799e2029beff6f857ae`;
   - the immutable deployment reference is
     `asia-northeast3-docker.pkg.dev/terraformers-platform/terraformers-backend/terraformers-backend@sha256:a9331bc8026075390cedd8bfcdc8625b5cc69cdf16cd3799e2029beff6f857ae`.

No Kubernetes backend Deployment was created by these checkpoints.

## Closure boundary

This unit proves an immutable source-to-registry delivery path with separated publisher and
infrastructure identities. It does **not** prove backend startup, MariaDB/GCS/JWT runtime
integration, rollout availability, rollback, accepted-job survival, or load/capacity behavior.
Those remain Case C work and require a separate approved Work Package.
