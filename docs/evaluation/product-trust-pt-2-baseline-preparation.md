# PT-2 realistic baseline preparation

**HUMAN_REQUIRED: MERGE_CHECKPOINT. No PT-2 live baseline has run.**

USER approved `LIVE_REALISTIC_BASELINE` and directed reuse of the existing protected evaluation
workflow/WIF path. Execution base, bound once: `3caae454661d21d84c3e469a7d76aff5633d5b26`.
The existing workflow is main-only; this bounded scope cannot execute from the preparation branch.
No merge, workflow dispatch, model call, GCP credential installation, rollout or teardown occurred.

The [Work Package](../../.agents/work-packages/product-trust-pt-2-current-system-realistic-live-baseline-v1.yml)
and [frozen procedure](product-trust-pt-2-measurement-protocol.md) define the measurement before any
new outcomes. Procedure SHA-256: `a16d255b58c128427b6c84465046695788553baf3a0125cbe572c74b384c2aae`.
Dataset `terraformers-realistic-v1`, revision 3, identity
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014` and all 26 pinned files are unchanged.

## Bounded execution preparation

The existing `.github/workflows/gcp-target-evaluation-baseline.yml` gains only the
`pt2-realistic-baseline` scope (`RUN_PT2_REALISTIC_BASELINE`). It reuses the protected environment,
WIF, GKE credentials, backend KSA, existing OpenSearch, immutable backend image toolchain,
ephemeral pod and artifact mechanism. Production prompts/models/RAG/quality and existing synthetic
scope behavior are preserved. No new workflow or infrastructure is added.

Read-only preflight pins retained backend image/source/configuration and production source
equivalence, Terraform/provider tooling and broad-v4 corpus identity. PT-2 does not apply the
existing ServiceAccount manifest or replace an already-existing evaluation pod. Cleanup is limited
to a pod created by this run.

Each of the ten cases has one sequential launcher invocation, with pod-side 420-second deadline,
zero outer retry, exclusive attempt directories, flushed STARTED evidence, raw output/diagnostics
and immediate result capture. Main drift stops before another case. A transport loss stops the
batch without an ambiguous resubmission. Raw and partial artifacts upload even when execution or
scoring fails. Deterministic report/calibration reuse existing evaluators; selected document content
is fetched by ID without another vector query/model call.

One measurement mismatch was found before inference: the historical launcher initially requests
8 evidence items, while current production requests 16 with search top K 8. Only PT-2's new explicit
setting uses 16; raw retrieval evidence records the actual query budget. No production retriever,
evidence selector, scorer or existing synthetic scope is changed.

## Per-case execution ledger at this checkpoint

All observed fidelity, retrieval, Terraform, trust and latency dimensions are **NOT_MEASURED**.
These are unstarted cases, not failed model results or zero-valued baseline scores.

| Case | Frozen classification | Case invocations | Outcome |
| --- | --- | ---: | --- |
| pt1-01-serverless-portal | ARCHITECTURE_DIAGRAM | 0 | NOT_RUN |
| pt1-02-order-fanout | ARCHITECTURE_DIAGRAM | 0 | NOT_RUN |
| pt1-03-parallel-lookup | ARCHITECTURE_DIAGRAM | 0 | NOT_RUN |
| pt1-04-analytics-catalog | ARCHITECTURE_DIAGRAM | 0 | NOT_RUN |
| pt1-05-private-web-fleet | ARCHITECTURE_DIAGRAM | 0 | NOT_RUN |
| pt1-06-thumbnail-pipeline | ARCHITECTURE_DIAGRAM | 0 | NOT_RUN |
| pt1-07-unresolved-design | AMBIGUOUS | 0 | NOT_RUN |
| pt1-08-partial-export | AMBIGUOUS | 0 | NOT_RUN |
| pt1-09-sprint-board | NON_ARCHITECTURE_IMAGE | 0 | NOT_RUN |
| pt1-10-workshop-table | NON_ARCHITECTURE_IMAGE | 0 | NOT_RUN |

## Acceptance limitations retained for independent review

The existing launcher has rich pipeline traces, but creates no Spring context or persisted
AnalysisJob. Actual persisted/API/UI trust, actual false trusted success and accepted-to-terminal
latency therefore remain unmeasured. Source-based presentation/quality projections and the existing
calibration `falseGreen` metric must not be relabeled as actual runtime trusted-success findings.
This is an original acceptance blocker; no waiver or phase-completion claim is recorded.

The existing evaluator also stops validation when observed classification conflicts with frozen
expected classification. That `NOT_RUN` is not evidence of technically invalid HCL. Preserve the
raw HCL and distinguish evaluator control flow from actual production finalization. Pipeline wall
duration includes launcher/JVM startup and kubectl transport, and is not durable job latency.

The required workflow merge also means a merged preparation PR cannot accept a later evidence
diff. The original one-PR completion requirement and the post-merge evidence handoff must be
resolved at the human checkpoint. This task creates one preparation PR and grants no authority
for a replacement or state-sync/normalization PR.

## Deterministic validation

- Offline backend tests: **35 passed; 0 failures/errors/skips**. `EvaluationDatasetLoaderTest` 4,
  `LiveEvaluationLauncherTest` 10, `ProductionEquivalentClosureEvaluationTest` 7,
  `EvaluationRunnerTest` 14. The backend source compiled during these focused runs.
- Python orchestration contracts: **8 passed**, using a fake pod only. Coverage includes
  immutable fixture/procedure guards, exactly ten invocations, no retry after timeout/transport
  loss, preservation of failed traces, existing attempt refusal and main-drift stop.
- Frozen identity/26-file/ten-fixture checksum audit: **PASS**.
- Workflow YAML and **35** embedded Bash syntax checks: **PASS**.
- `actionlint 1.7.12 -shellcheck=''`: **PASS**. Bash syntax was checked separately; external
  ShellCheck was not run.
- Durable PT-1 freeze/completion/repair and completed historical state-update preservation,
  null transient PR fields, and bound PT-2 approval/zero-run state: **PASS**.
- Scope audit and `git diff --check`: **PASS**.

Machine-readable preparation evidence:
[preparation-validation.json](../evidence/product-trust-pt-2/preparation-validation.json).
No baseline quality result, PT-3 failure class, independent acceptance, or full backend suite/package
claim is made from these proportional preparation checks. PT-3 and retained-runtime teardown remain
unauthorized. Resume the same Goal after the required human checkpoint; inspect structured PR
feedback first and preserve the bound execution base.
