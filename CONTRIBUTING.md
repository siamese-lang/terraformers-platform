# Contributing and Evidence Guardrails

This repository preserves the **completed Terraformers Modernization portfolio** (2024 five-person team project reused in a subsequent individual modernization). The representative GCP runtime was decommissioned in October 2026; this is not an actively deployed service or an invitation to resume old milestone queues.

Before contributing:

- Read the [current README](README.md), [repository contract](AGENTS.md), [final engineering evidence](docs/portfolio/terraformers-modernization-final-engineering-account-20261011.md), and [GCP teardown receipt](docs/lifecycle/gcp-2026-final-teardown-receipt.md).
- Attribute the 2024 team implementation and subsequent modernization accurately. Do not describe original team work as sole personal work.
- Never commit access tokens, IAM keys, Terraform state/`tfvars`, kubeconfig, Firebase user IDs, private input images, account data, database credentials, or unredacted diagnostic logs.
- Keep original negative test results and evaluation limitations. Terraform CLI PASS does **not** imply semantic architecture correctness; Product Trust full acceptance was not established.
- Treat former AWS/GCP deploy, apply, evaluation and destroy workflows as **historical code**. A documentation or CI maintenance change does not authorize live cloud deployments, model calls, IAM mutation, billing changes, or user deletion.
- Prefer narrowly scoped changes with reviewable source/evidence references. Operational or security changes require the existing repository decision/approval contract.

Suggested contribution scopes: corrections to portfolio evidence/citations, historical-document labels, local-safe backend or frontend regression fixes explicitly approved for a new goal. Do not silently restart the original milestone plan.
