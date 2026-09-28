# M4 — AI Targeted Improvement

## Status

**COMPLETE — M4-1 THROUGH M4-5 CLOSED**

M3 is complete. M4 changes only failure classes that were observed in the canonical M3 baseline.

Canonical before-state:

- workflow run: `36379633596`
- evaluation run: `m3-baseline-36379633596-1`
- dataset: `terraformers-eval-v1`
- configuration fingerprint:
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`
- analysis: `docs/evaluation/m3-live-baseline-analysis.md`
- metrics: `evaluation/baselines/m3-live-baseline-metrics.json`

## Objective

Improve only an evidence-backed M3 failure class through:

`baseline failure → root cause → minimal targeted change → same-dataset re-evaluation`

The first target is:

`FACT_EXTRACTION / PROVIDER_RUNTIME`

Affected canonical cases:

- `arch-vpc-three-tier`
- `arch-private-aoss`

The same VPC case passed the preceding M3-R3c smoke and failed in the six-case baseline. M4-2 later
reproduced that VPC failure as explicit output truncation while the second affected AOSS case passed
end to end under the unchanged configuration.

## Non-goals

Do not change these merely to improve a score:

- prompt contents;
- `terraformers-eval-v1` case expectations or fixtures;
- `terraformers-reference-v3` corpus contents;
- OpenSearch top-K/ranking;
- generation model;
- embedding model;
- Terraform validator;
- GKE/OpenSearch topology;
- LangChain;
- LangGraph;
- reranker;
- additional judge/model call;
- persistent Python AI worker/service.

Do not create a second live evaluation/runtime environment.

## M4-1 — Preserve root-cause evidence at Vertex fact extraction

**Status: COMPLETE — PR #69 / merge `6b6cd4bec6e0ecf78c8d9fb2d1f505201b6260a0`**

### Problem

The canonical baseline records only:

`PROVIDER_RUNTIME / IllegalStateException`

That is enough to localize the first failed stage but not enough to distinguish:

- transient provider throttling/capacity;
- transport/runtime error;
- model finish/empty response;
- response-format/schema parsing failure;
- application defect.

The current `VertexArchitectureFactsExtractor` performs a direct
`client.models.generateContent(...)` call and has no fact-extraction-specific bounded retry or
sanitized provider failure classification.

### Change boundary

Add the smallest provider-specific failure evidence needed to identify the root cause without
changing successful extraction semantics.

Requirements:

1. keep `ArchitectureFactsExtractor` provider-neutral;
2. do not expose credentials, request bytes, image bytes, or prompt contents;
3. preserve a sanitized provider/runtime error category and useful status/reason where the SDK
   exposes one;
4. distinguish provider-call failure from output/format validation failure;
5. preserve whether any retry occurred and retry count if retry behavior is later introduced;
6. keep the existing evaluation first-divergence category compatible;
7. keep current success output and prompt unchanged;
8. add focused deterministic tests with simulated provider exceptions and malformed responses.

### Validation

- existing backend regression remains green;
- `EvaluationRunnerTest` remains green;
- new fact-extraction error-classification tests are deterministic and offline;
- no prompt/model/corpus/retrieval behavior changes.

### Completion evidence

A failed fact-extraction trace can identify a safe, actionable failure subtype rather than only
`IllegalStateException`.

## Delivery prerequisite — Resume the existing target runtime

**Status: COMPLETE — PR #71 / activate run `36384234371`**

The canonical target runtime is intentionally idle with Terraform `node_count=0`. M4-2 requires
one existing workload node, but the protected apply workflow currently has no operation for
0 → 1 on an already-created runtime. The `foundation` operation must not be reused because it is
restricted to an empty canonical runtime state.

Add one repository-owned `activate`/resume operation with this contract:

- the canonical GKE cluster and node pool must already exist in remote state;
- set `TF_VAR_node_count=1`;
- the saved plan must contain exactly one update:
  `google_container_node_pool.target`;
- create, delete, replacement, and any other managed-resource update are forbidden;
- apply the exact saved plan through protected `gcp-target-apply`;
- reuse the existing GitHub OIDC/apply identity;
- create no new GCP resource or IAM privilege;
- after apply, prove cluster `RUNNING`, node pool `RUNNING`, and Terraform `node_count=1`.

This is delivery lifecycle support for the already-approved single target runtime, not a second
runtime and not an M4 quality change.

Completion evidence:

- PR #71 merged the `activate` operation as `64dbaae156906e3705e4bfeb161fb11bacd79f52`;
- protected run `36384234371` completed successfully;
- plan gate recorded `operation=activate`, `resource_change_count=1`, plan JSON SHA-256
  `49055c3593910e55d3a7f68a18a8502f09ef18d06523203817c9439e35942b22`;
- apply result was `0 added, 1 changed, 0 destroyed`;
- the changed resource was the canonical `google_container_node_pool.target`;
- cluster and node pool were verified `RUNNING`, with Terraform `node_count=1`.

## M4-2 — Reproduce the root cause on the same target runtime

**Status: COMPLETE — runs `36385950712` and `36386233526`**

### Problem

M3 proves variability but not the underlying provider cause.

### Execution boundary

Use the same GKE/Vertex/OpenSearch runtime, KSA/Workload Identity, model, dataset, and configuration
identity. Do not rebuild the runtime or change the fixed dataset.

First rerun only enough evidence to reproduce the affected fact-extraction failure with the new
diagnostics. Use the existing GCP target evaluation workflow in single-case mode, once for each
affected case:

- `m4-arch-vpc-three-tier`;
- `m4-arch-private-aoss`.

Both runs must use confirmation `RUN_M4_2_REPRODUCTION` and the exact current `main` SHA. A PASS
means the original fact-extraction failure was not reproduced in that bounded attempt; it is not a
reason to invent a fix or to rerun until a failure appears.

### Decision gate

Do not implement retry/backoff until the captured failure is classified.

Examples:

- if the provider reports transient throttling/5xx/capacity, bounded retry/backoff is eligible;
- if output is truncated/empty/malformed, fix only the evidenced response-handling issue;
- if the issue cannot be reproduced, record that result and do not invent a fix.

### Completion evidence

Detailed evidence is recorded in
[`m4-r2-live-reproduction-evidence.md`](../../evaluation/m4-r2-live-reproduction-evidence.md).

- `arch-vpc-three-tier`, run `36385950712`: fact extraction **FAIL** at
  `FACT_EXTRACTION / OUTPUT_TRUNCATED`, detail `reason=RESPONSE_TRUNCATED`, 9556 ms; downstream
  stages did not run.
- `arch-private-aoss`, run `36386233526`: fact extraction **PASS** 9392 ms, retrieval **PASS**
  3787 ms, generation **PASS** 17204 ms with `retryOccurred=false`, validation **PASS** 3 ms, and
  no first divergence.
- both runs used configuration fingerprint
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`.

