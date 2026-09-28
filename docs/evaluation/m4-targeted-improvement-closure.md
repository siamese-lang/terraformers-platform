# M4 AI Targeted Improvement Closure

## Status

**COMPLETE**

M4 closes the M3 first-divergence class through an evidence-bounded change:

`baseline failure → root cause → minimal targeted change → same-dataset re-evaluation`.

## Before state

Canonical M3 baseline:

- workflow run: `36379633596`
- source commit: `8bb03ff6074246159d1007f572de454ed3a14df9`
- evaluation run: `m3-baseline-36379633596-1`
- dataset: `terraformers-eval-v1`
- configuration fingerprint:
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`
- first divergence: `FACT_EXTRACTION / PROVIDER_RUNTIME` in 2 of 6 cases
- affected cases: `arch-vpc-three-tier`, `arch-private-aoss`

The VPC case was later reproduced in M4-2 as
`FACT_EXTRACTION / OUTPUT_TRUNCATED` with `reason=RESPONSE_TRUNCATED`.
The AOSS failure did not reproduce in the bounded M4-2 rerun.

## Root cause and targeted change

M4-3 inspected the production Vertex fact-extraction path and found:

- fact extraction had its own fixed `MAX_FACT_TOKENS=800` bound;
- Gemini 3.8 Flash default thinking was not explicitly constrained for this compact structured-output request;
- the provider reported `MAX_TOKENS` / truncated structured output in the reproduced VPC case.

PR #74, merged as `4369feb20da5ef4c48986667c88606cc6488259f`, changed only the
Vertex fact-extraction request to use `LOW` thinking while retaining:

- the 800-token fact-extraction bound;
- the same prompt and structured JSON schema;
- `gemini-3.8-flash`;
- `gemini-embedding-001`;
- `terraformers-reference-v3`;
- top-K 8;
- the existing Terraform validator;
- no generic retry/backoff.

Targeted VPC validation run `36388548679` then passed fact extraction, retrieval, generation, and
validation with no first divergence and `retryOccurred=false`.

## Same-dataset before/after comparison

M4-4 full comparison:

- workflow run: `36391698161`
- source commit: `2526f85b4bef781976126f99e5ed0c25344d0f94`
- evaluation run: `m3-baseline-36391698161-1`
- artifact: `m3-live-baseline-36391698161`
- artifact id: `10955719668`
- artifact digest:
  `sha256:3697bf1a703b437132d6bf98eb89a51ab45810154afb02d3e5d3e1ffcbb7d1c6`
- dataset: `terraformers-eval-v1`
- configuration fingerprint:
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`

The six fixtures, corpus/index, provider knowledge version, models, embedding dimension, top-K,
and production Java evaluation path remained fixed.

| Metric | M3 before | M4 after |
| --- | ---: | ---: |
| Case count | 6 | 6 |
| Fact extraction PASS | 4 | 6 |
| Fact extraction FAIL | 2 | 0 |
| Retrieval PASS | 4 | 6 |
| Generation PASS | 4 | 6 |
| Positive architecture validation PASS | 2 | 4 |
| First divergence | 2 | 0 |
| Generation retries observed | 0 | 0 |
| Negative-control classification matches | 2/2 | 2/2 |

### After-state case results

| Case | Fact extraction | Retrieval | Generation | Validation | First divergence |
| --- | --- | --- | --- | --- | --- |
| `arch-vpc-three-tier` | PASS, 4403 ms | PASS, 2591 ms | PASS, 26038 ms | PASS, 1 ms | none |
| `arch-cloudfront-private-alb` | PASS, 2898 ms | PASS, 1296 ms | PASS, 20017 ms | PASS, 1 ms | none |
| `arch-private-aoss` | PASS, 130489 ms | PASS, 1383 ms | PASS, 14442 ms | PASS, 0 ms | none |
| `arch-s3-metadata-split` | PASS, 2931 ms | PASS, 1290 ms | PASS, 17151 ms | PASS, 1 ms | none |
| `ambiguous-cropped-service-sketch` | PASS, 2997 ms | PASS, 1257 ms | PASS, 5221 ms | NOT_RUN | none |
| `non-architecture-deployment-dashboard` | PASS, 3499 ms | PASS, 424 ms | PASS, 4794 ms | NOT_RUN | none |

