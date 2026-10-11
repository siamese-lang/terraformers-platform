# GCP 2026 hosted runtime — FINAL teardown delta, preservation and preflight

> **Status: DOCUMENTATION / NOT A DESTRUCTIVE AUTHORIZATION.** User selected “final evidence preservation, then GCP teardown” on 2026-10-11. No live deletion, Terraform destroy, scale-to-zero, secret export or Firebase-account deletion has been executed by this documentation task. **A separately reviewed current resource inventory, deletion-only plan and explicit USER destructive checkpoint are required.**

## 1. Why the previous teardown procedure is not executable as-is

The existing [GCP target final teardown runbook](../runbooks/gcp-target-final-teardown.md) was written for an earlier private runtime and an **original 29-delete Terraform contract**. Later work added:

- Firebase Authentication real user accounts and server-side billable UID gating.
- Public `otogi.cloud` with GCE Ingress, managed HTTPS certificate, global static IP / load balancing and NEG/BackendConfig.
- An additional immutable frontend image and frontend GKE Deployment/Service.
- GCP service state that may have been created **outside** the original Terraform-managed target.
- Additional user Project, source/result objects, MariaDB rows, Cloud Logging and owner-scoped diagnostic evidence since earlier closures.

An earlier 29-delete expectation is **not a current inventory**. Do not run the earlier `DESTROY_REVIEWED_GCP_TARGET_RUNTIME_29` workflow, `cleanup_gcp_target_bootstrap.sh apply`, or broad gcloud delete commands against the new hosted runtime without re-evaluating actual state and unmanaged hosted resources. Even a successful `kubectl delete namespace` may leave project-level forwarding rules, reserved addresses, IAM, buckets, Firebase setup or DNS/certificate controller residues. Conversely, deleting shared project-level configuration may destroy resources outside this scope.

## 2. Preservation gate — complete before destruction

1. **Merge and verify documentation:** [Final 2026 live evidence](../portfolio/terraformers-gcp-final-live-evidence-20261011.md) and [Modernization final engineering account](../portfolio/terraformers-modernization-final-engineering-account-20261011.md), keeping the old original PT-8A failures and AWS-era closures unmodified. After the PR is merged, verify that the GitHub paths and source/Job IDs resolve on `main`.
2. **Produce an independent source-bank-use project summary** (plain Markdown/Word/PDF as requested), covering scenario, original team vs personal follow-on, evidence, implementation, validation, precise metrics, caveats and interview-ready narratives. The public repository may contain a sanitized summary; private screenshots/raw diagnostic JSON should be retained under the operator's own control if sensitive.
3. **Confirm the operator retains any needed raw evidence outside this disposable GCP project:** A/B/N1/N2/N3 Job/diagnostic reviews, original image hashes and (if needed) source images, T1 two-user 403/paid-denial status log, T2 full Cloud Logging readback, relevant screenshots, immutable publication/rollout workflow URLs, specific source and image digest. Repository summaries are **not a replacement** for private original data.
4. **Evaluate whether MariaDB/GCS bytes themselves must be preserved.** If no export is desired, explicitly accept loss of original user project/source/result/diagnostic data on teardown. If preservation is required, choose a secure external destination, restrict access, and record file inventory/checksums without placing Firebase tokens or private objects in GitHub. Do not assume Cloud Logging is permanent; retention is product/configuration dependent.
5. **Account for cost and continuing personal ownership:** DNS domain registration/renewal (registrar), any Firebase tenant/users/configuration, project billing, external static IP and load balancer, GKE/VM/disk, buckets, Artifact Registry, state bucket, service accounts/WIF and secrets. Decide per item whether it should be destroyed, transferred, or retained intentionally, and who controls it.

If any of these are missing, stop and preserve data before destruction.

## 3. Mandatory read-only inventory from the actual GCP project

All operations in this section are **GET/LIST/PLAN with no apply** and must record timestamp/account/project/region. Do not print secret values or JWTs.

