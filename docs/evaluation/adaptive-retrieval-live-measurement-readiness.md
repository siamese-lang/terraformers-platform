# Adaptive Retrieval Live Measurement Readiness

## Status

**READINESS IMPLEMENTED — LIVE RUN NOT AUTHORIZED**

This readiness package measures the production adaptive retrieval behavior introduced by PR #127
without changing production retrieval, corpus, canonical datasets, generation, model routing, or
infrastructure.

The measurement is intentionally retrieval-only:

`frozen architecture facts → one Vertex query embedding → production OpenSearchReferenceRetriever
→ bounded evidence selection`

It does not invoke Gemini generation and does not claim Terraform-output quality improvement.

## Operating problem

PR #127 removed three hidden fixed-eight ceilings:

1. Vertex fact extraction previously limited every fact array to 8;
2. `ReferenceQuery` retained at most 8 resource types;
3. final retrieval evidence could not grow beyond the base top-K.

The implementation now allows up to 16 resource types and a separate max-evidence budget, default
16, while retaining base search K8. Evidence expands above K8 only when requested-resource coverage
cannot be preserved through replacement within the base budget.

The canonical six-case `terraformers-eval-v1` dataset cannot validate this operating problem
because no case requires more than eight resource types. Re-running that dataset would therefore
exercise only the already-covered simpler path.

## Frozen scenario

Scenario ID: `large-vpc-eks-rds-s3`

The fixed facts describe an internet-facing EKS application in a VPC with public/private subnets,
controlled egress, ALB ingress, RDS persistence, and S3 object storage.

Frozen resource types, in order:

1. `aws_vpc`
2. `aws_subnet`
3. `aws_internet_gateway`
4. `aws_nat_gateway`
5. `aws_route_table`
6. `aws_security_group`
7. `aws_lb`
8. `aws_lb_listener`
9. `aws_lb_target_group`
10. `aws_eks_cluster`
11. `aws_db_instance`
12. `aws_s3_bucket`

The fixture is committed at
`evaluation/adaptive-retrieval-v1/fixed-facts.json`.

It uses only resource types already present in the committed
`terraformers-reference-v3` corpus. A deterministic corpus set-cover check over the committed
`documents.jsonl` shows that these twelve resource types require **at least 10 corpus documents**
for complete coverage. Complete 12/12 coverage is therefore structurally impossible inside an
eight-document evidence budget.

This is the key reason for selecting the scenario. It does not rely on a lucky vector rank or an
invented threshold.

## Frozen comparison

Both arms use:

- the exact same frozen facts;
- the same `RetrievalQueryTextBuilder`;
- one `ReferenceQuery` with all 12 explicit canonical resource types;
- query limit 16;
- base OpenSearch top-K 8;
- `terraformers-reference-v3`;
- AWS provider knowledge version `5.100.0`;
- `gemini-embedding-001`, 1024 dimensions;
- the current production `OpenSearchReferenceRetriever`;
- the current production `ReferenceEvidenceSelector`;
- identical OpenSearch endpoint/index/query semantics.

The evaluation-only memoizing embedding adapter permits both arms to request the same query through
the production retriever while delegating to Vertex **exactly once**. Both arms therefore reuse the
same vector.

### Control

- base top-K: 8
- max evidence: 8

This recreates the evidence-budget ceiling while keeping the current query/search/selection code.

### Adaptive

- base top-K: 8
- max evidence: 16

This is the production PR #127 configuration boundary under test.

## Artifact contract

Schema: `adaptive-retrieval-live-probe-v1`

The artifact records:

- source commit;
- scenario/corpus/provider/embedding identity;
- base K and both max-evidence values;
- ordered query resource types;
- actual delegated embedding call count;
- per-arm retrieval latency;
- per-arm evidence count;
- selected document IDs;
- covered resource types;
- missing resource types;
- exact coverage matched/total;
- selected document authority, score, and resource-type metadata.

No p95/p99 or long-run reliability claim is permitted from the planned single run.

## Acceptance

The later live run is accepted only if all conditions hold:

1. source SHA equals the separately approved current `main`;
2. frozen scenario identity is unchanged;
3. `embeddingDelegateCalls == 1`;
4. control has `coverageTotal == 12`, `coverageMatched < 12`, and evidence count `<= 8`;
5. adaptive has `coverageMatched == 12`;
6. adaptive evidence count is `> 8` and `<= 16`;
7. adaptive has no missing resource type;
8. adaptive selected document IDs contain no duplicates;
9. no corpus, production retrieval, model, dataset, holdout, infrastructure, or IAM change is
   required to make the run pass.

A live failure counts as evidence. Do not rerun until passing.

## Implementation reuse

The readiness harness does not duplicate retrieval ranking logic. It constructs two
`OpenSearchReferenceRetriever` instances with different max-evidence properties and calls the
production retriever directly.

The existing `gcp-target-evaluation-baseline.yml` manual workflow is extended with exactly one new
scope:

`adaptive-retrieval-probe`

Confirmation token:

`RUN_ADAPTIVE_RETRIEVAL_PROBE`

The workflow continues to require an exact trusted `main` SHA and the existing GCP target
environment. It uploads one machine-readable artifact and reuses the existing ephemeral evaluation
pod lifecycle.

## Deterministic readiness validation

Offline tests verify:

- the frozen fixture contains 12 distinct canonical resource types;
- all 12 exist in the committed v3 corpus;
- the exact minimum corpus document cover is 10;
- production K8/max8 retrieval cannot cover all 12 in the deterministic fixture;
- production K8/max16 retrieval recovers 12/12;
- adaptive output grows to 12 evidence documents in the deterministic test;
- both arms share one delegated embedding;
- every underlying OpenSearch search remains size 8.

These tests validate the harness and structural scenario. They are not substitutes for the later
live Vertex/OpenSearch measurement.

## Non-goals

This readiness task does not:

- change PR #127 production behavior;
- change `terraformers-eval-v1` or the frozen Case A holdout;
- add corpus documents;
- call Gemini generation;
- evaluate Terraform output quality;
- tune embeddings or OpenSearch ranking;
- change base K8 or max16;
- create another workflow;
- dispatch a live workflow;
- activate/idle infrastructure;
- introduce rerankers, LangChain, LangGraph, or another vector store.

## Next checkpoint

After this readiness PR is independently reviewed and merged, the next candidate single task is one
manual `adaptive-retrieval-probe` live run on the exact merged `main` SHA.

That live Vertex/OpenSearch action remains **AWAITING_APPROVAL** and is not authorized by this
readiness implementation.