All four architecture cases produced the expected architecture classification and passed Terraform
validation. Both negative controls retained their expected classification and emitted no Terraform.
No generation retry occurred.

## Previously successful behavior

The M3 cases that had already reached downstream stages remained successful:

- `arch-cloudfront-private-alb`: required project-decision retrieval, grounded generation, and
  validation remained successful;
- `arch-s3-metadata-split`: required project-decision retrieval, grounded generation, and
  validation remained successful;
- both negative controls remained correctly classified and emitted no Terraform.

This satisfies the M4 regression boundary for previously successful behavior.

## Residual findings

M4 does not claim that every downstream quality expectation is now perfect.

### VPC retrieval coverage

The VPC case now reaches retrieval and generation for the first time in the full six-case comparison.
Its top-8 retrieval result included `aws_vpc` and `aws_lb` evidence but did not include the fixed
dataset's required `tfref-v2-sg-relations`, `aws_db_instance`, or `aws_security_group`
coverage. Generation nevertheless produced all fixed required Terraform resource types and passed
validation.

This is a newly observable downstream retrieval-quality gap, not a regression from the M3 baseline,
because the VPC case did not reach retrieval in that baseline. M4 does not introduce reranking,
top-K, corpus, or prompt changes to chase this finding without a separate evidence gate.

### AOSS latency outlier

The AOSS fact-extraction stage passed but took `130489 ms` in the one M4-4 full comparison.
The other five fact-extraction stages were `2898–4403 ms`, with an all-case median of `3248 ms`.
The same AOSS case had passed fact extraction in the bounded M4-2 reproduction at `9392 ms`.

This single outlier is preserved as variability evidence. It is not used to justify retry,
timeout, model, or topology changes in M4.

### Configuration fingerprint boundary

The retained configuration fingerprint hashes the live evaluation runtime identity
(model/corpus/retrieval/generation settings) but does not include the fact-extraction-specific
thinking level. Therefore the unchanged fingerprint proves the fixed evaluation identity covered by
that contract, while the exact source commit and PR #74 identify the deliberate M4 behavior delta.
M4 does not revise the fingerprint contract during closure.

## Runtime and cost closure

The final M4 live session reused the single canonical target runtime.

Activation:

- workflow run: `36391340996`
- commit: `2526f85b4bef781976126f99e5ed0c25344d0f94`
- operation: `activate`
- plan JSON SHA-256:
  `c11273a98e1b3a82ec3372cc45b9be36c08d0f3e91a91ee173fe82f5858f6729`
- only `google_container_node_pool.target` changed, `node_count 0 → 1`
- apply: `0 added, 1 changed, 0 destroyed`

Final idle:

- workflow run: `36392580754`
- commit: `2526f85b4bef781976126f99e5ed0c25344d0f94`
- operation: `idle`
- plan JSON SHA-256:
  `e8a7bd03f416c66c707dd44f01fbcae9e61eeed02a14d5d9b18f84d7ccc70367`
- only `google_container_node_pool.target` changed, `node_count 1 → 0`
- apply: `0 added, 1 changed, 0 destroyed`
- final runtime-boundary verification passed with `node_count=0`.

## Exit decision

M4 exit criteria are **MET**:

1. the M3 first-divergence class was reproduced and root-caused;
2. the implementation was limited to the smallest evidenced truncation remedy;
3. the same fixed six-case dataset and target runtime contract were rerun;
4. the targeted fact-extraction failure count changed from 2 to 0;
5. previously successful downstream and negative-control behavior did not regress;
6. no retry was introduced, and the latency/retrieval residual findings are explicitly recorded;
7. LangGraph, LangChain, reranking, judge-model expansion, and a persistent Python AI worker remain
   deferred.

M4 is complete. The next milestone is M5 — Backend Reliability Baseline. M5 must begin with an
active plan that measures current `AnalysisJob` lifecycle behavior before selecting a reliability
solution.
