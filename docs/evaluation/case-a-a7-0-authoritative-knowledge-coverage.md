# Case A A7-0 — Authoritative Knowledge Coverage

## Status

**IMPLEMENTATION IN PROGRESS — LIVE/COST ACTION NOT REQUIRED**

Execution base:

`dd34d1cec34d31783ca2d672c65311ce82fd2666`

This checkpoint implements the first phase of ADR-008. It does not activate v4 in the serving
runtime and does not modify the historical v3 corpus.

## Problem

The current v3 corpus contains 128 documents but represents only 30 AWS provider resource types.

That is sufficient for the original Case A evaluation fixtures but insufficient as a general
quality oracle. If a runtime request contains a valid AWS resource outside that subset, a missing
retrieval result could mean:

- the corpus does not contain the official knowledge;
- the retriever failed to select available evidence; or
- the provider does not support the resource.

Those are different failure classes and must not be collapsed.

The historical v2 builder encodes provider support in a source-code `RESOURCE_SPECS` dictionary,
so a new resource requires a code edit before it can even be compiled.

## Immutable baseline

The committed `terraformers-reference-v3` remains unchanged.

Measured from current main:

- document count: 128
- provider resource types represented: 30
- Terraformers project decisions: 8
- provider version: 5.100.0
- provider source commit: `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`

The 30 provider resource types are:

`aws_cloudfront_distribution`,
`aws_cloudfront_origin_access_control`,
`aws_cloudwatch_log_group`,
`aws_cloudwatch_metric_alarm`,
`aws_cognito_user_pool`,
`aws_cognito_user_pool_client`,
`aws_db_instance`,
`aws_db_subnet_group`,
`aws_ecr_repository`,
`aws_eks_cluster`,
`aws_eks_node_group`,
`aws_iam_policy`,
`aws_iam_role`,
`aws_iam_role_policy_attachment`,
`aws_internet_gateway`,
`aws_lb`,
`aws_lb_listener`,
`aws_lb_target_group`,
`aws_nat_gateway`,
`aws_opensearchserverless_access_policy`,
`aws_opensearchserverless_collection`,
`aws_route`,
`aws_route_table`,
`aws_s3_bucket`,
`aws_s3_bucket_policy`,
`aws_security_group`,
`aws_sqs_queue`,
`aws_subnet`,
`aws_vpc`,
`aws_vpc_security_group_ingress_rule`.

The pinned provider source tree has 1,514 `website/docs/r/*.html.markdown` files. That source-tree
count is not substituted for the provider-schema resource count. The exact schema universe must be
reported from the real `terraform providers schema -json` output.

## Selected implementation

### Scalable v4 compiler

`scripts/rag/build-corpus-v4.py`:

- accepts arbitrary `aws_*` resource names;
- validates them against the supplied pinned provider schema;
- derives official documentation paths from the resource type;
- can build an explicit bounded resource set or the full documented/schema intersection;
- always produces schema evidence for selected valid provider resources;
- records missing official docs as knowledge coverage gaps;
- preserves source version/commit/path and sanitization/risk metadata;
- carries forward curated Terraformers project decisions from v3;
- writes a machine-readable `coverage-report.json`;
- uses a new `terraformers-reference-v4` identity.

There is no production per-resource `choices=RESOURCE_SPECS` allowlist.

### Coverage reporter

`scripts/rag/report-corpus-coverage.py` independently reports:

- provider-schema resource universe;
- committed corpus provider-resource coverage;
- corpus provider-schema document coverage;
- project-decision count;
- optional official-document/source coverage;
- schema file SHA-256.

This is the required distinction between "knowledge absent" and "retrieval missed available
knowledge."

### v4 contract

`scripts/checks/rag-corpus-contract-verification.py` registers the v4 runtime identity and requires
the coverage report to reconcile with the generated provider documents and selected resource set.

v1/v2/v3 runtime contracts are unchanged.

## Acceptance evidence still required

Before A7-0 can close:

1. CI must pass the v1/v2/v3 regression and the new v4 compiler tests.
2. A resource absent from committed v3 must be compiled under v4 without changing compiler source.
3. The real AWS Provider 5.100.0 schema JSON must be supplied to
   `report-corpus-coverage.py` so the provider-schema universe count is recorded.
4. A bounded-vs-broad/full v4 ingestion decision must be made from the measured resource/document
   and embedding volume.
5. v3 directory digest/content must remain unchanged.

Until those are satisfied, A7-1 does not start.

## Non-claims

This checkpoint does not claim:

- v4 is currently served by OpenSearch;
- all 1,514 provider documentation files correspond one-to-one with provider schema resources;
- every provider resource has usable official documentation;
- broad/full v4 has been embedded;
- corpus expansion alone proves output quality;
- current live runtime exists.

## Live boundary

No GCP resource, Vertex embedding request, OpenSearch ingestion, deployment, or other cost-bearing
action is authorized by A7-0 implementation.

If later v4 ingestion or representative runtime proof is required, it occurs under the separately
declared live checkpoint in A7-7.
