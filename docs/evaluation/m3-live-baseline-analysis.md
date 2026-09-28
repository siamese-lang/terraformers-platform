# M3 Live Baseline Analysis and Failure Taxonomy

## Status

**M3-5 COMPLETE — captured run analyzed without tuning**

Authoritative live run: `36379633596`  
Artifact: `m3-live-baseline-36379633596` (ID `10952207994`)  
Source commit: `8bb03ff6074246159d1007f572de454ed3a14df9`  
Configuration fingerprint: `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`

The workflow's final conclusion was failure only because a post-execution jq expression used invalid
`$.configuration` syntax. The six-case evaluation step itself completed, the artifact was
uploaded, and the ephemeral pod was deleted. PR #64 fixed the verifier and preserved run #1 as the
canonical baseline. This report analyzes that exact run; no rerun or tuning is substituted.

## Aggregate result

| Measure | Result |
| --- | ---: |
| Fixed cases | 6 |
| Fact extraction PASS | 4 / 6 |
| Fact extraction FAIL | 2 / 6 |
| Retrieval PASS | 4 / 6 |
| Generation PASS | 4 / 6 |
| Validation PASS | 2 / 6 |
| Cases with first divergence | 2 / 6 |
| First-divergence class | 2 × `FACT_EXTRACTION / PROVIDER_RUNTIME` |

The two validation PASS values are the two positive architecture cases that reached generation.
The ambiguous and non-architecture cases correctly skip Terraform validation. Two other positive
architecture cases never reached retrieval because fact extraction failed first.

## Per-case baseline

| Case | Facts | Retrieval | Generation | Validation | First divergence |
| --- | --- | --- | --- | --- | --- |
| `arch-vpc-three-tier` | FAIL | NOT_RUN | NOT_RUN | NOT_RUN | `FACT_EXTRACTION:PROVIDER_RUNTIME` |
| `arch-cloudfront-private-alb` | PASS | PASS | PASS | PASS | none |
| `arch-private-aoss` | FAIL | NOT_RUN | NOT_RUN | NOT_RUN | `FACT_EXTRACTION:PROVIDER_RUNTIME` |
| `arch-s3-metadata-split` | PASS | PASS | PASS | PASS | none |
| `ambiguous-cropped-service-sketch` | PASS | PASS | PASS (`AMBIGUOUS`) | NOT_RUN | none |
| `non-architecture-deployment-dashboard` | PASS | PASS | PASS (`NON_ARCHITECTURE_IMAGE`) | NOT_RUN | none |

## Deterministic quality findings

### Classification

All four cases that reached generation matched the dataset's expected classification:

- CloudFront/private-ALB: `ARCHITECTURE_DIAGRAM`;
- S3/metadata split: `ARCHITECTURE_DIAGRAM`;
- cropped sketch: `AMBIGUOUS`;
- deployment dashboard: `NON_ARCHITECTURE_IMAGE`.

This is **4 / 4 among cases that reached generation**, not 6 / 6 overall. The two remaining positive
cases are unavailable for classification scoring because the pipeline failed earlier.

### Retrieval

For the two positive architecture cases that reached retrieval:

- required project-decision coverage: **2 / 2**;
- both required project-decision documents were retrieved at **rank 1**;
- required retrieval resource-type coverage: **4 / 4**.

Concrete evidence:

- `arch-cloudfront-private-alb` retrieved `tfref-v2-alb-private-origin` at rank 1 and included
  both `aws_cloudfront_distribution` and `aws_lb` evidence;
- `arch-s3-metadata-split` retrieved `tfref-v2-s3-content-metadata` at rank 1 and included both
  `aws_s3_bucket` and `aws_db_instance` evidence.

The two failed positive cases did not execute retrieval, so they must not be counted as retrieval
quality misses.

### Generation and validation

Across the two positive cases that reached generation:

- required generated Terraform resource types: **5 / 5**;
- checked forbidden Terraform resource types generated: **0 / 4**;
- `TerraformDraftValidator` PASS: **2 / 2**.

The two negative-control cases generated no Terraform and correctly skipped validation.

The canonical baseline therefore does **not** support retrieval-ranking or grounded-generation as
the first improvement target. Those stages look healthy where the upstream path reaches them.

## Fact-extraction review

The two first divergences are:

1. `arch-vpc-three-tier` — `PROVIDER_RUNTIME / IllegalStateException`;
2. `arch-private-aoss` — `PROVIDER_RUNTIME / IllegalStateException`.

