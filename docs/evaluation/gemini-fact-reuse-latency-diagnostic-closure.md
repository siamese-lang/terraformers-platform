# Gemini Fact-Reuse Latency Diagnostic Closure

## Status

**DIAGNOSTIC COMPLETE — PRODUCTION FACT-REUSE ADOPTION HELD**

This document closes the bounded latency investigation that followed the six-case FACT_REUSE
comparison. It records evidence and the resulting decision boundary only. It does not authorize a
production routing, prompt, retry, timeout, model, retrieval, corpus, dataset, or infrastructure
change.

Authoritative source for the completed diagnostic attempts:

- GitHub Actions run `36758290144`
- source commit `b7de917d1c97c591f863f3e473f99c3d670161ef`
- comparison mode `FACT_REUSE_DIAGNOSTIC`
- frozen cases, in order:
  1. `arch-cloudfront-private-alb`
  2. `arch-private-aoss`
- model `gemini-3.8-flash`
- location `global`
- control generation thinking: model-default / MEDIUM-equivalent comparison path
- candidate generation thinking: `MEDIUM`
- control fact extraction thinking: `LOW`
- candidate canonical extraction thinking: `LOW`

The three attempts are a bounded reproducibility screen. They do not justify p95/p99 claims or a
long-run reliability rate.

## Why this diagnostic was run

The first six-case FACT_REUSE comparison, run `36739886593`, showed that reusing one canonical
image analysis could preserve the frozen candidate acceptance while removing the second image input,
but per-case latency varied dramatically. In particular:

- CloudFront control reached roughly 626 seconds in the original comparison;
- Private AOSS candidate text-only generation reached roughly 326 seconds;
- aggregate candidate wall time was lower than control, but some individual candidate cases were
  materially slower.

That evidence was insufficient to conclude that duplicate image analysis caused the tail, because a
text-only generation call also exhibited a multi-minute delay.

PR #130 added evaluation-only phase, transport, usage, and safe payload telemetry. PR #131 added the
bounded two-case diagnostic mode. The first diagnostic run `36755574921` completed model work but
failed when writing the JSON artifact because nested telemetry contained `java.time.Instant`
values and the launcher mapper did not register Java Time support. PR #132 fixed only that artifact
serialization defect and added a regression test. No production request semantics changed.

The failed pre-fix diagnostic is retained as supporting log evidence, not as a complete artifact.
Its most important observation was a Private AOSS candidate text-only generation latency of roughly
173.6 seconds.

## Frozen diagnostic rule

The user-approved bounded repeat was exactly three successful instrumented attempts at the same
source SHA and frozen inputs. A generation phase above 60 seconds was treated as a **diagnostic tail
event**, not as a production SLA or timeout recommendation.

No attempt was discarded because of a slow result or quality failure. No N=4 or repeat-until-lucky
execution is authorized by this closure.

## Attempt artifacts

| Attempt | Artifact ID | Digest |
|---|---:|---|
| 1 | `11117757769` | `sha256:f9835b5fd0feed821739cd786f90e3fc2608b1e2bbad87bc57babe63775c0718` |
| 2 | `11117899762` | `sha256:ac93a5f2c374865b34367b6fd1f5f47550c8250bb11eb7a9cdc867740e6cd126` |
| 3 | `11118632138` | `sha256:fff83ff7ab22e5e7188a6b394d135dd1234e9a1efc6be3f994018f31c698f4ad` |

All three artifacts use schema `gemini-fact-reuse-comparison-v2`.

## Phase latency evidence

Milliseconds are rounded to one decimal second below.

| Case / phase | Attempt 1 | Attempt 2 | Attempt 3 |
|---|---:|---:|---:|
| CloudFront control fact extraction | 3.7s | 3.8s | **48.5s** |
| CloudFront control generation | 19.0s | 25.1s | **59.8s** |
| CloudFront candidate canonical extraction | 4.1s | 3.9s | **63.7s** |
| CloudFront candidate text-only generation | 19.5s | 22.6s | **109.8s** |
| Private AOSS control fact extraction | 4.1s | 3.7s | 4.5s |
| Private AOSS control generation | 16.4s | 14.8s | 18.8s |
| Private AOSS candidate canonical extraction | 4.6s | 3.9s | 8.0s |
| Private AOSS candidate text-only generation | 18.7s | 17.3s | 19.4s |

Attempt 3 therefore reproduced the target phenomenon after attempts 1 and 2 remained in the
ordinary tens-of-seconds range. The clearest frozen tail event was CloudFront candidate text-only
generation at `109773 ms`.

The same attempt also showed elevated latency in three preceding CloudFront phases before the next
Private AOSS case returned to the ordinary range. This is evidence of short-window latency
variability on the provider/model side of the request boundary, not proof of a specific internal
Google scheduling mechanism.

## Transport boundary evidence

Across the three attempts there were exactly 24 logical Gemini requests:

`2 cases × 4 model phases × 3 attempts = 24`.

For all 24 logical requests:

- observed HTTP exchange count: `1`;
- network failure count: `0`;
- final HTTP status: `200`;
- compact application retry: not observed for the reported successful phases.

The tailing CloudFront candidate generation in attempt 3 had:

