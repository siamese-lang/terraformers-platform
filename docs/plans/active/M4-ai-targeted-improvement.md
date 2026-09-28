# M4 — AI Targeted Improvement

## Status

**ACTIVE — M4-1 COMPLETE / M4-2 PREPARATION**

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

The same VPC case passed the preceding M3-R3c smoke and failed in the six-case baseline. M4 must
therefore treat the failure as live provider/runtime variability until stronger evidence proves a
different cause.

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

**Status: NEXT**

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

## M4-2 — Reproduce the root cause on the same target runtime

**Status: BLOCKED ON EXISTING-RUNTIME RESUME PREREQUISITE**

### Problem

M3 proves variability but not the underlying provider cause.

### Execution boundary

Use the same GKE/Vertex/OpenSearch runtime, KSA/Workload Identity, model, dataset, and configuration
identity. Do not rebuild the runtime or change the fixed dataset.

First rerun only enough evidence to reproduce the affected fact-extraction failure with the new
diagnostics. A bounded targeted reproduction may use the two affected case IDs before a full
six-case comparison.

### Decision gate

Do not implement retry/backoff until the captured failure is classified.

Examples:

- if the provider reports transient throttling/5xx/capacity, bounded retry/backoff is eligible;
- if output is truncated/empty/malformed, fix only the evidenced response-handling issue;
- if the issue cannot be reproduced, record that result and do not invent a fix.

### Completion evidence

The root cause is either reproduced with sanitized evidence or explicitly classified as not
reproduced under the same runtime/configuration.

## M4-3 — Minimal targeted behavior change

**Status: CONDITIONAL**

Implement only the remedy justified by M4-2.

For a proven transient provider error, an acceptable candidate is bounded retry/backoff isolated to
Vertex fact extraction. It must:

- retry only demonstrably transient categories;
- have a strict attempt/time bound;
- preserve the same prompt/model and structured response contract;
- avoid retrying deterministic format/application failures;
- record retry occurrence in evaluation evidence;
- add deterministic tests for retryable and non-retryable paths.

If M4-2 identifies a different root cause, this step must be amended before implementation rather
than applying a generic retry.

## M4-4 — Same-dataset before/after comparison

**Status: BLOCKED ON TARGETED CHANGE**

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

## M4-5 — M4 closure

**Status: TODO**

M4 is complete only if:

1. one M3 failure class has a reproduced/root-caused explanation;
2. the implemented change is the smallest justified remedy;
3. the same fixed dataset/configuration is rerun;
4. the targeted failure is reduced or controlled in the comparison;
5. no regression is introduced in previously successful retrieval/generation/negative-control
   behavior;
6. trade-offs such as added latency/retries are documented;
7. framework decisions remain evidence-based.

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

Implement the existing-runtime `activate`/resume operation described above, validate it in CI,
merge it, and then use the protected workflow to move the canonical target node pool from 0 to 1.
Do not add retry/backoff before M4-2 live reproduction classifies the failure.
