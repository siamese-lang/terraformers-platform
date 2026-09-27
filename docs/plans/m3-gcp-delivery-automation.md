# M3 GCP Delivery Automation — Implementation Plan

Status: **ADR-006 design accepted; no cloud mutation authorized by this document**
Baseline: `main` `d3eb25f12548414954c0331fa57576f114c10588` (2026-09-24)  
Owner milestone: M3-R2; the first incomplete product task remains the first target Terraform plan.

## Why this is a prerequisite

M3-R2 has a reusable target Terraform root and static verification, but the live procedure still
would use one Cloud Shell session and default to local Terraform state, private `terraform.tfvars`, manual
`kubectl`, and manual scale-down. A lost session/state, an unreviewed apply, or a forgotten live node
would undermine the single-target Free Trial operating rule. Delivery work here is limited to making
that existing runtime reproducible and reviewable; it does not add an environment or change M3's AI
evaluation goal.

`AGENTS.md`, the [active M3 plan](active/M3-ai-evaluation-baseline.md),
[ADR-005](../architecture/decisions/ADR-005-target-ai-rag-runtime.md), and
[2026-09-24 readiness evidence](../evaluation/m3-r2-live-readiness-evidence.md) govern the runtime.
The [accepted ADR-006](../architecture/decisions/ADR-006-gcp-delivery-state-and-identity.md) specifies the state and identity trust boundaries.
The root README and historical AWS workflows are references, not current GCP state. The active IaC
tool is **Terraform 1.15.x**, not OpenTofu. Workload Identity Federation for **GKE workloads** is
already implemented; GitHub Actions federation is a separate decision.

## Current boundary and unknowns

| Item | Verified from repository | Must be checked live before use |
| --- | --- | --- |
| `infra/terraform/envs/gcp-target-runtime` | GKE/VPC/node pool and GKE workload identity exist; no remote backend | Any existing local or remote state, unmanaged target resources, subnet CIDR conflict |
| `.github/workflows/terraform-static-verification.yml` | Includes the GCP root via `-backend=false`; screenshots show the `terraform-static-verification` job required by the active `main` Ruleset | Deployment environment protection and actual CI cloud identity still unverified |
| AWS delivery workflows | OIDC, review and plan-summary patterns exist | No AWS role, backend, or apply permissions transfer to GCP |
| `scripts/deploy/summarize-terraform-plan.py` | General action counts and deletion detection; high-cost/public-exposure checks are AWS-specific | GCP-specific plan safety tests before using it as a gate |
| GCP account | Operator recorded billing, Free Trial credit, quota and no cluster on 2026-09-24 | Fresh credit, resource/state inventory, IAM rights and CI federation feasibility |
| Kubernetes workload | OpenSearch manifests and a manual readiness runbook exist | Control-plane access from the chosen runner, PVC behavior, safe scale-down |

Never interpret the repository's absence of a backend as proof that no local state exists in Cloud
Shell. A `terraform.tfstate` found there must be backed up securely and migrated to the one remote
backend; do not initialize a new empty state over already managed resources.

## 2026-09-27 operator inventory (read-only, partial)

The operator ran the A-stage commands in the `terraformers-platform` GCP project:

- GKE cluster list: no rows returned.
- Seoul subnet list: only `default` (`10.178.0.0/20`) returned. Check the proposed target CIDR against this and any other VPC/subnet allocation before apply.
- Project GCS bucket list: no rows returned. This does not prove the absence of state in a different account/project or backend.
- Home-directory search for `terraform.tfstate` and `.backup`: results were under the **separate** `application-review-platform` and `arp-m12-closeout` OpenTofu trees; none was under a `terraformers-platform` target root. Paths inside `.terraform/` may describe backend configuration rather than managed-resource state. Do not move, delete or import any of these other-project files.
- External Workload Identity pool list: no rows returned. This query does not inventory GKE's built-in workload identity pool.
- Service account list: only the Compute Engine default service account was returned; no dedicated GitHub delivery service account appears.
- At the initial GitHub repository inspection, the Rulesets page showed “You haven't created any rulesets,” and Settings → Branches showed no Classic branch protection. Subsequent operator screenshots show `protect-main-for-delivery` **Active**, applying to one target (`main`), with an empty bypass list. Require a pull request before merging, restrict deletions and block force pushes are checked. Later screenshots show **Require status checks to pass** enabled with `terraform-static-verification` sourced from GitHub Actions. PR required approvals are **0**; up-to-date branch, Code Owners/team review, approval of the latest push and conversation-resolution requirements are unchecked.

This narrows the first-target-state risk but is not an exhaustive cloud inventory. Existing external federation and a dedicated delivery identity were not listed; the displayed main protection settings are verified from the operator screenshots. Deployment environment gates and any state outside the searched home directory remain unverified. No GCP creation or apply was performed.

## Target responsibility split

1. A human selects the next small M3 work item and approves material architecture, IAM, cost and
   destructive decisions. An agent may edit code, Terraform, tests and docs and open a PR.
2. PR CI performs static checks without cloud credentials. PRs and forks never receive a deployment
   identity. Protect `main` with required PR/check rules before granting CI cloud access.
3. A trusted `main` workflow requests a **plan-only** GCP identity for target resources,
   uses a locked/versioned state, and publishes a sanitized resource/action summary. Terraform
   state locking can require narrowly scoped writes to the GCS backend even during plan; this
   identity must not have target-resource mutation rights. Only authorized operators see raw
   plan/state.
4. A separately gated apply job verifies the exact commit, input values and reviewed plan identity;
   it fails on unexpected delete/replace or IAM/network/cost changes. Human approval is required
   for first creation and sensitive changes. Avoid automatic apply on every merge.