- modality: `TEXT`;
- image bytes: `0`;
- prompt tokens: `3703`;
- candidate tokens: `2784`;
- thoughts tokens: `596`;
- one HTTP exchange;
- zero network failures;
- HTTP 200;
- request body complete → response headers received: approximately `109.74 s`;
- total logical request time: approximately `109.75 s`.

The same candidate generation in attempts 1 and 2 used comparable prompt/thought-token scales but
completed in approximately 19.5 seconds and 22.6 seconds. Token count alone therefore does not
explain the tail.

The diagnostic can localize the delay to the interval after the client finished sending the request
and before response headers arrived. It cannot split that interval into provider queue/scheduling
time versus actual model inference compute time.

## Image-duplication hypothesis result

The original service path sends the image once for fact extraction and again for generation. The
candidate removes image bytes from the second generation request.

The diagnostic does **not** support the claim that duplicate image processing is the primary cause
of the observed multi-minute latency tail:

- the 109.8-second attempt-3 tail occurred on a text-only candidate generation;
- the earlier Private AOSS candidate text-only generation observations were roughly 326 seconds and
  173.6 seconds;
- image extraction phases can also vary, but removing the second image does not remove provider-side
  tail exposure.

Fact reuse still removes duplicate multimodal input and skips the second call for negative/ambiguous
inputs. Those are real structural properties, but they are not sufficient to satisfy the frozen
production-adoption latency gate.

## Quality and safety observations

Candidate evidence remained strong on the two diagnostic cases, but the diagnostic was not a new
six-case quality acceptance run.

- Attempts 1–3 candidate arms for the selected two cases recorded
  `firstFailureCategory = NONE`.
- Attempt 2 Private AOSS **control** failed
  `TERRAFORM_STRUCTURAL_VALIDATION` because the generated Terraform contained an
  account-specific AWS identifier.
- That control failure is retained; it was not rerun away.
- The original six-case FACT_REUSE comparison already recorded canonical classification 6/6 and
  candidate frozen acceptance 6/6, while control acceptance was 5/6.

These results do not weaken the frozen fact-reuse adoption gate: adoption required both preserved
quality/safety and a meaningful end-to-end latency improvement.

## Root-boundary conclusion

The strongest supported conclusion is:

> The observed extreme latency is a stochastic Vertex/Gemini provider-side response-wait phenomenon
> at the client/provider boundary. The current evidence does not support SDK retry, transport
> failure, duplicate image processing, or simple token volume as the primary mechanism.

More specifically:

| Hypothesis | Current evidence |
|---|---|
| client preprocessing / JSON construction | weakened; not where elapsed time accumulates |
| request upload | weakened; body completes before the long wait |
| response download | weakened; delay occurs before response headers |
| SDK/HTTP retry | not observed in the 24 instrumented requests |
| network/connect failure | not observed |
| duplicate image processing | insufficient; text-only generation also tails |
| prompt size alone | insufficient; comparable prompt sizes have very different latency |
| thoughts-token count alone | insufficient; comparable or larger counts can complete much faster |
| provider queue/scheduling vs model compute | unresolved inside the provider boundary |

No stronger provider-internal claim is made.

## Decision

### Fact-reuse production adoption

**HOLD — DO NOT CHANGE PRODUCTION ROUTING**

The Work Package allowed production adoption only if all of these were true:

1. canonical classification 6/6;
2. candidate frozen acceptance 6/6;
3. no new safety/account-specific literal regression;
4. meaningful end-to-end latency improvement on the frozen comparison.

The first two candidate quality gates were met in the original comparison, but the latency evidence
is not stable enough to claim that fact reuse reliably improves the measured operating problem.
Attempt 3 showed candidate CloudFront end-to-end latency `173451 ms` versus control
`108285 ms`, driven by the text-only provider wait.

Fact reuse therefore remains evaluation evidence, not an accepted production change. Production
adoption would require a new Work Package and a new explicit decision.

### Generic retry

**NOT SELECTED**

The tail requests returned one HTTP 200 exchange without a captured transient transport/provider
error. Automatically retrying such a long-running request could duplicate expensive inference and
increase both cost and latency. Existing compact retry behavior remains unchanged.

### Timeout

**NOT SELECTED**

The 60-second value used in this diagnostic was an observation threshold, not an approved user-facing
SLO. There is no frozen production timeout budget in this evidence set. A production timeout/cancel
policy requires a separate operating requirement and same-scenario validation.

## Residual risk

- The provider-internal split between queue/scheduling and actual model compute remains unknown.
- Three repetitions are intentionally too small for percentile or long-run rate claims.
- A different region/model/service tier may behave differently; none was tested here.
- The candidate can still reduce duplicate multimodal input and negative-control calls even though
  this investigation does not justify production adoption for latency.
- Provider-side tail latency remains a real runtime risk for both the current and fact-reuse shapes.

## Closure and next candidate

This diagnostic line is closed. Do not perform N=4, change the model, add generic retry, or invent a
timeout merely to continue the investigation.

The next candidate Work Package is **adaptive retrieval live-measurement readiness**, currently
`AWAITING_APPROVAL`. PR #127 removed the hidden eight-resource ceiling in production code, but the
canonical six-case `terraformers-eval-v1` contract does not contain a case that requires more than
eight resource types. A later measurement task should therefore freeze a separate evaluation-only
`>8 resource` scenario before claiming that adaptive expansion solves the intended operating
problem. That next Work Package is not started by this document.
