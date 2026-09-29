# Case A A3 Fixed-Facts Retrieval Probe Readiness

## Status

**A3 probe readiness = COMPLETE. The first protected live attempt aborted before comparison, and
the A3 live comparison remains INCOMPLETE.** Evaluation-only embedding pacing is implemented;
production retrieval is **UNCHANGED**, no retrieval alternative is selected, Case A remains
**OPEN**, and A4 is not authorized.

## First protected live attempt and pacing correction

Protected workflow run `36592590562` used source commit
`b8c549bd1f80e90c4649f65fa70719ffab93c56f`. GKE/OpenSearch substrate checks, ephemeral evaluation
pod startup, and bundle transfer succeeded. The probe then aborted in
`VertexEmbeddingProvider.embed` via `CaseAAlternativeProbeRunner.checkedEmbedding` with Vertex
embedding `429 RESOURCE_EXHAUSTED` before the retrieval-strategy comparison completed. No
machine-readable A3 comparison artifact was produced, so the retrieval alternative result is
**NOT OBSERVED**.

The subsequently queried project quota for project `terraformers-platform` (project number
`21647422237`) showed `gemini-embedding = 5 requests/minute`, refreshed per minute, with quota
increase eligibility `NOT_ENOUGH_USAGE_HISTORY`. The unchanged six-snapshot experiment makes two
embedding calls per snapshot, or exactly 12 unpaced requests. This is evidence of an evaluation
harness quota/cadence mismatch, not retrieval-strategy, OpenSearch, GKE, or production retrieval
failure. The separate `gemini-embedding-2` quota is outside the frozen experiment.

The evaluation-only launcher now spaces embedding request starts by at least 13 seconds across the
entire probe, without delaying the first request, changing/caching vectors, or adding 429 retry or
backoff. The frozen `gemini-embedding-001` model, 1024 dimensions, corpus, provider version,
strategies, K values, fixture, and expectations remain unchanged. After the failed attempt, the
target was returned to Terraform `node_count=0`.

## Evidence and fixed inputs

A2's three unchanged canonical runs (`36574906125`, `36576165161`, `36577747809`) passed fact
extraction and retrieval but reproduced the VPC grounding gap `3 / 3`. Every VPC query already
contained `aws_vpc`, `aws_subnet`, `aws_lb`, `aws_security_group`, and `aws_db_instance` filters.
Consequently, CloudFormation vocabulary causing empty Terraform filters is insufficient to explain
A2, and vocabulary normalization is not a primary A3 candidate.

The repository-owned `case-a-a3-fixed-facts-v1` fixture freezes exactly six snapshots: the three
VPC fact extractions above plus CloudFront/private-ALB, private-AOSS, and S3/metadata-split controls
from A2 run `36576165161`. Each snapshot maps to the immutable canonical dataset's retrieval
expectations for scoring; expectations are not retrieval or reranking inputs.

## Comparison boundary

One protected run compares the current filtered semantic K8 control, filtered K24 diagnostic,
unfiltered K24 diagnostic, priority K24-to-K8 rerank, query-resource-coverage K24-to-K8 rerank, and
relationship-first filtered K24-to-K8 query. The current query is embedded once and its exact vector
is reused by the first five strategies; the relationship-first query is embedded once separately.
Raw embeddings are not persisted, only deterministic SHA-256 identities.

The report records ordered candidates and selections, exact project-decision/resource coverage and
ranks, OpenSearch latency, and candidate/selected content character counts. It summarizes VPC
completeness, control regression, coverage, and minimum/median/maximum values. It does not run fact
extraction, generation, or Terraform validation and does not declare a winner.

## Decision criteria and next boundary

A later decision requires all three VPC snapshots to have complete retrieval contracts without
coverage loss on the three controls, no expectation-specific retrieval logic, and comparison of
latency, context characters, and complexity. Equivalent quality prefers selected K8 output and the
simpler approach. A K24 diagnostic alone cannot authorize production K24, and a single VPC pass is
insufficient.

The first live attempt did not complete the comparison. The immediate next single task, after review
and merge, is one separately approved protected A3 live probe rerun against the existing single
target, followed by artifact review and idle closure. A3 itself is not complete, A4 is not
authorized, and production retrieval remains unchanged.
