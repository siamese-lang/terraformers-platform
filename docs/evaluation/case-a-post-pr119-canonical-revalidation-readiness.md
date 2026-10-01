# Case A Post-PR119 Canonical N=3 Revalidation Readiness

## Status

**COMPLETE — CANONICAL N=3 PASS; HOLDOUT PASS**

This readiness checkpoint freezes the next Case A validation step after the PR #119 lifecycle
correction. It does not change production code, the evaluation workflow, the canonical dataset,
the holdout, corpus, model, prompt, retrieval behavior, validator, infrastructure, or IAM.

The frozen execution has completed. Canonical runs `36803174654`, `36803781744`, and
`36804600570` all used source `32d62e21821ed303555ade5e26b03cb669db94ef`, workflow attempt 1,
and the same configuration fingerprint. Every canonical hard gate passed. The separately approved
frozen holdout then ran once as `36805478708` on the same source/configuration and also passed.

The authoritative final interpretation is
[Case A Final Closure](case-a-final-closure.md).

## Why this checkpoint exists

The last recorded canonical N=3 after the PROJECT_DECISION ordering correction used runs:

- `36678421069`
- `36678439743`
- `36679694645`

on source:

`edd291a0e5b00e3e37d2b35b52531b3acd07a835`

That N=3 produced:

- VPC project-decision coverage: `3/3`
- VPC required-resource coverage: `4/4 × 3`
- grounding gaps: `0/12`
- positive validation: `12/12`
- negative controls: `5/6`
- holdout: `NOT RUN`

The single hard-gate failure was `non-architecture-deployment-dashboard`. Retrieval successfully
returned zero usable references, but REQUIRED grounding was enforced before input classification,
so the negative control could not reach its classification path.

PR #119 corrected that lifecycle boundary:

- successful empty retrieval may continue to classification;
- REQUIRED grounding still fails closed for an architecture input after classification;
- NON_ARCHITECTURE_IMAGE and AMBIGUOUS retain their existing rejection semantics;
- retrieval execution failure is still a failure and is not converted into an empty-success path.

A fresh canonical N=3 after that correction is not yet recorded.

## Reuse instead of new validation infrastructure

No new workflow or verifier is needed.

Use the existing workflow:

`.github/workflows/gcp-target-evaluation-baseline.yml`

with:

- scope: `case-a-full-baseline`
- confirmation: `RUN_CASE_A_BASELINE`

Each dispatch already produces:

- `case-a-baseline-<github_run_id>`
- `case-a-grounding-report-<github_run_id>`

The existing deterministic aggregator is:

`com.terraformers.modernization.evaluation.CaseAMeasurementLauncher --mode=aggregate`

The three grounding reports can therefore be aggregated after download without adding another
repository-owned verifier.

## Live source identity

The live source SHA is **not** the readiness branch base SHA.

After this readiness PR merges, resolve the exact new `main` merge SHA and use that same SHA for all
three canonical dispatches. If `main` changes before all three approved dispatches are completed,
stop and re-freeze the source identity rather than mixing runs from different code states.

## Frozen canonical configuration

Every run must report:

- dataset: `terraformers-eval-v1`
- six traces
- corpus: `terraformers-reference-v3`
- provider knowledge: `5.100.0`
- analysis provider: `vertex`
- embedding provider: `vertex`
- retrieval mode: `REQUIRED`
- top-K: `8`
- generation model: `gemini-3.8-flash`
- embedding model: `gemini-embedding-001`
- fact-extraction thinking: `LOW`
- fact-extraction max output tokens: `800`
- generation max output tokens: `8192`

Do not hard-code the old M3 configuration fingerprint. PR #127 and later work changed the current
configuration surface. Instead require all three canonical runs to report the same non-empty
`sha256:` configuration fingerprint and otherwise comparable configuration identity.

## Exact run count

Run **exactly N=3** independent workflow dispatches.

- each must be workflow attempt 1;
- do not add N=4 to chase a pass;
- do not rerun a model/evaluation hard-gate failure until lucky;
- a pre-evaluation infrastructure/identity failure is a blocker to classify, not an invisible sample
  to discard and replace.

