# Case C Immutable Backend Image Delivery Readiness

## Status

**IMPLEMENTED / CI PENDING — NO LIVE CLOUD OR IAM ACTION**

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
GKE activation or idling. One bounded repository-side repair was required before PR creation:
unconditional delivery resources would have invalidated the historical 12-resource foundation and
pre-delivery runtime-check contracts. The delivery resources are therefore conditionally enabled;
foundation explicitly keeps them disabled, delivery-foundation enables them, and runtime-check
matches the pre-delivery 12-resource or post-delivery 17-resource state without planning deletion.

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

## Validation pending

Static acceptance requires:

- Python compile/unit tests for the extended plan gate;
- automatic PR workflow-policy PASS;
- shell syntax checks for both new bootstrap scripts;
- static checks proving image publication is manual-only, source-SHA bound, digest-resolving and
  contains no Terraform apply or kubectl action;
- Terraform static verification PASS.

No live Artifact Registry API enable, repository creation, IAM mutation, image push or backend
deployment is part of this Work Package.

## Next gate

After CI and independent PR review pass, merge still requires user approval.

After merge, live publisher bootstrap, delivery-foundation apply, and first immutable image
publication remain three separately approved actions. MariaDB/GCS application-runtime integration
does not start automatically.
