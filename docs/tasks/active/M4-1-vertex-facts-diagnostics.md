# M4-1 — Preserve root-cause evidence at Vertex fact extraction

**Status: COMPLETE — PR #69 merged as `6b6cd4bec6e0ecf78c8d9fb2d1f505201b6260a0`**

## Execution authorization

This is an explicitly authorized **non-live repository development task**.

Do not require live GCP credentials, a running GKE node, GitHub CLI/API access, billing access, or
Vertex calls to complete this task. All validation in this task must be deterministic/offline.

Do not implement retry/backoff behavior yet.

## Read first

- `AGENTS.md`
- `docs/AI_PROJECT_STATE.md`
- `docs/plans/MASTER_PLAN.md`
- `docs/plans/active/M4-ai-targeted-improvement.md`
- `docs/verification/m3-ai-evaluation-closure.md`
- `docs/evaluation/m3-live-baseline-analysis.md`
- `evaluation/baselines/m3-live-baseline-metrics.json`
- `backend/src/main/java/com/terraformers/modernization/reference/VertexArchitectureFactsExtractor.java`
- `backend/src/main/java/com/terraformers/modernization/evaluation/EvaluationRunner.java`
- `backend/src/main/java/com/terraformers/modernization/evaluation/EvaluationTrace.java`

## Canonical evidence

M3 canonical baseline run `36379633596` produced two first divergences:

- `arch-vpc-three-tier`: `FACT_EXTRACTION / PROVIDER_RUNTIME`;
- `arch-private-aoss`: `FACT_EXTRACTION / PROVIDER_RUNTIME`.

The recorded detail for both is only `IllegalStateException`.

The same VPC case passed the immediately preceding M3-R3c serving smoke, so M4 must not assume the
fixture is deterministically invalid.

## Problem

The current `VertexArchitectureFactsExtractor` combines multiple failure classes behind generic
runtime exceptions:

1. the Google Gen AI `generateContent` provider call can fail;
2. the provider can return MAX_TOKENS;
3. response text can be empty;
4. JSON can be malformed or not an object;
5. required fields can have invalid types;
6. the parsed fact object can be empty.

The current `EvaluationRunner` catches all of these as `RuntimeException` and records only:

- category `PROVIDER_RUNTIME`; and
- `exception.getClass().getSimpleName()`.

That loses the information needed to decide whether M4-2 should investigate throttling/transport,
response-format handling, or another cause.

## Goal

Preserve safe, actionable fact-extraction failure evidence while keeping successful behavior
unchanged.

Do **not** add retries in this task.

## Required design

### 1. Keep the application port provider-neutral

Do not change `ArchitectureFactsExtractor` to expose Google/Vertex types.

Provider-specific exception classes may live under the Vertex/reference implementation boundary.

### 2. Introduce explicit fact-extraction failure semantics

Add the smallest exception/evidence mechanism needed to distinguish at least:

- provider call/runtime failure;
- response truncated/MAX_TOKENS;
- empty response;
- malformed/invalid JSON or schema/field shape;
- parsed facts empty.

The exact class structure is up to the implementation, but avoid one class per trivial validation if
a small typed exception with a stable reason enum/string is clearer.

### 3. Sanitize provider evidence

For provider-call failures, retain only safe information that is useful for RCA when available, such
as:

- stable failure reason/category;
- HTTP/status code or SDK status when exposed;
- provider error type/reason;
- whether the failure is plausibly transient, only if the SDK evidence supports that classification.

Do not preserve:

- credentials/tokens;
- image bytes/base64;
- prompt contents;
- full request payload;
- raw response payload;
- arbitrary provider stack trace text in machine-readable evaluation evidence.

Do not guess a status code if the SDK does not expose one.

### 4. Improve EvaluationRunner failure detail without changing first-divergence compatibility

The M3 first-divergence category must remain compatible with
`FACT_EXTRACTION / PROVIDER_RUNTIME` for provider-call/runtime failures.

The failure `detail` must become stable enough to distinguish the new fact-extraction subtypes
instead of only `IllegalStateException`.

If response-format/truncation cases already map cleanly to existing
`EvaluationFailureCategory` values, reuse them only when doing so preserves the M3 contract and
does not misclassify provider runtime failures. Otherwise keep the existing category and encode the
safe subtype in detail.

Do not add a parallel evaluation schema.

### 5. No retry behavior

Do not:

- retry `generateContent`;
- add sleep/backoff;
- change timeout behavior;
- change the facts prompt;
- change `MAX_FACT_TOKENS`;
- change model ID/configuration;
- change dataset/corpus/retrieval/generation/validator behavior.

Retry is an M4-2/M4-3 decision only after live reproduction identifies a transient cause.

## Offline tests

Add focused deterministic tests covering at minimum:

1. successful extraction remains unchanged;
2. provider-call exception becomes a sanitized provider-runtime failure;
3. MAX_TOKENS is distinguishable;
4. empty response is distinguishable;
5. malformed JSON / invalid field shape is distinguishable;
6. parsed empty facts are distinguishable;
7. EvaluationRunner records the intended stable failure detail/subtype;
8. provider-call failure still yields first divergence at FACT_EXTRACTION and does not execute
   retrieval/generation/validation;
9. no secret/prompt/image payload is included in recorded failure detail.

Prefer injected/mocked `Client` or a minimal wrapper seam. Do not make live Vertex calls in tests.

Existing backend regression and `EvaluationRunnerTest` must remain green.

## Scope guard

Do not modify:

- prompt content;
- `terraformers-eval-v1`;
- `terraformers-reference-v3`;
- OpenSearch query/ranking/top-K;
- generation stage behavior;
- Terraform validator;
- GKE/Terraform/IAM/workflows;
- LangChain/LangGraph/framework architecture.

## Completion report

Report:

- base SHA;
- branch/head SHA;
- changed files;
- new failure subtype model;
- what provider evidence is preserved and sanitized;
- how EvaluationRunner records it;
- exact tests/commands run;
- confirmation that no retry/backoff was added;
- immediate next live handoff for M4-2.

If GitHub authentication is unavailable, complete code/tests locally and report the commit/diff.
Missing PR capability is not an implementation blocker.


## Completion evidence

- implementation PR: #69 — `Preserve Vertex fact-extraction root-cause diagnostics`;
- base SHA: `d606766bb591390cac1b5286bf59055e0a6f27a5`;
- implementation head SHA: `81b6088e70cb6440fe6319271abdc22f80a4cebf`;
- merge commit: `6b6cd4bec6e0ecf78c8d9fb2d1f505201b6260a0`;
- relevant pull-request workflows: all eight completed successfully;
- provider failure remains first-divergence compatible with
  `FACT_EXTRACTION / PROVIDER_RUNTIME`;
- response truncation, empty response, invalid response, and empty facts are now distinguishable;
- evaluation detail is bounded/sanitized and does not persist prompt/image/raw provider payloads;
- no retry/backoff, timeout, prompt, token limit, model, dataset, corpus, retrieval, generation,
  validator, Terraform, IAM, or workflow behavior was changed by M4-1.

Immediate handoff: add a protected existing-runtime resume operation, then run M4-2 bounded live
reproduction for `arch-vpc-three-tier` and `arch-private-aoss`.