The reproduced evidence does not support generic retry/backoff. It supports a truncation-targeted
M4-3 change; the AOSS baseline failure is explicitly recorded as not reproduced in this bounded
attempt.

## M4-3 — Minimal targeted behavior change

**Status: COMPLETE — PR #74 / merge `4369feb20da5ef4c48986667c88606cc6488259f`**

M4-2 reproduced `arch-vpc-three-tier` as `RESPONSE_TRUNCATED` and did not capture a transient
HTTP/provider error. Code inspection found that Vertex fact extraction keeps a hard
`MAX_FACT_TOKENS=800` limit while Gemini 3.8 Flash defaults to `MEDIUM` thinking. Gemini's output
limit includes thinking plus visible response tokens, so a compact structured-output request can
reach `MAX_TOKENS` before producing the final JSON. The smallest targeted change is to keep the
existing 800-token bound and explicitly set fact extraction to `LOW` thinking; prompt, schema,
model, corpus, retrieval, validator, and retry behavior remain unchanged.

The implementation preserved the same fixed dataset and generation/embedding model identities,
successful structured response parsing, the 800-token fact-extraction bound, prompt, corpus,
retrieval, top-K, validator, and no-retry behavior.

Targeted validation run `36388548679` on merge commit
`4369feb20da5ef4c48986667c88606cc6488259f` passed the previously reproduced
`arch-vpc-three-tier` case end to end:

