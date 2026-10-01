# Case C GCP Quota / Cost Preflight

## Status

**PASS — QUOTA / MACHINE / FREE TRIAL BUDGET GATES COMPLETE**

Execution base:

`a5316f399b4ae71d0b319d264be81766dbf87951`

Work Package:

`.agents/work-packages/case-c-gcp-quota-cost-preflight-v1.yml`

## Purpose

Prove before resource creation that the selected Case C candidate can coexist with the existing
Terraformers GCP target under the project's actual allocation quota.

Candidate active shape:

- one `e2-standard-2` GKE node;
- one `e2-medium` MariaDB VM;
- existing 15 GiB OpenSearch PVC;
- one 20 GiB `pd-balanced` MariaDB data disk;
- a 10 GiB balanced-disk budget for the MariaDB VM boot disk;
- no public MariaDB service.

This is a read-only gate. It does not authorize or create the candidate runtime.

## Execution path

The existing `GCP Target Terraform Plan` workflow is reused.

A new `case-c-preflight` mode uses the existing `terraformers-plan` GitHub OIDC/WIF identity and
performs only read operations.

The workflow is also triggered automatically when this Work Package file is merged to `main`.
That push trigger is scoped only to:

`.agents/work-packages/case-c-gcp-quota-cost-preflight-v1.yml`

No new GitHub Actions workflow is created.

## Read-only checks

The preflight reads:

1. project-wide Compute Engine quota;
2. `asia-northeast3` regional quota;
3. current `terraformers-target` GKE node count;
4. `e2-standard-2` metadata in `asia-northeast3-a`;
5. `e2-medium` metadata in `asia-northeast3-a`;
6. project billing-link status when the existing read identity is permitted to query it.

The workflow still checks that the plan identity has no target mutation permissions before the
preflight runs.

## Quota calculation

The intended before-state is:

`1 × e2-standard-2 GKE node + 1 × e2-medium MariaDB VM`

The preflight adjusts required **additional** quota from the current state.

If the target GKE node count is zero:

- additional E2 vCPU quota budget: 4 visible vCPUs;
- additional instance count: 2;
- additional `pd-standard`: 30 GiB for the GKE node boot disk;
- additional `pd-balanced` / SSD-backed quota budget: 30 GiB for DB boot + data disks;
- additional in-use address budget: 1 for the GKE node.

If the target GKE node is already active:

- the existing GKE node quota is already reflected in current usage;
- only the MariaDB VM and DB-disk additions remain incremental.

The exact current limit/usage/available values are always read from GCP; historical quota values are
not reused.

## Disk quota interpretation

The existing GKE node boot disk is `pd-standard`, so it consumes the regional
`DISKS_TOTAL_GB` quota when that node exists.

The selected MariaDB data disk is `pd-balanced`. Google Cloud documents that balanced Persistent
Disk is counted against the regional SSD-backed Persistent Disk quota, exposed as
`SSD_TOTAL_GB`.

The preflight uses a 30 GiB balanced-disk envelope:

- 10 GiB VM boot-disk budget;
- 20 GiB MariaDB data disk.

The actual VM boot-disk implementation remains a later bounded implementation detail, but it must
fit this preflighted envelope or trigger a new review.

## Cost / Free Trial boundary

The preflight may verify that project billing is linked/enabled if the existing project-scoped plan
identity can query that field.

It **cannot invent remaining promotional-credit balance**.

If remaining Free Trial credit is not available to the existing read-only identity, the workflow
records:

`HUMAN_REQUIRED`

for the final budget confirmation.

This does not authorize expanding the plan identity into billing-account administration.

## Acceptance

The quota/machine portion is PASS only if:

- the canonical target cluster exists;
- current target node count is exactly 0 or 1;
- both selected machine types are advertised in the selected zone;
- all required quota metrics are present;
- current available quota can accommodate the candidate increment;
- billing is not explicitly disabled;
- no GCP mutation occurs.

A missing required quota metric or insufficient quota is a hard FAIL, not a reason to request quota
increase automatically.

## Automatic continuation

After this PR merges:

1. the push to `main` automatically starts the existing GCP plan workflow in
   `case-c-preflight` mode;
2. the workflow publishes current quota and PASS/FAIL evidence in the GitHub Actions job summary;
3. the Work Package remains open until that run is reviewed;
4. no VM/disk/GCS/Artifact Registry implementation starts automatically.

If quota/machine checks PASS but promotional-credit balance remains unavailable, only the budget
confirmation remains a human checkpoint.


## Executed result

Authoritative workflow run:

`36823850490`

Merge/head SHA:

`576d343e207aa98486e386a66782890ebe7dd3bd`

Observed result:

- GitHub OIDC exchange / service-account impersonation: **PASS**;
- plan-identity mutation-permission denial check: **PASS**;
- Case C quota/machine preflight: **PASS**;
- Terraform setup/init/plan/apply steps: **SKIPPED**;
- GCP mutation: **none**;
- current target GKE nodes: **1**;
- additional E2 quota required for the DB VM: **2 vCPU**;
- additional instances required: **1**;
- additional `pd-standard` required: **0 GiB**;
- additional balanced/SSD-backed disk envelope: **30 GiB**;
- additional in-use addresses required: **0**.

Observed quota:

| Scope | Metric | Limit | Usage | Available | Required additional | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| global | `CPUS_ALL_REGIONS` | 12 | 2 | 10 | 2 | PASS |
| region | `CPUS` | 32 | 2 | 30 | 2 | PASS |
| region | `E2_CPUS` | 8 | 0 | 8 | 2 | PASS |
| region | `INSTANCES` | 8 | 1 | 7 | 1 | PASS |
| region | `DISKS_TOTAL_GB` | 2048 | 45 | 2003 | 0 | PASS |
| region | `SSD_TOTAL_GB` | 250 | 0 | 250 | 30 | PASS |
| region | `IN_USE_ADDRESSES` | 4 | 1 | 3 | 0 | PASS |

Therefore the selected active shape is **not blocked by current Compute Engine quota**.

The workflow could not authoritatively query project billing-link state with the existing
project-scoped plan identity:

- billing query: `unavailable`;
- billing enabled: `unknown`;
- remaining Free Trial promotional-credit balance: not exposed to the plan identity.

This does **not** justify granting billing-account administration to the plan identity. The user
confirmed on 2026-10-01 that sufficient Free Trial promotional credit remains for the bounded
Case C implementation and evidence session. Therefore the budget gate is **PASS**.

## External references

- Compute Engine quota and limits:
  https://docs.cloud.google.com/compute/quotas-limits
- Compute Engine allocation quota / disk quota:
  https://docs.cloud.google.com/compute/resource-usage
- E2 machine types:
  https://docs.cloud.google.com/compute/docs/general-purpose-machines
- Persistent Disk creation and minimum sizes:
  https://docs.cloud.google.com/compute/docs/disks/add-persistent-disk

## Final acceptance

The preflight is **COMPLETE / PASS**.

- quota and machine availability: PASS;
- read-only identity boundary: PASS;
- no cloud mutation: PASS;
- Free Trial budget checkpoint: PASS by explicit user confirmation;
- runtime implementation: still separately gated.

The next candidate Work Package may implement the production-representative Case C runtime, but it
must not start until explicitly approved.
