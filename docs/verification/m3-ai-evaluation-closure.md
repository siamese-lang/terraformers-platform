# M3 AI Evaluation Baseline Closure

## Status

**PASS — M3 COMPLETE**

Closure reviewed against repository evidence after M3-5 baseline analysis.

Canonical baseline:

- workflow run: `36379633596`
- evaluation run: `m3-baseline-36379633596-1`
- dataset: `terraformers-eval-v1`
- corpus/index: `terraformers-reference-v3`
- generation model: `gemini-3.8-flash`
- embedding model: `gemini-embedding-001`
- configuration fingerprint:
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`

The GitHub workflow's final conclusion was failure only because a post-execution jq verifier used
invalid root-reference syntax. The six-case execution itself completed, the artifact was uploaded,
and the pod was cleaned up. PR #64 fixed the verifier and preserved the original run as canonical
evidence rather than rerunning to obtain a cleaner result.

## Evidence chain

M3 now has:

1. provider-neutral stage-provenance contract;
2. fixed six-case dataset with stable fixture hashes;
3. one reusable `EvaluationRunner`;
4. target GKE/Vertex/OpenSearch runtime;
5. immutable v3 corpus/index with 128 documents;
6. production Java serving-path smoke;
7. six-case live baseline;
8. reproducible metrics and failure taxonomy.

Primary evidence:

- `docs/evaluation/m3-evaluation-contract-v1.md`
- `evaluation/terraformers-eval-v1/`
- `evaluation/baselines/m3-live-baseline-status.json`
- `evaluation/baselines/m3-live-baseline-run-36379633596-summary.json`
- `evaluation/baselines/m3-live-baseline-metrics.json`
- `docs/evaluation/m3-live-baseline-analysis.md`

## Baseline conclusion

The dominant observed failure is not retrieval ranking or grounded generation.

Two of six cases stopped at:

`FACT_EXTRACTION / PROVIDER_RUNTIME`

Affected cases:

- `arch-vpc-three-tier`
- `arch-private-aoss`

The VPC case had passed the immediately preceding live serving smoke, so the baseline demonstrates
live provider/runtime variability rather than a deterministic bad fixture.

Where the pipeline reached later stages:

- expected classification matched 4/4;
- required project-decision retrieval coverage was 2/2, both at rank 1;
- required retrieval resource types were covered 4/4;
- required generated Terraform resource types were covered 5/5;
- checked forbidden generated resource types observed: 0/4;
- positive validation passed 2/2;
- ambiguous and non-architecture controls emitted no Terraform.

The second important finding is diagnostic loss: fact-extraction failures are reduced to
`IllegalStateException` in the evaluation record, so provider status/category and retry behavior
are not available for root-cause discrimination.

## Framework decision checkpoint

### LangGraph — DEFER

M3 does not show a need for graph/state-machine orchestration.

The observed pipeline is still linear and the first failure occurs inside one provider call. There
is no evidence of a need for checkpointed AI workflow state, branching repair states, multi-agent
coordination, or graph-level retry routing.

### LangChain — DEFER

M3 does not show a retrieval/model-integration abstraction gap.

The existing Java ports and OpenSearch/Vertex adapters successfully preserve retrieval provenance
and grounded generation where execution reaches those stages. Replacing them with LangChain would
not directly address the measured fact-extraction provider/runtime failure.

### Reranker — DEFER

The two executed positive retrieval cases each returned the required project-decision document at
rank 1 and covered all required retrieval resource types. No baseline evidence justifies adding a
reranker.

### Additional judge/model call — DEFER

The fixed dataset and deterministic structural expectations already expose the first failure class.
A judge model would add cost and variance without addressing the provider-runtime divergence.

### Persistent Python AI worker/service — DEFER

The M3 path is successfully exercised through the existing Spring/Java boundary. The measured
failure does not establish a need for a separate persistent AI service.

## M4 handoff

M4 must follow:

`baseline failure → root cause → minimal targeted change → same-dataset re-evaluation`

The first investigation target is:

**Vertex architecture-fact extraction provider/runtime reliability and diagnostics.**

Before changing behavior, M4 should preserve enough sanitized provider failure detail to distinguish
transient provider errors from response-format/application defects. Only then should a bounded retry
or backoff change be considered for demonstrably transient failures.

M4 must not start with:

- prompt rewriting;
- corpus expansion;
- retrieval top-K/ranking changes;
- reranker introduction;
- model replacement;
- LangChain/LangGraph adoption.

## Exit criteria review

| # | M3 exit criterion | Result |
|---|---|---|
| 1 | Fixed/versioned evaluation dataset exists | PASS |
| 2 | Stable input identity and stage-level expectations | PASS |
| 3 | One reusable runner executes the dataset | PASS |
| 4 | Machine-readable stage provenance per case | PASS |
| 5 | Retrieval references preserve source/score/authority metadata | PASS |
| 6 | First observable failure stage is distinguishable | PASS |
| 7 | Component and relationship extraction evaluated | PASS |
| 8 | Retrieval success/relevance/project-decision coverage evaluated | PASS |
| 9 | Unsupported/ungrounded generation evaluated | PASS |
| 10 | Terraform generation/validation outcome evaluated | PASS |
| 11 | Runtime/model/corpus/configuration identity preserved | PASS |
| 12 | Latency and available usage/cost data recorded without invention | PASS |
| 13 | Reproducible baseline report and failure taxonomy exist | PASS |
| 14 | Framework adoption remains evidence-gated | PASS |
| 15 | M4 receives evidence-backed failure class | PASS |

## Residual limitations

M3 intentionally does not fix the discovered failure.

Remaining evidence gaps handed forward:

- provider failure trace lacks sanitized provider status/category and retry metadata;
- input token counts and provider-reported cost are unavailable;
- a one-case pre-baseline smoke observed a validator false-positive candidate around the phrase
  `Placeholder EC2 instance`, but this was not reproduced in the canonical six-case baseline.

These do not prevent M3 closure.

## Closure decision

M3 is complete.

The next repository task is to create the active M4 — AI Targeted Improvement plan. M4 must target
the fact-extraction provider/runtime failure class first and use the same fixed dataset and
configuration for before/after comparison.
