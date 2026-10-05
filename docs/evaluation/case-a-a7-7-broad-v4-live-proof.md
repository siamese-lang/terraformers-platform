# Case A A7-7 — broad v4 end-to-end live proof

Status: **REPOSITORY IMPLEMENTED — AWAITING REVIEW/CI — NO LIVE ACTION YET**

## Required claim

Repository-only evidence is not sufficient for the final Case A extension.

The improved Terraformers must demonstrate in the existing representative GCP runtime that:

1. the broad `terraformers-reference-v4` corpus is actually built from the pinned authority;
2. all 5,395 documents are embedded and served from the live OpenSearch v4 index;
3. the real authenticated backend path uses that v4 retrieval configuration;
4. a positive AnalysisJob reaches terminal success;
5. `evidence-quality-v1` is durably persisted and returned by the job API;
6. a real negative control produces no Terraform result and persists the expected NOT_APPLICABLE quality;
7. the recreated runtime is torn down after evidence collection.

## Why this phase exists

A7-0 proved that the historical v3 corpus covers only 30 provider resources and that a broad v4
candidate can cover all 1,514 AWS Provider 5.100.0 resources with official resource documentation.
A7-1 through A7-5 added deterministic quality semantics, false-green calibration, provider failure
classification, durable quality persistence, and safe Terraform diagnostics.

Those repository results are necessary but do not prove the production serving path was switched
from v3 to v4.

A7-7 closes that exact gap. It is not a new capacity, HA, rollout, or observability project.

## Existing constraints discovered before activation

The current live ingestion path is intentionally historical and v3-specific:

- `.github/workflows/gcp-target-corpus-ingestion.yml` requires
  `INGEST_M3_R3B_REFERENCE_V3`;
- it verifies `terraformers-reference-v3`, 128 documents, and index
  `terraformers-reference-v3`;
- `scripts/rag/ingest-gcp-target-corpus.py` hard-codes the same v3/128 runtime contract;
- the GCP backend ConfigMap currently sets `INDEX_NAME` and `CORPUS_VERSION` to v3.

Therefore live v4 proof requires a bounded repository preparation change before any cloud action.

## Selected implementation shape

Do not add a new workflow.

Generalize the existing protected ingestion path from v3-only to manifest-driven v3+v4 while
retaining the historical v3 contract. Add one exact A7-7 v4 operation to the existing backend
live-validation workflow. Change only the GCP target runtime ConfigMap to v4.

The broad corpus is built reproducibly for live ingestion instead of committing the generated
14.8 MB v4 JSONL by default:

- provider source commit:
  `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`;
- provider schema: exact AWS Provider 5.100.0 schema from the A7-7 backend image/runtime;
- compiler: `scripts/rag/build-corpus-v4.py --all-documented-resources`;
- project-decision provenance: exact final A7-7 main source SHA.

Before any embedding request the built corpus must reproduce the A7-0 broad contract:

| Measurement | Required |
| --- | ---: |
| Provider schema resources | 1,526 |
| Official-documented schema resources | 1,514 |
| Selected v4 resources | 1,514 |
| Selected resources with official evidence | 1,514 |
| Official evidence extraction gaps | 0 |
| Provider chunks | 5,387 |
| Project decisions | 8 |
| Total documents | 5,395 |

Any mismatch stops live execution.

## Repository preparation implemented

Frozen execution base:

`13e398599ce69e087e9098b1413cad37c41edfd1`

The bounded repository path now contains:

- manifest-driven GCP ingestion support for the exact immutable v3 and broad v4 contracts;
- preserved historical v3 document-count/checksum behavior and new v4 fail-closed tests;
- deterministic broad-v4 live build from provider commit
  `f7a3b98da589ab1d52756b0dcee0dbf2de83d635` and the AWS Provider 5.100.0 schema copied from the
  exact deployed backend image/runtime;