| Surface | Exact facts required before planning destruction | Known risk |
|---|---|---|
| Terraform remote state | State bucket existence, current state list/managed count, resource addresses, `terraform plan -destroy` against the exact current configuration (if safely executable) | Old fixed 29-count contract is stale until proven |
| GKE cluster / namespace | Cluster/node pools; Backend/frontend/JWKS/OpenSearch Pods, Deployments, Services, Ingress, ManagedCertificate, BackendConfig, PVC/PV and disk handle | Deleting namespace can take down public site and trigger controller deletion |
| Public L7 load balancing | Global static IP reservations, forwarding rules, target proxies, URL maps, backend services, health checks, NEGs and managed SSL state; identify **terraformers-owned vs shared** | Load balancer/static IP costs may remain after nodes idle |
| Compute/storage | MariaDB VM, boot/data PD; OpenSearch PD; GCS runtime objects/bucket; Artifact Registry images; Secret Manager metadata; state bootstrap bucket | Data deletion irreversible; don't enumerate/publish secret payloads |
| Identity/configuration | Firebase project, registered users (count/ownership only), authorized domain, external DNS provider, WIF pools/providers, GCP service accounts and IAM bindings | Firebase users/domain and registrar are not automatically covered by Terraform destroy |
| Billing | Linked billing and actual charges/credits where authorized; verify after cleanup | Disabled Pods do not mean zero billable resources |

Read-only inventory must explicitly identify all **post-original hosted resources** and whether each is Terraform-managed, Kubernetes-controller-created, manually provisioned or external. A generic `gcloud projects list` or frontend HTTP check is insufficient.

## 4. Proposed destroy sequence — planning only

**Do not execute without fresh inventory and separate approval.**

1. Freeze the approved scope and decision about data export and domain/Firebase user retention. Confirm `FIREBASE_BILLABLE_UIDS` override is **ABSENT** and disable any remaining paid submission entitlement before changing serving.
2. Plan **hosted public ingress decommission** first: exact `terraformers-hosted` Ingress/ManagedCertificate/FrontendConfig/Service NEG relationships and their controller-managed LB dependencies; identify external static IP and DNS order. Verify no unrelated Ingress/LB is affected. DNS redirect/deletion and Firebase authorized domains require explicit owner decision. HTTPS should be treated as going permanently offline, not a temporary outage.
3. Prepare the exact current Terraform-managed runtime teardown with reviewed `delete`-only addresses and residual resource inventory, allowing for the previous state history but **never assuming** it remains exactly 29 instances.
4. Execute reviewed destroy **only after an explicit user confirmation of names, data loss, cost and irreversible effects**. If a stage fails, stop and inspect residuals, preserve state and bootstrap identities until recovery is complete; do not mass-delete unknown objects or run a second unreviewed plan.
5. After runtime, GCS/PD/registry/Secret and GKE residue absence is verified, separately handle approved bootstrap state bucket and WIF/service-account cleanup. **State and WIF must survive an incomplete destroy.**
6. Inspect leftover LB/IP/GCE disks/buckets/secret versions, Firebase and DNS, project IAM and billing; record intentional survivors with ownership/cost explanation. Retained GitHub repository/history/docs and any private exported evidence are **not** GCP resources.
7. Write a post-teardown closure receipt with actual timestamp, GitHub `main` SHA, resource-by-resource absence, intentionally retained services and remaining costs. Only then update the repository lifecycle state to **GCP_RUNTIME_TORN_DOWN**. Never claim already torn down from a planned deletion.

## 5. Current safe stop state / approved next action

Last T2 operator readback: `FIREBASE_BILLABLE_UIDS` Deployment override **ABSENT**, public `https://otogi.cloud/` HTTP **200**, Backend replaced and Ready; deployment source readback `5dd67b046ed802089b1a55b7bf07343b7a6316e6`. This was **a point-in-time observation**, not a fresh final-destroy inventory.

**Next action after documentation PR independent review/USER merge and source-bank material preservation:** collect the bounded read-only actual runtime/resource inventory above; present an exact resource-bound deletion plan; obtain fresh USER destructive approval. No further Vertex AI model experiment, Pod interruption, database fault injection or code mutation is required.
