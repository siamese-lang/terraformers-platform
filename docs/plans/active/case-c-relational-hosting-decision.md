# Case C Relational Hosting Decision

## Status

**SELECTED — MARIA DB RETAINED ON DEDICATED COMPUTE ENGINE VM; IMPLEMENTATION BLOCKED ON READ-ONLY QUOTA/COST PREFLIGHT**

Decision base:

`f5f92508efdafbfc25e672cfa68976193dbd9024`

This decision closes the relational-hosting branch reopened after the MySQL 8.4 compatibility
failure. It does not create any GCP resource.

## 1. Problem

Case C needs a production-representative backend runtime whose measured bottleneck can be attributed
to the real application path rather than to a benchmark fixture topology.

The initial managed-DB candidate was Cloud SQL for MySQL. Compatibility verification proved that it
is not a drop-in replacement for the retained MariaDB contract: MySQL 8.4 connected successfully,
applied migrations 001 and 002, and rejected V003 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` with
SQL error 1064 while the MariaDB 11.4 baseline remained green.

Changing the production migration history and re-proving the Case B durable-ownership semantics would
turn Case C into a separate database-migration project.

## 2. Alternatives

### A. Intentionally migrate MariaDB to MySQL / Cloud SQL

Advantages:
- managed database operations;
- database lifecycle separated from GKE;
- familiar GCP managed-service topology.

Costs and risks:
- existing migration history is not accepted unchanged;
- driver/dialect/runtime configuration changes are required;
- migrations 004-006 and all Case B claim/lease/fencing/retry/cleanup semantics would need a new
  authoritative MySQL proof;
- database-portability work would materially expand Case C before a capacity baseline exists.

**Decision: DEFER for Case C.**

This remains a valid future cloud-portability project if an independent requirement justifies the
migration.

### B. Run MariaDB as a StatefulSet in the current GKE cluster

Advantages:
- no additional VM product;
- Kubernetes-native lifecycle;
- current MariaDB contract remains unchanged.

Costs and risks:
- MariaDB competes with Backend/OpenSearch on the same target-node capacity;
- DB CPU/memory/storage pressure becomes harder to distinguish from application-cluster saturation;
- PVC/StatefulSet/node lifecycle becomes part of the database operating surface;
- it weakens Case C bottleneck attribution.

**Decision: REJECT for the first Case C baseline.**

### C. Dedicated Compute Engine VM running MariaDB

Advantages:
- retains the already-proven MariaDB persistence contract;
- separates DB CPU/memory/I/O from the GKE worker;
- keeps backend rollout lifecycle separate from database process/data lifecycle;
- supports same-region private networking;
- much smaller scope than an intentional MariaDB→MySQL migration.

Costs and risks:
- database patching, backup, restore and recovery are project responsibilities;
- it is not a managed HA database;
- a single VM remains a failure domain;
- operating evidence must not be described as managed-database HA.

**Decision: SELECT.**

## 3. Selected runtime boundary

The Case C representative runtime will use:

- existing GKE Standard target for Spring Backend and OpenSearch;
- existing Vertex AI path;
- Cloud Storage candidate retained from compatibility PASS;
- Artifact Registry candidate retained from compatibility PASS;
- **MariaDB 11.4 retained as the relational contract**;
- **one dedicated Compute Engine VM in `asia-northeast3`** for the first baseline;
- **containerized MariaDB**, pinned to an exact immutable image digest;
- **dedicated Persistent Disk for `/var/lib/mysql`** so DB data does not depend on the container
  writable layer or VM boot disk;
- database service reachable from GKE through private VPC networking;
- **no public MariaDB listener/exposure**;
- one database instance only for the initial Case C baseline.

This is production-representative for the capacity/safe-delivery case, but it is not a claim of
production HA.

## 4. Containerized MariaDB vs direct host installation

Containerized MariaDB is selected over direct package installation.

Reason:

- the existing authoritative CI baseline already uses MariaDB 11.4 as a containerized engine;
- exact image digest gives a clear database runtime identity;
- VM recreation does not require reproducing OS package-repository state;
- the durable-data authority remains the attached Persistent Disk, not the container layer;
- DB process packaging is separated from GKE orchestration.

The container runtime is an implementation detail. The acceptance boundary is:

- MariaDB process runs in a container;
- exact MariaDB image digest is recorded;
- `/var/lib/mysql` is backed by the dedicated Persistent Disk;
- deleting/recreating the MariaDB container does not delete relational state.

Do not convert this into a Kubernetes database operator or multi-container orchestration project.

## 5. Initial resource candidate

The initial sizing candidate is:

- VM machine type: **`e2-medium`**;
- memory: **4 GiB**;
- shared-core CPU allocation: `e2-medium` class;
- DB data disk: **zonal `pd-balanced`**, initial candidate **20 GiB**;
- boot disk: only what is required by the selected VM image/container runtime;
- no replica VM;
- no automatic failover.

The sizing is deliberately modest but not the smallest possible shape. The goal is to avoid making
an artificially tiny database the guaranteed first bottleneck.

These values are **candidate implementation values, not authorized cloud creation parameters** until
the preflight below passes.

## 6. Quota / Free Trial hard gate

Before any Terraform/Compute Engine implementation is allowed, one read-only preflight must prove the
candidate can coexist with the current GKE target under the actual project limits.

Required project evidence:

1. current Free Trial / billing status is active enough to run the bounded experiment;
2. current credit headroom is reviewed before cost-increasing apply;
3. `asia-northeast3` regional Compute Engine allocation quota is read directly from GCP;
4. project-wide Compute Engine quota is read directly from GCP;
5. candidate VM + active GKE node fit the applicable CPU quota;
6. VM instance quota permits the additional VM;
7. disk quota permits the boot disk and MariaDB data disk alongside current GKE/OpenSearch disks;
8. any required IP/address quota is available;
9. no quota increase is assumed;
10. if any required quota is insufficient, **STOP** before resource creation and reopen only the
    sizing/topology decision.

Canonical read-only commands may include:

```bash
gcloud compute regions describe asia-northeast3 \
  --project=terraformers-platform \
  --format="table(quotas.metric,quotas.limit,quotas.usage)"

