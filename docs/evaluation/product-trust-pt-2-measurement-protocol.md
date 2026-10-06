# PT-2 frozen realistic measurement procedure

Procedure `pt2-realistic-baseline-v2` is frozen before any PT-2 upload, inference or outcome inspection.
It supersedes v1 (`a16d255b58c128427b6c84465046695788553baf3a0125cbe572c74b384c2aae`),
which was rejected before execution by [independent review](https://github.com/siamese-lang/terraformers-platform/pull/244#issuecomment-6017268972).
The human-authorized correction addresses only that review's same-job observability and evidence-handoff blockers.
It consumes no autonomous repair. No baseline has started.

The execution base remains `3caae454661d21d84c3e469a7d76aff5633d5b26`, read once at activation.
USER approved `LIVE_REALISTIC_BASELINE` and the bounded existing protected workflow extension.
Independent acceptance and explicit merge approval are required before dispatch; neither CI success
nor acceptance grants merge authority. PT-3 implementation and teardown remain unauthorized.

## Immutable inputs and runtime

Use `evaluation/terraformers-realistic-v1`, revision **3**, identity
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`.
Verify the identity itself, all 26 pinned files, ten fixture checksums, external USER freeze binding,
live approval and this procedure's SHA before inference and each fixture again before submission.
Historical unapproved markers in pinned pre-approval snapshots are immutable history. Submit only
the original PNG and a neutral project name; never submit truth, provenance or expected labels.

Reuse `.github/workflows/gcp-target-evaluation-baseline.yml`, main-only `workflow_dispatch`,
`gcp-target-apply`, `google-github-actions/auth@v3`, existing WIF/apply service account, retained
`terraformers-target` GKE namespace, backend KSA and OpenSearch. No Codex GCP credential.
Scope `pt2-realistic-baseline`, confirmation `RUN_PT2_REALISTIC_BASELINE`, workflow attempt **1**.
An immutable retained backend image and its full embedded source SHA must be supplied. Prove all
backend main source, POM and Dockerfile equal the trusted dispatch source. Record dispatch SHA
separately; the approved merge checkpoint does not rebind the execution base. Unrelated main drift
requires a stop; before each upload remote main must equal dispatch SHA.

Read-only preflight checks the single ready retained backend, effective configuration, Terraform
1.8.5/AWS provider 5.100.0, generation `gemini-3.8-flash`, embedding `gemini-embedding-2`/1536,
REQUIRED retrieval, top K 8, production evidence budget 16 and output limit 8192. Corpus must be
`terraformers-reference-v4`, 5,395 documents, checksum
`da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a`.
Verify existing IAM/Kubernetes permissions; never grant missing permissions. Abort on mismatch,
without rollout, backend configuration change, reindexing or substitution.

The existing ephemeral evaluation pod is reused only for substrate/toolchain checks; it makes no
model call. PT-2 skips standalone Java build/bundle/evaluation/scoring. Frozen inputs are included
in the evidence bundle on the runner, not passed to another evaluator. All previous PT-2-only
standalone evaluator changes are restored to the execution base.

## One production job per input

The primary measurement is the proven A7-7 authenticated production path:

`POST /api/upload → persisted AnalysisJob → GET exact job → project and Terraform readback`.

Reuse the existing A7-7 portable JWT/JWKS fixture via the shared helper. Require an empty placeholder
JWKS before mutation; use the existing issuer/client and short-lived run-specific identity. Only the
JWKS fixture deployment restarts. No backend rollout, IdP/IAM expansion or new credential route.
Restore the placeholder even after partial preparation, delete temporary token/private-key files,
and never put them or Authorization headers in an artifact. No public ingress or browser is started.

Submit sequentially in frozen order:

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

One upload attempt per input, at most one accepted job, zero outer submission retries and zero
standalone model invocations. No nondeterminism repeats. Existing production internal provider/job
retries, MAX_TOKENS compact fallback and bounded closure/repair are unchanged. Record observable
attempt/retry logs; a single accepted job does not imply a single provider RPC.

Flush exclusive STARTED ledger evidence before POST. Capture request timestamp, HTTP acceptance
response/receipt time, project/job/source identities and server timestamps. Validate ownership-path
readback identities. Poll only that accepted job every five seconds for up to 420 seconds from
acceptance receipt; HTTP reads have 10-second connect/20-second total bounds (25-second subprocess
bound). Read failures may be polled again within this deadline; they never cause another upload.
The last read may exceed the deadline by its bounded transport duration. Persist every poll.

Capture SUCCEEDED or FAILED naturally and continue after terminal failures. Preserve API rejection
as admission failure, not successful classification. Lost/malformed/duplicate acceptance, auth
failure, identity mismatch or main drift stops the batch without resubmission. A job not terminal
by the observation deadline is **censored**, not declared FAILED; stop before another upload, retain
the accepted job, and do not cancel/delete it. Unstarted cases remain NOT_RUN. Existing attempt
directories cannot be resumed/overwritten by an inference run. Never rerun the workflow/batch to
replace failures. Later read-only recovery of that exact job is permitted with separately labeled
observation times; it cannot replace the original censored sample.

## Same-job evidence and limits

For each accepted job preserve:

- Full terminal AnalysisJob API response: summary, components, relationships, warnings, failure
  reason, result identity and persisted `evidence-quality-v1` snapshot, including reasons/boundary.
- Project API terminal semantics and `latestAnalysisJobId`; Terraform readback identity and full
  `main.tf` (or actual HTTP failure). Require result key agreement with the same terminal job.
- Acceptance/terminal UTC receipt timestamps and monotonic elapsed time; last nonterminal receipt.
- Backend log lines correlated by that exact `analysisJobId`, including validation, reference
  retrieval counts/corpus, retries, terminal failures and existing stage timing where emitted.
  Logs are bounded to 5,000 recent lines since submission; gaps are explicitly NOT_OBSERVED.
- Before/after Prometheus snapshots. These are cumulative context, not per-job timings or token
  counts unless exclusivity is independently established. Correlated stage logs are primary.
- Frozen truth worksheet and unchanged source copies of ProjectDetailPage/MyProjectsPage status
  mappings, bound by dispatch source and artifact hashes.

Project/API statuses are actual observations. Their current source-bound UI labels establish the
presentation contract, not a browser execution claim (final browser flow remains PT-6). Backend
EVIDENCE_BACKED claims and API-driven completion presentation claims are separate columns.
Backend claim predicate: same job SUCCEEDED, technical PASS, quality EVIDENCE_BACKED.
Presentation claim: same project's API reports SUCCEEDED, which current client maps to completion.
A completion label alone is not renamed an evidence-backed promise.

No second inference is permitted to recover richer trace. Raw extracted facts, ranked hits/document
IDs, initial draft/repair details and internal fact/retrieval/generation timing are NOT_OBSERVED when
not emitted by this job's existing APIs/logs. Do not fabricate EvaluationTrace fields, feed invented
traces to calibration, or attribute downstream wrong output to an unseen upstream stage. Existing
scorer/evaluator policies remain unchanged; semantic inspection uses the original frozen truth.

## Frozen assessment rules

After the single batch, derive a per-case table and aggregate analysis from these exact artifacts.
Do not dispatch another model, tune truth/aliases/thresholds or replace failures during analysis.
Every finding cites case/job ID, artifact path and observed field or HCL/log lines.

| Dimension | Assessment |
| --- | --- |
| Classification | Explicit observed classification if available; otherwise generated architecture vs explicit input rejection. Runtime/provider failure is UNKNOWN. Generic rejection cannot prove AMBIGUOUS vs NON_ARCHITECTURE distinction. |
| Components | Frozen required entities recovered / required entities, plus invented/forbidden entities. Use persisted detected components and HCL; raw initial facts may be unobserved. |
| Relationships | Frozen required directed semantic edges recovered / required edges; inspect actual HCL references/behavior and reported relationships, forbidden edges and logical cardinality. |
| Resource intent | Required resource types and their frozen role/cardinality; type presence alone cannot establish intent. Support-only acceptable resources are not required. |
| Retrieval/evidence | Persisted knowledge/quality reasons, correlated corpus/reference count and available authoritative substrate evidence. Rank/document coverage is UNKNOWN when absent. Evidence supporting wrong interpretation is separately recorded. |
| Terraform | Actual persisted technical status and same-job draft/schema/CLI validation log/failure evidence plus full persisted HCL. CLI fmt/init/validate is not plan/apply or deployment proof. Missing detail is NOT_OBSERVED, never invented PASS. |
| Trust/presentation | Actual terminal quality, project/API status and source-bound completion label. Report mismatches and missing quality independently; browser execution NOT_RUN. |
| False trusted success | Requires actual same-job backend evidence-backed or API-driven success presentation **and** independently established frozen semantic truth violation. Separate backend and presentation findings; pending semantic review is not zero. |
| Latency | Acceptance-response receipt to first terminal-response receipt, with last nonterminal/terminal observation interval and server timestamps separately. Polling/transport uncertainty is explicit; not exact server completion time. Censored samples are separate. |

Classification denominator is ten, split six architecture/four controls; report admission failures,
unknown classification and ambiguity discrimination separately. Positive semantic denominators are
original required truth for the six positives; report numerator/denominator for components, edges,
intent, forbidden interpretations and exact logical cardinality. Accept an alias only if the same
frozen entity/edge is demonstrated by observed output; record reasoning, not a post-outcome alias
policy. Report macro/micro values, missing/blocked counts and control hallucinations. Missing evidence
is neither a pass nor an invented zero score. For latency/stages report measured N, censored N,
median and range; N=10 cannot support a reliable p99/SLO. Cost/tokens remain unavailable if absent.

Distinguish first supported divergence: vision/input interpretation → retrieval/grounding →
generation/topology → Terraform technical → trust/status presentation. Multiple failures may coexist.
If initial interpretation/retrieval is unseen, record its cause UNKNOWN and cite the first observable
wrong summary/HCL or quality/status mismatch. Preserve individual failures and produce concrete
failure classes, evidence confidence, user impact and possible PT-3 decision requirements.

## Authoritative post-merge handoff and Goal continuation

This PR contains only procedure/workflow preparation and its deterministic correction evidence.
After independent acceptance and explicit human merge approval, resume the **same PT-2 Goal**:
verify merged source/history and frozen procedure, then dispatch the authorized single batch through
the existing protected workflow. No separate replacement, state-sync or normalization PR.

The GitHub Actions run plus immutable `upload-artifact@v4` artifact is authoritative live evidence.
Bind run ID, attempt, dispatch SHA, retained image/source, execution base, dataset identity, procedure
SHA, artifact ID/archive digest and SHA-256 file-inventory identity. Upload raw/partial failures with
90-day retention even on workflow failure. Before expiry, preserve required raw evidence and derived
per-case/aggregate analysis in the next substantive PT-3 or program-closure PR; never claim deleted
artifacts remain retrievable. A fresh structured `[PRODUCT_TRUST_REVIEW:v1]` on the same preparation
PR must bind the exact live run/artifact/inventory and derived analysis, validate original acceptance
and state whether PT-2 is accepted or incomplete. Workflow green is not acceptance.

PT-2 completion is durable only after that independent result acceptance. Fold its result, evidence
binding and phase completion into the next substantive PT-3 or closure PR permitted by the Program
DAG, not a dedicated post-run state PR. If a decision gate prevents implementation, prepare the
repository decision/evidence deliverable within the next substantive Work Package and stop there.
GitHub is authoritative for transient PR/branch lifecycle; durable program state stores only phase,
approval, execution base, procedure, corrections and evidence. Do not register this PR as active state.

On every start/resume, inspect open/current Work Package PR conversation/review history first.
Latest structured review governs its reviewed head; inspect later commits before editing, preserve
same branch/base, address unresolved blockers only, distinguish human correction from autonomous
repair and return to the original gate. After accepted+approved merge, autonomously choose the next
DAG-eligible approved action without requiring repetitive continue prompts. Review acceptance never
bypasses decision, live cost/security/IAM, destructive, budget or merge gates. Never start PT-3 on CI
success or preparation acceptance; never self-approve truth, merge or tear down the retained runtime.