The purpose of N=3 is to test consistency of the corrected canonical path, not to select the best
sample.

## Frozen hard gates

### 1. Identity and comparability

Across all three runs:

- exact same merged source SHA;
- exact same canonical dataset;
- exact same configuration identity;
- exact same non-empty configuration fingerprint;
- exactly six traces per run;
- both raw and grounding-report artifacts present.

### 2. Fact extraction

Across N=3:

- fact extraction PASS: `18/18`.

### 3. Retrieval

Across N=3:

- retrieval PASS: `18/18`.

A successful empty retrieval for a negative-control input still counts as retrieval PASS. PR #119
changes what happens after that successful empty retrieval; it does not redefine provider/search
failure as success.

### 4. VPC project-decision consistency

For `arch-vpc-three-tier` in every run:

- `projectDecisionCoverage = 1/1`;
- `tfref-v2-sg-relations` present.

Required frequency:

- `3/3`.

### 5. VPC required-resource coverage

For `arch-vpc-three-tier` in every run, exact required-resource coverage remains `4/4` for:

- `aws_vpc`
- `aws_lb`
- `aws_db_instance`
- `aws_security_group`

Required frequency:

- `4/4 × 3`.

### 6. Grounding gaps

Across the 12 applicable architecture-case observations:

- grounding gaps: `0/12`.

### 7. Positive Terraform validation

Across the four architecture cases × three runs:

- deterministic Terraform validation: `12/12 PASS`.

### 8. Negative-control classification

Across both canonical negative controls × three runs:

- correct negative-control outcome: `6/6`.

This is the gate directly relevant to the PR #119 lifecycle correction.

### 9. First divergence

The accepted N=3 must contain no new first-divergence failure.

If a failure appears, preserve and classify it. Do not reinterpret it as a pass merely because the
other two runs succeed.

## Aggregate interpretation

The existing multi-run aggregator should be used for the three grounding reports. It provides:

- run count;
- compatible configuration identity;
- source run IDs;
- grounding-gap frequency;
- grounding-gap-with-valid-output frequency;
- per-case coverage histories;
- bounded latency statistics already defined by the existing tool.

The aggregate is evidence, not an excuse to average away a hard-gate failure. Any per-run hard-gate
failure above means canonical N=3 does not pass.

## Holdout gate

The frozen `terraformers-eval-holdout-v1` is **not authorized by this readiness task**.

Only if every canonical N=3 hard gate passes may the holdout become the next candidate checkpoint.
That holdout must:

- run once;
- use the exact then-approved main SHA;
- use the existing `case-a-holdout` workflow scope;
- receive separate user approval.

Do not run the holdout automatically after the third canonical run.

## Stopping rules

Stop and assess before continuing if:

- `main` drifts between approved runs;
- any artifact is missing;
- configuration identity differs across runs;
- a workflow fails before producing a comparable evaluation;
- a canonical hard gate fails;
- satisfying the gate would require changing corpus, model, prompt, retrieval, validator, dataset,
  workflow, infrastructure, or IAM.

No tuning is authorized inside this measurement sequence.

## Relationship to completed side investigations

This canonical revalidation does not reopen:

- adaptive >8-resource retrieval measurement — **CLOSED / PASS**;
- FACT_REUSE latency investigation — **CLOSED with production adoption HOLD**.

Their artifacts remain evidence but are not additional canonical gates.

## Completion boundary

This checkpoint is **COMPLETE**.

Final canonical result:

- fact extraction: `18/18 PASS`
- retrieval: `18/18 PASS`
- VPC decision coverage: `3/3`
- VPC required-resource coverage: `4/4 × 3`
- grounding gaps: `0/12`
- positive Terraform validation: `12/12 PASS`
- negative controls: `6/6 correct`
- new first divergence: `0`

Frozen holdout run `36805478708` also passed: fact extraction `4/4`, retrieval `4/4`,
positive validation `2/2`, negative controls `2/2`, grounding gaps `0`, first divergence `0`.

Do not repeat these runs for another successful sample. See
[Case A Final Closure](case-a-final-closure.md).
