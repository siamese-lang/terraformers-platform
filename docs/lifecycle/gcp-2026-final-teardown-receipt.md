# Terraformers 2026 — final GCP hosted teardown closure receipt

> **Lifecycle disposition: `GCP_RUNTIME_TORN_DOWN` / `BOOTSTRAP_CLEANED` / `BILLING_UNLINKED`.** Evidence collected 2026-10-11 UTC (2026-10-12 KST). Firebase Authentication users were deleted in the Console as **operator-reported**, not independently queried through the Admin API. The Firebase/GCP project itself, its configuration, and the domain registration remain. This receipt is a retrospective documentation update, **not** authorization to perform additional deletion.
>
> **Source of truth:** this newer closure receipt supersedes older documents' *then-current* `GCP_TEARDOWN_NOT_EXECUTED`, `ACTIVE_RETAINED` and preflight-only wording. Historical T1/T2 observations, Product Trust negative evidence, source identities and acceptance criteria are preserved, not retroactively rewritten.

## 1. Scope, provenance and preserved evidence

- Project: `terraformers-platform` (number `21647422237`); repository: [siamese-lang/terraformers-platform](https://github.com/siamese-lang/terraformers-platform).
- Exact independently checked GitHub teardown source: [main `91a8181295a4e74c1ac3881a2b2f45638d536cb0`](https://github.com/siamese-lang/terraformers-platform/commit/91a8181295a4e74c1ac3881a2b2f45638d536cb0); [workflow run 38177854068](https://github.com/siamese-lang/terraformers-platform/actions/runs/38177854068), with `destroy-plan` job [114583945526](https://github.com/siamese-lang/terraformers-platform/actions/runs/38177854068/job/114583945526) and `destroy` job [114584128898](https://github.com/siamese-lang/terraformers-platform/actions/runs/38177854068/job/114584128898).
- The GitHub run, its logs and workflow code were independently read. The ingress/certificate/IP, orphan-disk, bootstrap, final resource inventory, billing and Firebase-user cleanup steps below derive from operator-supplied Cloud Shell / Firebase Console observations; these are **not** independently authenticated cloud-API readbacks by the reviewer.
- The operator confirmed Markdown/Word portfolio material and a targeted core evidence archive were saved outside GCP before destructive cleanup, and accepted deletion of the remaining MariaDB/GCS operational data and omission of additional source-image hash checks. Do not imply that all original private data were exported or that hashes were verified at teardown.
- This file contains no access tokens, private UIDs, raw source images, credentials, Terraform state, or user identifiers.

## 2. Executed sequence and result

| Stage (2026-10-11 UTC) | Evidence and boundary | Disposition |
| --- | --- | --- |
| Public HTTPS Ingress (about 21:27) | Operator verified and deleted only namespace `terraformers-target` Ingress `terraformers-hosted`. Subsequent query reported Ingress, global forwarding rules, HTTP/HTTPS proxies, URL maps, backend services and health checks absent; reserved IP remained intentionally. | `STAGE1_INGRESS_LB_CLEANUP=PASS` |
| Hosted certificate / address (about 21:37) | Operator removed `otogi.cloud` A-record in its DNS administration interface; confirmed `terraformers-frontend-hosted` ManagedCertificate and Google certificate `mcrt-32f7b8db-23d8-49db-88f0-7499adb7a6fc` absent, then deleted reserved `terraformers-hosted-ip` (`34.160.76.5`). Authoritative DNS propagation was **not** independently demonstrated; recursive DNS initially returned the old address. | `STAGE2_HOSTED_CLEANUP=PASS` (cloud resources) |
| Terraform destroy plan / apply (21:48–21:59) | Before dispatch, isolated Cloud Shell worktree at the approved SHA showed 29 managed addresses and a delete-only plan. [Run 38177854068](https://github.com/siamese-lang/terraformers-platform/actions/runs/38177854068) confirmed `Plan: 0 to add, 0 to change, 29 to destroy`, `gcp_target_plan_gate=passed`, and `Apply complete! Resources: 0 added, 0 changed, 29 destroyed`. State-empty and named-runtime residual checks succeeded. | **29 Terraform-managed instances deleted** |
| Unmanaged OpenSearch disk recovery (about 22:07–22:11) | Independent operator inventory **after** the successful workflow still showed zonal 15 GiB `pd-standard` disk `pvc-42c0a5d8-8d0c-4a7f-95ba-a4fb5722a439`. Guarded deletion confirmed the exact project, zone, name, size, type, `READY` status, no attached users, no clusters/VMs. A final `gcloud compute disks list` returned zero. This disk matched the previously inventoried OpenSearch PVC-associated disk by name/size; its final Kubernetes PV handle could no longer be inspected because GKE was gone. | `STAGE3_RESIDUAL_DISK_CLEANUP=PASS` / `GCP_DISKS_REMAINING=0` |
| Bootstrap (about 22:18) | Approved exact-scope script removed all state-bucket object versions and `terraformers-platform-tfstate-21647422237`, the three `terraformers-plan`/`terraformers-apply`/`terraformers-image-publish` service accounts and their project IAM bindings, and GitHub WIF Pool `terraformers-github` (provider included). Independent list readback reported bucket and accounts absent, Pool **not active** (not a claim of permanent deletion beyond provider soft-delete lifecycle). | `STAGE4_BOOTSTRAP_CLEANUP=PASS` |
| Final inventory and billing (22:19–22:27) | Cloud Shell lists showed zero GKE clusters, VMs, disks, reserved IPs and GCS buckets. Operator removed `codex-logging-reader` and its `roles/logging.viewer` project binding, unlinked billing and checked `billingEnabled=false`. | `FINAL_BILLING_CLEANUP=PASS` |
| Firebase Authentication (about 22:31) | After an attempted script lost its Cloud Shell session without a trustworthy completion result, the operator inspected Firebase Console, saw **two** Authentication users, and deleted both manually. The Console deletion was reported by the operator; no subsequent independent Firebase Admin API enumeration is claimed. | **Two user deletions operator-confirmed** |

## 3. Important failure found despite green CI

The [destroy job](https://github.com/siamese-lang/terraformers-platform/actions/runs/38177854068/job/114584128898) reported success even though its logs warned that `gke-gcloud-auth-plugin` was missing/not executable. The workflow used `if kubectl get namespace terraformers-target ...` without distinguishing an absent namespace from a failed Kubernetes authentication/query, so the PVC discovery/cleanup branch **could be skipped without failing the job**. Its later residual-verification commands checked GKE, MariaDB VM/data disk, runtime bucket, registry, secrets and VPC, **but not the named OpenSearch PD**; nevertheless its step summary claimed `OpenSearch PVC disk: absent`.

The independently observed 15 GiB disk after the run demonstrates that the full-runtime residue claim was false. The exact failed `kubectl` call's return code was not logged, so the plugin warning plus branch semantics is a supported failure mechanism, **not a proven complete low-level trace**. The guarded manual deletion closed this specific cost-bearing residual. **Do not represent CI green alone as proof of full cleanup, or silently repair/merge the old workflow after decommission.**

## 4. Final resource boundary and retained configuration

| Surface | Final known disposition / evidence limit |
| --- | --- |
| GKE, Compute Engine instances, Compute disks including OpenSearch, reserved IPs | Zero in operator's final project listing (disks re-verified after orphan cleanup). |
| GCE Ingress LB/SSL/NEG | Removed in separately scoped checks before Terraform teardown. |
| Terraform-managed VPC and 29 state instances | 29 destroy actions applied; managed state empty **before** the state bucket was removed. |
| Runtime GCS and Artifact Registry `terraformers-backend` | Deleted by workflow; runtime bucket absent in final list. |
| MariaDB Secret Manager entries | Two named entries absent in successful workflow checks; do not generalize to all secrets in the project. |
| Terraform state bucket, GitHub WIF and plan/apply/image service accounts | State bucket/accounts absent, GitHub WIF not active in operator readback. A soft-deleted WIF record might remain during retention. |
| Diagnostic `codex-logging-reader` | Removed with its project `roles/logging.viewer` binding. |
| Google default Compute SA, `firebase-adminsdk-fbsvc`, `terraformers-platform.svc.id.goog` GKE-managed identity pool | Deliberately retained configuration in last readback. The GKE identity pool was `ACTIVE`; do not remove it without an owner/dependency review. |
| Firebase project / Authentication configuration | Retained; Authentication users A and B **deleted by operator in Console**. Deleting a Firebase project's parent GCP project was **not** approved or executed. |
| GCP billing | Operator readback `billingEnabled=false`; already accrued charges/credits and actual billing invoices were **not** independently audited. |
| `otogi.cloud` | Domain registration retained; DNS A-record removed per operator. No guarantee about registrar renewal costs or worldwide DNS-cache expiration. |
| GitHub source/docs, immutable past CI, locally saved portfolio/evidence | Retained; previous run logs may be subject to GitHub retention, and local evidence inventory/hash completeness is not independently established. |

**Cost boundary:** the inventoried GCP compute, disk, network-address and storage-bucket resources were removed, and project billing was unlinked. This is **not** a certified zero-charge invoice, a complete cross-product quota inventory, or a claim that Firebase/project configuration no longer exists. Enabled APIs or retained service accounts are not by themselves evidence of an active paid runtime.

## 5. Product Trust and lifecycle boundaries

- **Lifecycle only:** `GCP_RUNTIME_TORN_DOWN` denotes verified destruction of the scoped hosted runtime and bootstrap, **not** a Product Trust acceptance promotion.
- Original official PT-8A A–E failing/NOT_RUN record and **original full PASS not established** remain untouched. Qualified A/B/N1/N2/N3 observations: five SUCCEEDED and five Terraform CLI PASS, `qualityStatus=UNKNOWN`, not semantic five-of-five PASS. Original PT-8B admission gate `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED` remains **unmet**, not waived.
- T1's narrowly observed authenticated 403/private-owner and billable-denial behavior, and T2's shutdown-induced FAILED Job, remain as originally recorded in [final live evidence](../portfolio/terraformers-gcp-final-live-evidence-20261011.md). Teardown does not convert either into universal reliability/security claims.
- **No additional cloud, model, user deletion, IAM, billing or DNS action is authorized by this PR.** Only this closure receipt and pointers from older current-status sections are changed. Repository runtime code, workflow logic, acceptance gates and original negative artifacts are unchanged.
