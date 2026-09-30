# Bounded Gemini generation comparison

## Scope and reason

The planned Claude Opus live evaluation stopped before inference because Google Cloud Free Trial
Welcome credit cannot fund managed partner-model APIs and the user permits no paid spend. The next
bounded diagnostic is therefore one paired generation-only run over the existing six-case
`evaluation/opus-generation-v1/manifest.json`; the historical fixture name does not make its inputs
model-specific.

The control is `gemini-3.8-flash` and the candidate is `gemini-3.1-pro-preview` (Public Preview).
This comparison is not a production recommendation or production model change.

## Frozen contract

- Execute exactly one arm per model per case in one manual run, apart from the existing single
  compact retry on output truncation. Do not rerun quality results.
- Both arms use the same image bytes, ordered references, `VertexPromptBuilder` semantic prompt and
  response schema, `VertexResponseParser`, and `TerraformDraftValidator`.
- Both arms use location `global`, 8192 maximum output tokens, temperature 0.2 normally and 0.1 only
  for the existing compact truncation retry.
- Do not run or change fact extraction, embedding, retrieval, the reference selector, corpus,
  dataset, frozen fixture, or Case A holdout.

## Interpretation and stopping

The artifact may report control/candidate pass or each case's independent failure category. A
provider/setup failure is not model-quality evidence. A single run cannot establish that either
model is generally better or more reliable. Stop after the deterministic harness and manual-only
workflow are reviewed; do not dispatch it, change production routing, run a holdout, or start a
repeated evaluation without a separate user decision.


## Fact-reuse latency diagnostic closure

The later FACT_REUSE experiment reused the same frozen six-case generation fixture to test whether
one canonical image analysis could replace the second image-bearing generation request. Production
adoption was explicitly gated on preserved quality/safety **and** meaningful end-to-end latency
improvement.

That investigation is now closed by
[Gemini Fact-Reuse Latency Diagnostic Closure](../../evaluation/gemini-fact-reuse-latency-diagnostic-closure.md).

Key decision:

- candidate canonical classification and frozen generation acceptance were strong enough to continue
  diagnosis;
- repeated instrumented evidence reproduced a text-only `109773 ms` generation tail;
- all 24 logical requests across the final N=3 diagnostic used exactly one observed HTTP exchange,
  zero network failures, and HTTP 200;
- the long interval accumulated after request-body completion and before response headers;
- duplicate image processing, SDK retry, transport failure, and simple token volume do not explain
  the observed tail;
- provider-internal queue/scheduling versus inference compute remains unresolved;
- production fact-reuse adoption is **HOLD** because the frozen latency gate was not reliably met;
- generic retry and an arbitrary production timeout are not selected.

Do not rerun FACT_REUSE merely to obtain a faster sample. Any production adoption, retry, timeout,
model, or routing change requires a new explicit Work Package.