5. Kubernetes readiness/evidence and the return to `node_count=0` are explicit operations on the
   same runtime. Failure must alert and leave a clear manual recovery path; do not claim a `finally`
   step can always scale nodes down if the runner, credentials or cluster fail.

## Ordered implementation and acceptance gates

### A. Inventory and GitHub governance (no GCP mutation)

- The operator's screenshots verify an active Ruleset targeting `main` with PR requirement, deletion/force-push protection, no bypass, 0 required approvals and the observed `terraform-static-verification` job as the required GitHub Actions check. The up-to-date branch requirement is off, avoiding a needless rebase gate for unrelated commits. The manually dispatched GCP plan workflow is not required on PRs. Do not test the rule by trying a direct push to `main`.
- Inspect GitHub deployment environments separately. Before any apply credentials are added, configure an apply environment restricted to `main` with the operator as required reviewer. Verify whether preventing self-review would deadlock a deployment the same operator initiated; do not enable that option without a second reviewer.
- In Cloud Shell, perform read-only checks for project/account, existing GKE/VM/VPC/subnet/bucket,
  Workload Identity pools/providers/service accounts and Terraform state files. Record resource
  names/counts and commands without tokens, account email, bucket objects or state contents.
- Refresh Free Trial credit/billing and the quota/model/duplicate-runtime gates near first apply.
  The 2026-09-24 snapshot is historical evidence, not a permanent gate pass.

**Exit:** exact existing-state status and GitHub protection settings are known; no cloud resources
or new permissions were created.

### B. Delivery identity and state decision

- Record a narrow delivery ADR for one GCS backend bucket and prefix, object versioning, retention
  and recovery, bootstrap ownership, and separate plan/apply trust boundaries. Check bucket naming,
  location and cost against the Free Trial account. Never store a service-account JSON key.
- Use GitHub OIDC → GCP Workload Identity Federation with an attribute condition restricted to the
  exact repository and trusted `main` ref; if separate plan/apply GitHub environments are used,
  restrict their allowed identities and branches as well. Use distinct plan and apply service
  accounts/roles after testing the minimum provider read and write operations. Grant state-bucket
  access at bucket scope and deployment access only for the actual selected root's resource types.
  Explicitly review IAM policy mutation and `serviceusage.services.enable` rights. No project-wide
  Editor/Owner role or long-lived credential in GitHub.
- Bootstrap the bucket/WIF/identities with a separately reviewed, idempotent procedure from the
  authorized GCP account. A one-time human-authenticated bootstrap is expected. Inventory and
  migrate existing local state, if any, before the first CI plan; verify a locked no-drift plan
  against the migrated state. Keep bootstrap state separate from the target runtime state.

**Exit:** reviewed ADR/IAM matrix, protected and versioned state, successful CI identity test and
verified state migration (or explicit evidence that no prior state existed). Bootstrap is a separate
cloud change; this plan does not execute it.

### C. Plan-only workflow before first runtime creation

- Add one reusable GCP delivery workflow boundary only after static verification and identity work.
  Pin Terraform/provider versions and dependency lock, checkout exact trusted SHA, use concurrency
  for the one target, and reject untrusted event/ref/input combinations.
- Run `init`, `validate`, then `plan` with reviewed `project_id`, zone, subnet CIDR and
  `node_count=1`. Fail on a project mismatch. Publish only a sanitized action/type summary and
  versioned evidence; raw `tfplan`, `terraform show -json` and state can contain secrets and must
  have restricted access and short retention. Extend the existing plan summarizer for the actual
  `google_*` resource types before relying on its cost/network/IAM findings.
- Compare the first plan with ADR-005, check actual changes and cost exposure. If credentials or
  quota are insufficient, diagnose the smallest missing permission; do not broaden IAM blindly.

**Exit:** first real plan is reviewed and recorded without creating GKE, disks or a second runtime.

### D. Approved apply and runtime evidence

- Implement apply only after C succeeds. Prefer a reviewed saved plan with a short lifetime and
  exact SHA/input/plan digest checks; if the plan is recomputed, repeat review when resource actions
  differ. Apply from the protected environment with a separate apply identity; never accept PR code
  or arbitrary branch/plan artifacts. Fail closed on delete/replace and out-of-scope resources.
- Before apply, repeat the live billing/credit, quota and duplicate-runtime checks. Approval of
  the concrete first plan and Free Trial cost is a separate operator decision.
- Apply only the M3-R2 namespace, StorageClass and internal OpenSearch resources. Check whether a
  GitHub runner can authenticate/reach the GKE control plane before automating `kubectl`; preserve
  the Cloud Shell runbook as recovery until that boundary is proven. Collect rollout, PVC/disk,
  internal service and minimal Vertex identity evidence without storing sensitive output.
- Use a reviewed `node_count=0` plan on the **same state/root** after evidence collection, verify
  zero nodes, and record any remaining disk/cluster charges and recovery procedure. An apply failure
  requires explicit operator attention to idle cost.

**Exit:** one target GKE/OpenSearch runtime passes the M3-R2 readiness criteria, evidence is
reviewable, and the node pool is returned to zero. Then resume M3-R3; application registry, full
backend deployment, corpus ingestion and agent issue orchestration remain later gated work.

## Immediate single task

Complete **A's read-only inventory**, especially Cloud Shell Terraform state and GitHub
branch/environment settings. Then decide the state and identity design in B. Do not replace the
existing M3-R2 goal with a broad automation milestone or trigger a live apply from this plan.