- fact extraction **PASS**, 5770 ms;
- retrieval **PASS**, 2692 ms;
- generation **PASS**, 20218 ms;
- validation **PASS**, 1 ms;
- `firstDivergence=null`;
- `retryOccurred=false`.

The M4-2 `RESPONSE_TRUNCATED` failure was therefore controlled without generic retry/backoff.

## M4-4 — Same-dataset before/after comparison

**Status: COMPLETE — FULL RUN `36391698161`**

Run all six unchanged `terraformers-eval-v1` cases on the same target configuration.

The comparison must preserve:

- dataset version and fixture SHA identity;
- corpus/index `terraformers-reference-v3`;
- provider knowledge version `5.100.0`;
- top-K 8;
- `gemini-3.8-flash`;
- `gemini-embedding-001`;
- 1024-dimensional embeddings;
- production Java evaluation path.

Compare at minimum:

- first-divergence count/category;
- fact-extraction completion;
- downstream retrieval/generation reachability;
- latency;
- retry occurrence;
- classification/retrieval/generation/validation regression;
- available usage data.

Do not claim improvement from a different model/corpus/prompt/configuration.

Completion evidence is recorded in
[`m4-targeted-improvement-closure.md`](../../evaluation/m4-targeted-improvement-closure.md).

Full run `36391698161` executed all six unchanged cases on commit
`2526f85b4bef781976126f99e5ed0c25344d0f94` with the canonical fingerprint
`sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`.

Observed aggregate change:

- fact-extraction PASS: **4/6 → 6/6**;
- first divergence: **2 → 0**;
- retrieval PASS: **4/6 → 6/6**;
- generation PASS: **4/6 → 6/6**;
- positive architecture validation PASS: **2/4 executed → 4/4**;
- both negative-control classifications remained correct;
- no generation retry occurred.

Residual findings are preserved rather than tuned away: the newly reachable VPC retrieval stage
missed part of the fixed required top-8 coverage, and AOSS fact extraction had a one-run
`130489 ms` latency outlier.

## M4-5 — M4 closure

**Status: COMPLETE**

All seven exit conditions are met:

1. the VPC failure was reproduced as `RESPONSE_TRUNCATED`;
2. only fact-extraction thinking was reduced to `LOW`, retaining the existing 800-token bound;
3. all six fixed cases were rerun on the same target runtime identity;
4. fact-extraction failures changed from 2/6 to 0/6;
5. previously successful CloudFront/S3 and negative-control behavior remained successful;
6. no retry was added, and latency/retrieval residuals are documented;
7. framework expansion remains deferred.

Final protected idle run `36392580754` returned the canonical node pool to `node_count=0`
with exactly one in-place node-pool update and plan JSON SHA-256
`e8a7bd03f416c66c707dd44f01fbcae9e61eeed02a14d5d9b18f84d7ccc70367`.

## Current framework position

- LangGraph — DEFER
- LangChain — DEFER
- reranker — DEFER
- judge/model expansion — DEFER
- persistent Python AI worker/service — DEFER

M4-1 through M4-4 may change this only if new evidence passes ADR-004.

## Runtime/cost rule

Reuse the single existing target runtime.

When no live reproduction or comparison is running, keep the GKE target node pool at the approved
idle size of 0. Reactivate one `e2-standard-2` node only for bounded live evidence sessions and
return it to 0 afterward.

## Immediate next single task

Create the M5 — Backend Reliability Baseline active plan from current `AnalysisJob` lifecycle
evidence. Do not select RabbitMQ, Transactional Outbox, retry infrastructure, or another reliability
mechanism before M5 reproduces and classifies current failure behavior.
