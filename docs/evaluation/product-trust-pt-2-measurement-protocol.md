# PT-2 frozen realistic measurement procedure

Procedure `pt2-realistic-baseline-v1` is frozen before any PT-2 inference or outcome inspection.
The execution base is `3caae454661d21d84c3e469a7d76aff5633d5b26`, read once at activation.
USER approved `LIVE_REALISTIC_BASELINE` and the bounded addition to the existing protected
evaluation workflow. Merge remains a separate human checkpoint. PT-3 and teardown are unauthorized.

## Immutable inputs and system

Use `evaluation/terraformers-realistic-v1`, revision **3**, identity
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`.
Verify the identity file itself, all 26 pinned files, and every manifest fixture before inference.
Truth authority is the external PT-1 USER freeze binding; historical `NOT_APPROVED` fields in pinned
pre-approval snapshots must not be rewritten. Do not pass truth/provenance/expected labels to Vertex.

Reuse `.github/workflows/gcp-target-evaluation-baseline.yml` through main-only `workflow_dispatch`,
`gcp-target-apply`, existing WIF and backend KSA, retained GKE/OpenSearch, ephemeral evaluation pod,
and artifact upload. The confirmation is `RUN_PT2_REALISTIC_BASELINE`. Pin the immutable image and
embedded source to the retained backend and prove production source equivalence with dispatch main.
Check the retained effective configuration against the unchanged broad-v4 launcher profile:
Vertex generation `gemini-3.8-flash`, embedding `gemini-embedding-2`/1536, REQUIRED retrieval,
`terraformers-reference-v4`, 5,395 documents, corpus checksum
`da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a`, top K 8,
existing evidence budget 16, AWS 5.100.0, Terraform 1.8.5, generation maximum 8,192 tokens, and
the source defaults for fact/generation thinking. Abort on mismatch. Do not repair a mismatch by
rollout, configuration change, reindexing, model substitution, or IAM expansion.

The existing launcher's initial query historically limits evidence to 8, whereas the unchanged
production `VertexAnalysisProvider` requests its evidence budget of 16 (search top K remains 8).
PT-2 alone supplies `EVALUATION_INITIAL_EVIDENCE_BUDGET=16`; the raw query records that limit.
Historical scopes, scorers and production retrieval stay unchanged. Record this separate measurement
setting alongside the existing configuration fingerprint, which does not encode evidence budget.

The merged dispatch source is recorded separately from the bound execution base. An explicitly
approved merge of this preparation is the allowed checkpoint transition; unrelated main drift
requires a human decision. Never silently rebind the Work Package to dispatch main.

## Runs and failure preservation

Run the existing `LiveEvaluationLauncher` broad-v4 production-equivalent path in SINGLE mode,
sequentially, once per case, in this frozen order:

1. `pt1-01-serverless-portal`
2. `pt1-02-order-fanout`
3. `pt1-03-parallel-lookup`
4. `pt1-04-analytics-catalog`
5. `pt1-05-private-web-fleet`
6. `pt1-06-thumbnail-pipeline`
7. `pt1-07-unresolved-design`
8. `pt1-08-partial-export`
9. `pt1-09-sprint-board`
10. `pt1-10-workshop-table`

No nondeterminism repeats are declared. One case invocation is not one provider request: preserve
the existing bounded fact/generation behavior, including initial MAX_TOKENS compact fallback and
at most one generated-resource closure/semantic repair. Do not add any outer provider retry.

Before each invocation, flush an immutable STARTED ledger entry. The pod-side command has a
420-second deadline and a 10-second kill grace, so loss of the runner connection cannot leave an
unbounded Java process. Capture stdout, stderr, exit status, UTC start/end, monotonic wall duration,
raw trace when produced, and diagnostic artifact absence. A timeout is censored operational
evidence, not a fabricated stage trace. Preserve each result before starting the next case.
Continue to later cases after a recorded case failure; stop on identity/configuration inconsistency.
Never overwrite an attempt directory or rerun a workflow after a case has started without a new
explicit human instruction. If interrupted, unstarted cases remain NOT_RUN. Read-only evidence
collection and deterministic scoring may be resumed without another model call.

## Evidence and assessment

The raw trace must retain extracted facts, query and ranked reference metadata, initial and final
drafts, generated resource types, closure/repair indicators, final reference selection, validation
diagnostics, earliest technical failure, and available stage times. Read selected OpenSearch
documents by ID after inference to preserve their exact content; this is evidence capture, not a
second retrieval or generation. Hash all artifact files in a final evidence inventory.

Reuse the unchanged `CaseAMeasurementLauncher` report and calibration modes. Exact-string scorer
results remain exact-string results. Separately inspect semantic truth against the frozen required,
acceptable and forbidden components, relationships and resource intent. Short labels/aliases count
only when the trace or HCL demonstrates the same frozen entity/edge; cite the supporting evidence.
Do not invent a new alias list or lower a threshold after seeing an outcome. Required logical
cardinality and relationship direction matter; support-only acceptable resources are not required.

For every case report these dimensions independently:

| Dimension | Frozen assessment rule |
| --- | --- |
| Classification | Record explicit generation classification when present. A provider/runtime failure is UNKNOWN, not correct rejection. A classifier's generic input rejection does not prove AMBIGUOUS vs NON_ARCHITECTURE discrimination. |
| Component fidelity | Required entities recovered / frozen required entities, plus forbidden or invented entities; separate initial facts from generated summary/HCL. |
| Relationship fidelity | Required directed semantic edges recovered / frozen required edges; identify forbidden edges and unsupported topology. |
| Resource intent | Required resource types plus their intended use; a resource-type list alone cannot prove intent or cardinality. |
| Evidence | Initial and closure/final hits, authority, provider/corpus identity, coverage of frozen intent and generated resources; evidence supporting wrong extracted facts is a separate failure. |
| Terraform | Existing draft/schema/CLI result and diagnostics; executable validation means fmt/init/validate with backend disabled, never plan/apply or deployability proof. |
| Trust | Actual persisted/API/UI evidence if available; otherwise NOT_MEASURED, separately from any source-based projection. |
| Latency | Observed pipeline wall time and existing stage durations; censor timeout cases and never rename pipeline duration as accepted-to-terminal. |

Classification denominator is all ten inputs, with a separate architecture/control split (6/4).
Semantic fidelity denominators use the six positives and their original required labels. Report
unobserved/blocked counts explicitly; a missing measurement is neither a pass nor an invented zero
score. Macro and micro summaries must show numerator/denominator, exact-string and semantic results
separately. Report stage-time sample counts, median and range; with N=10 do not claim a reliable p99
or an SLO. Provider token/cost fields may remain unavailable; never fabricate a billed cost.

Distinguish the first supported semantic divergence from the runner's first technical failure:
vision/input interpretation → retrieval/grounding → generation/topology → Terraform technical
failure → trust/status presentation. Multiple defects may coexist. If an upstream stage is not
observable, say UNKNOWN rather than assigning the downstream symptom to that stage.

## Trust and latency observability blocker

The existing launcher explicitly creates **no Spring application context**. It does not accept an
authenticated upload, create/persist an AnalysisJob, assess/persist its runtime quality, or execute a
browser. Its calibration reporter receives no runtime quality map by default. Therefore actual
persisted/user-visible trust, accepted-to-terminal latency, and actual false-trusted-success counts
are **NOT_MEASURED by this path**, even when all pipeline stages pass.

Source inspection of `AnalysisJobResponse`, the quality finalizer and current frontend completion
labels may explain a possible presentation gap, but it is a projection, not a runtime observation.
An actual false trusted success requires both an observed trusted/presented success and a violation
of frozen truth. A technically valid yet semantically wrong pipeline output is reported as a
potential false trusted success until its actual persisted/user-visible state is observed.

This limitation is an unresolved original acceptance requirement. It is not permission to weaken
PT-2, claim zero false trusted successes, call this a completed product baseline, or silently add
another inference per input. Independent review must resolve a same-inference observational path
before claiming those dimensions measured. Any procedure amendment must be frozen and reviewed
before inference; it cannot be chosen after outcomes. The present preparation stops at merge review
and does not treat an incomplete measurement as a completed baseline.

## Handoff and acceptance

Keep this Work Package, harness and evidence within this one PT-2 branch/Goal. This preparation
opens one PR. No state-sync or normalization PR. The original one-PR completion requirement remains
recorded: a merged preparation PR cannot receive a later evidence diff, so the evidence handoff
after the required main-only merge must be settled at that human checkpoint, not assumed to grant
a replacement PR. Program state records phase/base/procedure/run
evidence, not active GitHub PR/branch lifecycle. On resume inspect PR feedback first, verify the
authorized merge transition and frozen hashes. Baseline results require independent review, and the
observability blocker must be resolved before claiming PT-2 complete. CI green is not acceptance. Do not merge,
start PT-3, tune the system from results, or tear down the retained runtime.
