# Case A A3 Fixed-Facts Retrieval Probe Readiness

## Status

**A3 probe readiness = COMPLETE. A3 live comparison = NOT RUN.** Production retrieval is
**UNCHANGED**, no retrieval alternative is selected, and Case A remains **OPEN**.

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

Live execution has not occurred. The immediate next single task, after review and merge, is one
separately approved protected A3 live probe run against the existing target. A3 itself is not
complete, A4 is not authorized, and production retrieval remains unchanged.
