# Changelog

## 2026-10-12 — Portfolio closeout and repository documentation

- Recorded the USER-approved 2026 GCP hosted teardown: 29 Terraform-managed deletions, separately removed 15 GiB OpenSearch disk, bootstrap/state cleanup, diagnostic-reader deletion and `billingEnabled=false` operator readback. Firebase Authentication test users were operator-confirmed removed; Firebase/GCP project metadata remains. [Closure receipt](docs/lifecycle/gcp-2026-final-teardown-receipt.md).
- Reoriented README around actual GCP application/AI/RAG/Backend reliability evidence and explicit semantic quality limits; labeled earlier AWS and early development documents as historical. This documentation work does not re-run deployment or change runtime code.

## 2026-07-14

### Added

- Public repository direction documents.
- Backend and cloud infrastructure modernization scope.
- Public-safe Spring Boot backend baseline.
- Backend Maven and Docker build workflows.
- Runtime config contract and internal readiness surface.
- Flyway baseline schema for backend modernization track.
- Local backend verification script.
- Evidence templates for backend build, runtime check, Terraform validation, image tag consistency, and smoke tests.

### Notes

- The backend baseline is not yet the full original Terraformers backend implementation.
- Private repository history, real secrets, tfstate, tfvars, kubeconfig, and account-specific values are intentionally excluded.
