# Adaptive Retrieval Live Measurement Closure

## Status

**PASS — ADAPTIVE RETRIEVAL LIVE MEASUREMENT COMPLETE**

This document closes the bounded live measurement prepared by PR #134. It records the actual
before/after retrieval evidence for the adaptive evidence-budget behavior introduced by PR #127.

This closure does not authorize a model, prompt, embedding, corpus, dataset, holdout, infrastructure,
IAM, generation, or further retrieval change.

## Authoritative execution

- GitHub Actions run: `36799722509`
- workflow: `GCP Target Evaluation Baseline`
- scope: `adaptive-retrieval-probe`
- source commit: `9d54fe27bbc24347c9eff09193c7aeaec3171ff6`
- event: `workflow_dispatch`
- run attempt: `1`
- workflow conclusion: `success`
- artifact ID: `11134889355`
- artifact name: `adaptive-retrieval-probe-36799722509`
- artifact digest:
  `sha256:a35c2c5001fb90c07d6cb6587aeff8e1dc3a8e706b2fe277ffb13a39b3732c60`
- artifact schema: `adaptive-retrieval-live-probe-v1`

The workflow's trusted-request gate, GKE/OpenSearch substrate checks, bounded Kubernetes permission
checks, Workload Identity checks, adaptive probe execution, artifact identity/coverage verification,
artifact upload, and ephemeral pod cleanup all completed successfully.

## Frozen identity

The live probe used the frozen `large-vpc-eks-rds-s3` scenario:

- corpus: `terraformers-reference-v3`
- provider knowledge version: `5.100.0`
- embedding model: `gemini-embedding-001`
- embedding dimension: `1024`
- base top-K: `8`
- control max evidence: `8`
- adaptive max evidence: `16`
- delegated Vertex embedding calls across both arms: **1**

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

The committed v3 corpus requires a minimum of **10 documents** to cover all twelve resource types,
so an eight-document final evidence budget is structurally incapable of complete coverage.

## Live before/after result

| Measurement | Control | Adaptive |
|---|---:|---:|
| base top-K | 8 | 8 |
| max evidence | 8 | 16 |
| selected evidence | 8 | 10 |
| resource coverage | 10 / 12 | 12 / 12 |
| missing resource types | 2 | 0 |
| retrieval latency | 584 ms | 249 ms |

Control missing resource types:

- `aws_route_table`
- `aws_db_instance`

Adaptive added exactly the two evidence documents needed to close those gaps:

- `tfaws-5.100.0-aws_route_table-schema`
- `tfaws-5.100.0-aws_db_instance-schema`

The adaptive arm therefore reached complete 12/12 resource coverage with exactly **10 selected
documents**, matching the precomputed minimum corpus document cover.

## Selected evidence

### Control — 8 documents

1. `tfaws-5.100.0-aws_eks_cluster-example-1-eks-cluster`
2. `tfaws-5.100.0-aws_internet_gateway-schema`
3. `tfref-v2-alb-private-origin`
4. `tfaws-5.100.0-aws_subnet-schema`
5. `tfref-v2-s3-content-metadata`
6. `tfaws-5.100.0-aws_nat_gateway-example-1-public-nat`
7. `tfaws-5.100.0-aws_vpc-schema`
8. `tfref-v2-sg-relations`

The control still covered ten resource types because one project-decision document,
`tfref-v2-alb-private-origin`, covers `aws_lb`, `aws_lb_listener`, and
`aws_lb_target_group` together. It nevertheless could not admit route-table and DB-instance
evidence inside the fixed eight-document final context.

### Adaptive — 10 documents

The adaptive arm retained all eight control documents and added:

9. `tfaws-5.100.0-aws_route_table-schema`
10. `tfaws-5.100.0-aws_db_instance-schema`

No selected document ID was duplicated.

## Mechanism demonstrated

The live result supports this bounded mechanism:

> The base semantic retrieval size can remain K8 while the final evidence budget grows only when
> requested-resource coverage cannot fit inside the base budget.

The failure was not that route-table or DB-instance knowledge was absent from the corpus. The
knowledge existed and was retrievable. The fixed final evidence ceiling prevented both required
resource anchors from coexisting with the other selected evidence.

The adaptive selector preserved the existing evidence and admitted only the additional resource
anchors required for complete requested-resource coverage, stopping at ten rather than filling all
sixteen available positions.

This is the intended PR #127 behavior and is now demonstrated against:

- the committed v3 corpus;
- one real Vertex query embedding;
- the live GKE-hosted OpenSearch index;
- the production `OpenSearchReferenceRetriever`;
- the production `ReferenceEvidenceSelector`.

## Frozen acceptance result

| Acceptance criterion | Result |
|---|---|
| exact approved merged source SHA | PASS |
| frozen scenario identity unchanged | PASS |
| delegated embedding calls = 1 | PASS |
| control coverage < 12/12 | PASS — 10/12 |
| control evidence count <= 8 | PASS — 8 |
| adaptive coverage = 12/12 | PASS |
| adaptive evidence count >8 and <=16 | PASS — 10 |
| adaptive missing resources = 0 | PASS |
| adaptive duplicate document IDs = 0 | PASS |
| no production/corpus/dataset/infra change required | PASS |

Overall result: **PASS**.

## Latency interpretation

The artifact records control retrieval latency `584 ms` and adaptive retrieval latency `249 ms`.

Do **not** interpret this single sequential run as evidence that adaptive retrieval is faster.
Control executed first and adaptive second, so JVM, HTTP, OpenSearch, filesystem, or index-cache
warming can affect the measurement. The Work Package acceptance target was coverage-ceiling removal,
not latency improvement.

No percentile, throughput, or long-run latency claim is supported by this run.

## Portfolio-grade technical conclusion

The defensible engineering story is:

1. **Problem:** final retrieval evidence was implicitly capped at eight documents.
2. **Impact:** a realistic 12-resource architecture can require more independent evidence than K8
   can hold, even when every required resource is present in the corpus.
3. **Reproduction:** the frozen live control selected 8 documents and covered only 10/12 resources.
4. **Root mechanism:** final evidence capacity, not corpus absence or global search K, blocked
   `aws_route_table` and `aws_db_instance` anchors.
5. **Decision:** retain base K8 semantic acquisition, but separate final evidence capacity and allow
   bounded growth to at most 16 when coverage requires it.
6. **Implementation:** PR #127 added adaptive evidence expansion while preserving the production
   retriever/selector architecture.
7. **Same-scenario after-state:** 8→10 evidence and 10/12→12/12 coverage, with the two exact missing
   provider-schema anchors admitted.
8. **Residual risk:** this run proves requested-resource coverage for one deliberately large frozen
   scenario; it does not prove generation quality, token-budget sufficiency for every architecture,
   latency improvement, or percentile reliability.

## Residual boundaries

- This is one live scenario, not a statistical workload distribution.
- Complete resource-type coverage does not by itself prove that every selected document is the best
  possible evidence for generation.
- The final generation context may eventually face token-budget pressure even before the
  max-evidence count reaches 16.
- This measurement did not invoke Gemini generation, so no Terraform-output quality claim is made.
- The earlier Case A canonical/holdout closure contract remains separate.

## Closure

The adaptive-retrieval >8-resource investigation is **closed**. Do not repeat this probe simply to
obtain a different latency sample or a second successful artifact.

The broader Case A remains **OPEN** because the repository still does not record completion of the
fresh canonical N=3 after the PR #119 lifecycle correction followed by the frozen holdout. This
document does not start that validation automatically.