gcloud compute project-info describe \
  --project=terraformers-platform
```

The exact billing-credit amount may require the Cloud Billing console/API rather than the Compute
Engine quota commands. If the agent cannot retrieve the remaining credit authoritatively, it must
report that as a human checkpoint rather than inventing a value.

## 7. Free Trial interpretation

The selected DB VM in Seoul is **not assumed to be Always Free**.

Google Cloud's Compute Engine Free Tier e2-micro benefit is limited to designated US regions. The
Case C database remains in Seoul because moving it to a US region solely for free-tier eligibility
would contaminate the latency/capacity experiment with cross-region network effects.

Therefore Compute Engine VM, MariaDB data disk, Seoul Cloud Storage, active GKE worker and other
non-free resources are treated as Free Trial credit consumers.

## 8. Network/security boundary

For the initial runtime:

- backend-to-MariaDB traffic uses the existing GCP VPC/private address space;
- MariaDB must not accept unrestricted Internet ingress;
- firewall scope must be limited to the actual backend/GKE source boundary;
- DB credentials remain secret material and must not be committed;
- no long-lived GCP credential is introduced for the DB VM;
- outbound bootstrap/image-pull method must be explicitly implemented without making public database
  exposure necessary.

Whether the VM itself needs an external egress path during bootstrap is an implementation detail; the
hard requirement is **no public MariaDB service exposure**.

## 9. Durability boundary

Before Case C load testing, the DB runtime must prove:

1. clean MariaDB 11.4 startup;
2. Flyway migrations remain authoritative and unchanged;
3. canonical Case B repository smoke remains valid;
4. MariaDB data is located on the dedicated Persistent Disk;
5. container replacement preserves DB state;
6. VM restart preserves DB state;
7. backend pod replacement does not affect DB state;
8. the accepted AnalysisJob/result state remains readable after backend replacement.

VM deletion/recreation with data-disk reattachment may be tested only if explicitly included in the
later implementation Work Package. It is not required merely to approve this decision.

## 10. HA and operations non-goals for first baseline

Do not build before first Case C baseline:

- MariaDB replication;
- leader election;
- automatic failover;
- multi-zone database cluster;
- database operator;
- multi-node database topology;
- production backup platform;
- automatic restore orchestration.

A bounded backup/restore procedure must exist before calling the environment production-like, but
Case C does not need a HA database platform to measure the backend's first saturation point and safe
application rollout.

If the Case C experiment later shows that DB availability itself is the material failure under study,
that becomes a separate decision.

## 11. Resulting production-representative candidate

After this decision, the Case C target shape is:

```text
Internal authenticated load driver
             |
             v
      GKE Spring Backend
        |      |       \
        |      |        +--> Vertex AI
        |      +-----------> OpenSearch on GKE
        |
        +------------------> MariaDB 11.4
        |                     dedicated Compute Engine VM
        |                     dedicated Persistent Disk
        |
        +------------------> Cloud Storage

GitHub Actions
      |
      v
Artifact Registry
      |
      v
digest-pinned GKE Backend Deployment
```

Cloud Storage and Artifact Registry remain candidates with prior compatibility PASS, but their live
implementation/IAM proof remains future work.

## 12. Decision effect

This decision closes the remaining relational-hosting architecture branch for the first Case C
runtime.

Selected:
- MariaDB retained;
- dedicated Compute Engine VM;
- containerized MariaDB;
- dedicated persistent DB disk;
- same-region private application path.

Deferred:
- MariaDB→MySQL migration;
- Cloud SQL;
- MariaDB HA/replication/failover.

Rejected for first baseline:
- MariaDB inside the current GKE cluster.

## 13. Immediate next single task

**Case C GCP quota/cost preflight — ACTIVE.**

See [Case C GCP Quota / Cost Preflight](../../evaluation/case-c-gcp-quota-cost-preflight.md).

It is read-only and reuses the existing GCP plan workflow / plan identity. Merging the active
Work Package automatically triggers the preflight on `main`; no manual Cloud Shell command or
resource creation is required.

Its only outcome is to determine whether the candidate:

- one active GKE `e2-standard-2` target node;
- one MariaDB `e2-medium` VM;
- current OpenSearch disk;
- one MariaDB `pd-balanced` data disk;
- required boot/storage/address footprint

can coexist under the actual `terraformers-platform` quota and remaining Free Trial budget.

No VM, disk, bucket, registry, IAM binding or Kubernetes resource is created in that preflight.

If the preflight passes, only then may a separately approved implementation Work Package assemble the
representative runtime.

## References

- Compute Engine E2 machine types:
  https://docs.cloud.google.com/compute/docs/general-purpose-machines
- Compute Engine allocation quotas:
  https://docs.cloud.google.com/compute/quotas-limits
- Persistent Disk performance:
  https://docs.cloud.google.com/compute/docs/disks/performance
- Google Cloud Free Trial / Free Tier:
  https://docs.cloud.google.com/free/docs/free-cloud-features
