# PT-2 realistic baseline preparation — human-review correction 1

**HUMAN_REQUIRED: MERGE_CHECKPOINT. Corrected head needs independent review. No live baseline has run.**

[Review 6017268972](https://github.com/siamese-lang/terraformers-platform/pull/244#issuecomment-6017268972)
rejected reviewed head `436bbcc6728d10adf05f2bcfa07550bb45ed8ca7` on two acceptance blockers.
This human-authorized iteration addresses those blockers on the same PR branch and preserves
execution base `3caae454661d21d84c3e469a7d76aff5633d5b26`. It is not an autonomous repair:
PT-2 autonomous repairs remain 0; historical program repairs remain 1.

USER approved the live baseline and existing protected workflow extension. The main-only workflow
requires independent correction acceptance and explicit merge approval before dispatch. No merge,
workflow dispatch, model/GCP call, credential installation, backend rollout or teardown occurred.

The [Work Package](../../.agents/work-packages/product-trust-pt-2-current-system-realistic-live-baseline-v1.yml)
and [measurement procedure v2](product-trust-pt-2-measurement-protocol.md) freeze the corrected path
before outcomes. Procedure SHA-256: `d8e615ef3fdc26de9be5e34b922da6c06055ca60e4b4e5b004775301d5479f90`.
Unexecuted v1 (`a16d255b58c128427b6c84465046695788553baf3a0125cbe572c74b384c2aae`)
is explicitly superseded, not relabeled as the same procedure.
Dataset revision 3 / identity
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`, all 26 pinned files
and ten input images remain byte-for-byte unchanged. PT-1 completion, USER freeze and repair history
are preserved; no transient PR/branch lifecycle is registered in program state.

## BLOCKER-1 correction: observe the same production job

The primary per-case call is now the existing authenticated `POST /api/upload` production path,
followed by read-only polling of that exact AnalysisJob. Reuse the proven A7-7 JWT/JWKS mechanism
through a shared helper, retaining existing issuer/client, placeholder guard and WIF/GKE route.
Only the owned JWKS fixture is temporarily installed/restored; backend configuration is unchanged.
The shared extraction also replaces the existing A7-7 prepare/restore blocks, avoiding duplicated
auth machinery. No new workflow, credential route, production IdP or IAM permission is added.

One upload attempt per input, at most one accepted job, zero resubmission and zero standalone
model invocation. Capture actual acceptance receipt/response and project/job/source identities,
all polls, terminal job/persisted quality, project/API semantics, full Terraform readback, terminal
receipt and same-job correlated logs/stages. Preserve raw HTTP failures and censored observations.
Read-only polling retries never submit another job. Lost/malformed acceptance, identity/auth failure
or main drift stops without resubmission. A 420-second observation deadline retains the job and
stops before another upload; it does not cancel or fabricate FAILED status.

Actual persisted quality and API completion presentation are separate claims. Frozen semantic truth
review must establish a violation on that same job before any actual false-trusted-success finding.
Pending semantic assessment is not zero false trusted successes. Client source mappings are captured
as presentation-contract evidence; no browser execution is claimed. Polling/transport uncertainty
is explicit in accepted-response-to-terminal receipt latency. Existing production internal retries,
closure/repair and validation stay unchanged.

Unemitted initial facts, ranked retrieval hits, repair trace and internal stage timings remain
NOT_OBSERVED. No additional standalone inference recovers them. Previous PT-2-only evaluator/main
and test changes in four files are restored exactly to execution base, so the final PR has no backend
or scorer diff against that base. Existing synthetic workflow scopes retain their evaluator behavior.

Read-only preflight pins the retained image/source/configuration, auth/storage route, existing
Terraform/provider and broad-v4 corpus. PT-2 skips standalone Java build/bundle/scoring and backend
ServiceAccount apply. Its ephemeral pod is only a substrate probe; cleanup deletes only this run's
pod and ephemeral auth files and restores its owned placeholder JWKS. Missing existing permissions
or mismatches stop instead of granting IAM or rolling out a backend.

## BLOCKER-2 correction: immutable evidence handoff

This same PR contains preparation and deterministic correction evidence. After independently
accepted and explicitly approved merge, the protected Actions run plus immutable uploaded artifact
is authoritative baseline evidence. Run summary binds run/attempt, dispatch SHA, retained image/source,
execution base, dataset/procedure SHA, artifact ID/digest and file-inventory SHA. Raw and partial
artifacts upload on failure with 90-day retention; preserve required evidence before expiry.

Structured independent review on the same PR must bind the exact run/artifact/inventory and derived
per-case/aggregate analysis and assess original acceptance. No dedicated post-run state-sync or
normalization PR. Accepted PT-2 completion and evidence are folded into the next substantive PT-3
or program-closure PR chosen from the DAG and measured result. Neither CI nor preparation acceptance
completes PT-2. USER's continuing Goal authorization removes repetitive continue prompts after
accepted/approved transitions; all actual decision, cost/security/IAM, destructive and merge gates
remain binding.

## Unstarted per-case ledger

All fidelity, retrieval, Terraform, trust and latency outcomes remain **NOT_MEASURED**. These are
unstarted inputs, not model failures or zero scores.

| Case | Frozen classification | Accepted jobs | Outcome |
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

## Proportional deterministic validation

- Offline backend: **27 passed, 0 failures/errors/skips**. Dataset loader 4, upload controller 6,
  job controller integration 1, persisted quality response 1, project metadata controller 11,
  Terraform draft controller 4. Backend source compiled. Test fixtures use mocks/H2; no retained
  runtime, MariaDB service or model is called.
- Python: **16 passed**, using fake HTTP/clock/logs and mocked Kubernetes only. Covers one upload
  per case, same-job terminal/quality/HCL/latency evidence, natural terminal failures, read-only
  poll recovery, censored deadlines, lost/malformed acceptance, identity/auth failure, drift,
  fixture/procedure tampering, private token handling and immutable inventory. Shared auth tests
  verify non-placeholder refusal, partial-apply cleanup and TTL/fixture-only restore.
- Two workflow YAML files, **85 embedded shell blocks (37 + 48)** and shared Bash helper syntax:
  **PASS**. `actionlint 1.7.12 -shellcheck=''`: **PASS**; external ShellCheck not run.
- Frozen identity/26 pinned files/ten fixtures/procedure SHA: **PASS**.
- Entire backend and evaluation directories match execution base: **PASS**.
- PT-1 freeze/completion/history, historical completed state update, exact PT-2 activation/base,
  null transient fields, autonomous repair counts and zero-run state: **PASS**.
- Allowed-path audit and `git diff --check`: **PASS**.

[Machine-readable evidence](../evidence/product-trust-pt-2/preparation-validation.json) preserves
superseded v1 validation separately. No full backend suite/package, live baseline result, independent
acceptance or PT-3 failure-class claim is made. Remaining gate: corrected-head independent review,
then explicit merge approval. PT-3 implementation and retained-runtime teardown remain unauthorized.