The VPC case is particularly important: the immediately preceding M3-R3c serving smoke executed the
same case successfully through extraction, retrieval, and generation. Its failure in the full
baseline is therefore evidence of live provider/runtime variability rather than a deterministic
bad fixture.

The current Java `VertexArchitectureFactsExtractor` performs a direct
`client.models.generateContent(...)` call and has no bounded retry/backoff layer. Its evaluation
trace records only the exception class for this failure category. The baseline artifact therefore
cannot determine whether the underlying provider failure was throttling, transport/capacity, or
another runtime exception.

This yields two distinct M4-relevant findings:

### F1 — Fact-extraction provider/runtime instability

**Priority: primary.**

Two of four positive architecture cases were blocked before retrieval. A system whose downstream
retrieval/generation quality is good but cannot reliably reach those stages has a reliability
problem before it has a ranking/prompt problem.

### F2 — Provider failure diagnostic loss

**Priority: root-cause blocker.**

The trace says only `IllegalStateException`. Provider status, sanitized provider error category,
retry attempt/count, and whether a retry was attempted are absent. M4 should not blindly add retries
without first preserving enough provider error evidence to distinguish transient failures from
response-format or application defects.

## Latency

Observed milliseconds:

| Stage | Result |
| --- | ---: |
| Fact extraction mean, all 6 | 7,041.8 |
| Fact extraction mean, PASS only | 6,629.5 |
| Fact extraction mean, FAIL only | 7,866.5 |
| Retrieval mean, 4 executed | 1,200 |
| Generation mean, 4 executed | 11,324 |
| Generation mean, architecture cases | 17,809 |
| Generation mean, negative controls | 4,839 |

Observed end-to-end stage sums:

- CloudFront/private-ALB: 26,095 ms;
- S3/metadata split: 24,264 ms;
- ambiguous sketch: 13,636 ms;
- deployment dashboard: 12,620 ms.

The two provider-runtime failures ended after 8,545 ms and 7,188 ms respectively.

## Usage and cost evidence

Provider usage evidence is incomplete.

- CloudFront/private-ALB output tokens: 2,252;
- S3/metadata split output tokens: 1,724;
- total observed output tokens: 3,976;
- input-token counts: unavailable;
- provider-reported cost: unavailable.

This is **F3 — usage/cost telemetry gap**. It does not block quality diagnosis, but it prevents a
reproducible per-case cost baseline.

## Human-reviewed extraction semantics

Component and relationship strings are not normalized to the dataset's labels, so semantic review is
kept separate from deterministic counters.

- CloudFront/private-ALB: all required components and required relationships are present
  semantically; the public entry remains CloudFront and the private ALB is not modeled as a second
  user-facing entry.
- S3/metadata split: all required components and relationships are present semantically; MariaDB
  owns metadata and S3 owns content bytes.

No semantic score is assigned to the two fact-extraction failures.

## Secondary observation from M3-R3c

The earlier one-case serving smoke produced a validation failure because generated Terraform
contained the comment `Placeholder EC2 instance`, which the current validator treated as
placeholder/example output. That behavior did **not** appear in the two positive cases that reached
validation in the canonical six-case baseline, both of which passed.

It remains useful evidence, but it is not counted as a run-1 baseline failure class.

## M4 handoff

The evidence supports this order of investigation:

1. preserve and classify Vertex fact-extraction provider errors;
2. test bounded retry/backoff only for demonstrably transient provider failures;
3. rerun the **same** six-case dataset/configuration after that targeted change;
4. compare first-divergence count and latency against run `36379633596`.

Do **not** start with:

- prompt changes;
- retrieval top-K/ranking changes;
- corpus expansion;
- rerankers;
- model changes;
- LangChain/LangGraph adoption.

The baseline does not show those as the first bottleneck.

## Framework implication

Current evidence still favors the existing linear Java orchestration. The dominant failure class is
provider-call reliability/diagnostics at one stage, not branching workflow complexity. LangGraph or
LangChain would not directly address the observed first divergence and remain evidence-gated for
M3-6.

## Reproducibility

Machine-readable metrics:
`evaluation/baselines/m3-live-baseline-metrics.json`

Durable run summary:
`evaluation/baselines/m3-live-baseline-run-36379633596-summary.json`

Full trace artifact:
GitHub Actions run `36379633596`, artifact `10952207994`.