- exact pre-embedding gates for 1,526 schema resources, 1,514 documented/selected resources,
  5,387 provider chunks, 8 project decisions, 5,395 documents, and zero extraction gaps;
- the existing protected GCP corpus-ingestion workflow extended with
  `INGEST_A7_7_REFERENCE_V4`, not a new workflow;
- GCP target backend runtime identity changed from v3 to
  `INDEX_NAME/CORPUS_VERSION=terraformers-reference-v4`;
- one `a7-7-broad-v4-live-proof` operation in the existing authenticated backend validation
  workflow;
- positive assertions for correlated v4 retrieval, generated Terraform read-back, and persisted
  `evidence-quality-v1`;
- negative assertions for `rejected_input`, no Terraform result, and persisted
  PASS / NOT_APPLICABLE quality semantics;
- bounded evidence artifacts containing identities, statuses, quality enums/counts, checksum, and
  reference count only — not raw prompt/image/reference/HCL/embedding content.

Live execution has not started. Repository CI and independent review are the next gate.

After PR #221 merged and the target runtime remained torn down, the existing full-shape quota/cost
preflight was found to reject the correct empty-cluster baseline because
`gcloud container clusters list` returns an empty value when `terraformers-target` is absent.
A bounded corrective change normalizes that exact absence to `currentNodeCount=0`; any nonempty
value other than 0 or 1 still fails closed. The separate protected foundation preflight already
handles an empty canonical Terraform state and requires the reviewed 12-create foundation plan.

## Positive control

Use frozen case `arch-vpc-three-tier` and its exact fixture SHA.

The real backend must:

- accept the authenticated upload;
- persist the source bytes to GCS;
- complete an AnalysisJob as `SUCCEEDED`;
- serve non-empty generated Terraform;
- log a correlated v4 retrieval success with positive reference count;
- return a non-null `quality` object from `GET /api/analysis/jobs/{id}`;
- return `contractVersion=evidence-quality-v1`, `technicalStatus=PASS`,
  `knowledgeStatus=COMPLETE`, and
  `runtimeQualityBoundary=CONDITIONAL_ON_EXTRACTED_FACTS`.

Do not force `qualityStatus=EVIDENCE_BACKED`. Under the current runtime contract project-decision
applicability may remain UNKNOWN, and the persisted quality result must report its actual bounded
state rather than being rewritten for the live proof.

## Negative control

Use frozen case `non-architecture-deployment-dashboard` and its exact fixture SHA.

The real backend must retain the existing INPUT_REJECTED contract:

- terminal job failure rather than successful Terraform generation;
- no result file/object;
- no successful project Terraform draft;
- persisted quality:
  - technical PASS;
  - knowledge NOT_APPLICABLE;
  - quality NOT_APPLICABLE;
  - project decision NOT_APPLICABLE;
  - empty reasons.

## Evidence retention

Do not rerun a correctness attempt merely because it is inconvenient.

If provider/retrieval/Terraform correctness fails naturally, preserve the exact source/image/corpus
identity and terminal evidence. Infrastructure ingestion may resume idempotently only for the same
v4 checksum; all workflow attempts remain part of the evidence history.

## Teardown boundary

After evidence collection, run the existing reviewed GCP target teardown.

The acceptance target is return to the pre-A7-7 baseline:

- no GKE target runtime;
- no MariaDB VM/data PD;
- no OpenSearch PVC disk;
- no runtime object bucket;
- no Artifact Registry runtime repository/images;
- no runtime Secret Manager secrets;
- no target VPC.

The pre-existing bootstrap state bucket and WIF/service-account identities may remain only at the
same baseline that existed before A7-7; A7-7 must not add a new residual bootstrap surface.

## Before live execution

A7-7 repository preparation must first merge and pass CI.

Then perform one read-only preflight and present one explicit live checkpoint containing the exact
main SHA, reviewed runtime plan/shape, image source strategy, v4 embedding volume/document count,
and teardown boundary.

No live/cost mutation is authorized before that checkpoint.
