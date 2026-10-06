# PT-2 bounded continuation procedure

Procedure `pt2-realistic-continuation-v1` is a bounded correction to the already-executed
`pt2-realistic-baseline-v2`. It exists only because authoritative run `37480519016` produced
valid partial evidence: case 01 reached terminal success, case 02 was accepted once and remained
non-terminal beyond the frozen 420-second measurement window, and cases 03-10 were never submitted.

This procedure does not replace, reinterpret, or rerun those observations. It preserves the original
420-second latency censoring for case 02 and permits only read-only recovery of that exact accepted
job plus first submission of the previously NOT_RUN cases.

## Frozen prior evidence

Continuation is valid only when all of the following exact prior identities are present:

- baseline run: `37480519016`, attempt 1
- dispatch SHA: `c3e9ed4e854778dfb2eefbc0182ae53a008025bf`
- artifact ID: `11422455892`
- artifact archive digest: `sha256:499f31b4311737884a2b7d0fc0405d477be7e41a99e9969217570c91eb5ac561`
- artifact inventory SHA-256: `107d31a942dcbc23bbfaf1f81e7c1b9c6a2fac4224d5689edb216a0bd4378f3d`
- frozen dataset identity:
  `04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`
- case 01 job: `2337e6bd-1f70-4382-a515-b4cdb9cba7d9`, terminal `SUCCEEDED`
- case 02 job: `578c05b9-2905-4d60-8432-ce7593c96133`, project 6,
  original measurement `TERMINAL_NOT_OBSERVED` with `censoredObservationMs=424846`
- cases 03-10: `NOT_RUN`, zero upload attempts

The continuation workflow must verify GitHub artifact metadata, download that exact artifact, verify
the inventory and every file checksum, and fail closed on any mismatch. No regenerated or hand-built
summary may substitute for the prior artifact.

## Recovery before any new inference

Before a new upload, read only the exact case-02 AnalysisJob through the existing authenticated
production API path. Do not resubmit case 02 and do not change its original latency measurement.
Record recovery timestamps separately.

If the exact job is already terminal, preserve the terminal response, project readback, Terraform
readback when present, and newly available correlated logs. If it is still PENDING/RUNNING, poll that
same job only. If it cannot reach terminal within the bounded drain window, stop without uploading
case 03. An inaccessible or identity-mismatched job also stops the continuation.

The recovery observation may complete semantic/trust review for case 02, but its latency sample remains
`>420s` censored with the original 424846 ms observation. A later terminal timestamp must never replace
the original censored measurement.

## First and only submission of cases 03-10

Only these cases may be uploaded, in frozen order:

1. `pt1-03-parallel-lookup`
2. `pt1-04-analytics-catalog`
3. `pt1-05-private-web-fleet`
4. `pt1-06-thumbnail-pipeline`
5. `pt1-07-unresolved-design`
6. `pt1-08-partial-export`
7. `pt1-09-sprint-board`
8. `pt1-10-workshop-table`

For each case, retain the v2 contracts: one upload attempt, at most one accepted AnalysisJob, no outer
submission retry, no standalone evaluator/model invocation, immutable fixture bytes, current-main
drift check before POST, same-job polling/readback, and natural terminal failures retained.

The measurement window remains 420 seconds from acceptance. If a job is not terminal by that deadline,
write the latency result as censored and freeze that value. Then continue read-only polling of the same
accepted job solely to drain it before submitting another case. The drain window is bounded to 1200
seconds from acceptance. A drained terminal observation is recorded separately and cannot convert the
censored latency sample into an uncensored sample. If the same job is still non-terminal at the drain
bound, stop; later cases remain NOT_RUN.

This separation prevents one latency tail from erasing the remaining benchmark while also preventing
overlapping accepted jobs from contaminating queue/latency evidence.

## Runtime and authority boundaries

Reuse the exact retained production AnalysisJob path, GKE/OpenSearch substrate, WIF identity,
ephemeral JWT/JWKS fixture and immutable backend image/source checks already accepted for PT-2.
No product code, prompt/model, RAG, scorer, truth, IAM, infrastructure, backend rollout or runtime
configuration may be changed by this correction.

The original `pt2-realistic-baseline-v2` procedure and run artifact remain immutable evidence.
The continuation gets its own run ID, artifact ID/digest and inventory. Independent review must bind
both artifacts when deciding PT-2 completeness.

The existing USER `LIVE_REALISTIC_BASELINE` approval covers the originally approved ten frozen inputs;
this correction does not add inputs or repeat an inference. Nevertheless, the continuation workflow
must not dispatch until this correction PR is independently accepted and explicitly merged.

Workflow green is not PT-2 acceptance. PT-3 remains blocked until independent review of the combined
baseline + continuation evidence establishes PT-2 disposition. No state-sync/normalization PR is
created solely for the continuation.
