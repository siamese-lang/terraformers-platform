# ADR-006: GCP target delivery state and GitHub identity (proposal)

## Status

Proposed — requires review before bootstrap or live apply. This proposal does not approve cloud resource creation, IAM grants or a Terraform apply.

## Context

M3-R2 has one target Terraform root, but its default backend is local and its live plan/apply procedure is specified for Cloud Shell. The target root has never been applied according to the 2026-09-24 readiness evidence. On 2026-09-27 the operator found no target GKE cluster, no bucket in the terraformers-platform project, no external Workload Identity pool in the queried project and no delivery service account; only the Compute Engine default service account appeared. Home-directory state files belonged to the separate application-review-platform and arp-m12-closeout repositories. At the initial GitHub settings inspection, both Rulesets and Classic branch protection were absent. Subsequent operator screenshots show the `protect-main-for-delivery` Ruleset active and applying to `main` only, with an empty bypass list, PR requirement, restricted deletions, blocked force pushes, and the GitHub Actions `terraform-static-verification` required check. Required approvals are 0; up-to-date branch and extra reviewer requirements are off. Deployment environments remain unverified.

The GKE Workload Identity Federation selected in ADR-005 authenticates workloads running in GKE. It does not authenticate GitHub Actions.

## Proposed decision

1. Use one GCS bucket with Object Versioning as the remote backend for the single gcp-target-runtime root, with a dedicated prefix and state locking. Check bucket location, lifecycle retention and account cost before creation. The bucket is a delivery foundation, not a second application runtime.
2. Bootstrap the bucket from the authorized GCP account using a reviewed, repeatable procedure. Record bootstrap ownership and recovery. If actual target state is discovered elsewhere, migrate and verify it before any CI plan against the new backend.
3. Create a dedicated external GitHub OIDC Workload Identity pool/provider, restricted to this exact repository and trusted main branch using OIDC claims. Use short-lived credentials through dedicated service accounts. Never export a JSON key. Verify the provider condition with a harmless authentication job before granting access to resource APIs.
4. Use distinct plan and apply service accounts. Plan may read target resources and write only the narrowly scoped GCS state/lock objects required by Terraform; it must not mutate GKE, Compute, IAM or project services. Apply has only the permissions needed for reviewed target-root actions, must run from a protected GitHub environment and must never reuse the Compute Engine default service account.
5. Require pull-request checks and trusted-branch protection before CI gets GCP credentials. Allow cloud authentication only from a checked-out trusted commit, never an untrusted PR or fork. First creation, deletion/replacement, IAM broadening and cost-sensitive changes need separate plan review and operator approval. An environment reviewer gate is distinct from protecting main.
6. Keep Kubernetes OpenSearch deployment and the node-count 1-to-0 lifecycle as separate, bounded jobs after the initial Terraform plan/apply succeeds. Test GKE API reachability from the runner before automating kubectl. Preserve a recovery runbook for failure during node scale-down and account for charges remaining at zero nodes.

## Required implementation proof before acceptance

- The supplied Ruleset screenshots establish active `main` targeting, PR requirement, deletion/force-push protection, empty bypass, 0 required approvals and the `terraform-static-verification` GitHub Actions required check. Inspect GitHub deployment environments separately; configure the apply-approval gate without blocking the repository's sole operator.
- Inventory state outside the searched Cloud Shell home directory as far as the selected GCP project and repository allow. Record the exact bucket/prefix and any migration result.
- Review the bucket policy, version retention/cost and the plan/apply IAM matrix against Google provider actions in the first real plan. Test that plan identity cannot create a resource.
- Verify GCS state locking/concurrency and that raw plan/state are not published as unrestricted PR or workflow artifacts.
- Inspect the first real plan with one live node before authorizing any apply. Refresh Free Trial credit/quota and duplicate-runtime checks immediately before resource creation.

## Consequences and scope

The one-time identity/state bootstrap is a separate cloud change and may incur storage costs. Subsequent Terraform runs share one protected state and no long-lived GCP key is placed in GitHub. This ADR selects no database, identity provider for application users, image registry, ingress or second cluster. It does not move M3-R3 ingestion into M3-R2.

See [the ordered M3 delivery plan](../../plans/m3-gcp-delivery-automation.md) for tasks and evidence gates.

## References

- https://developer.hashicorp.com/terraform/language/backend/gcs
- https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines
- https://docs.github.com/en/actions/reference/security/oidc
- https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments
