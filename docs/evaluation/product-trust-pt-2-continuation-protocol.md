# PT-2 bounded continuation procedure

Procedure `pt2-realistic-continuation-v2` is a bounded correction to the already-executed
`pt2-realistic-baseline-v2`. It exists only because authoritative run `37480519016` produced
valid partial evidence: case 01 reached terminal success, case 02 was accepted once and remained
non-terminal beyond the frozen 420-second measurement window, and cases 03-10 were never submitted.

This procedure does not replace, reinterpret, or rerun those observations. It preserves the original
420-second latency censoring for case 02 and permits only read-only recovery of that exact accepted
job plus first submission of the previously NOT_RUN cases.

Draft continuation v1 (protocol SHA-256
`28d225feceb2240d127a9fe7b0f33d3bbca836ecc2913bd3b6ff2a9721be1b00`)
and its later clarified SHA-256 `d24bdec4065d647ac4ae5cf21afb76b4e98fd9d50faacd3f3e72783313ace482`
were untrusted ChatGPT implementation, not accepted work and never dispatched. USER authorized Codex to audit
PR #245 on the same branch. The audited handoff head is
`f2afda0e2e5957b161749d752ba2400593212fb7`, including five commits after the supplied
`a8cd0270ed89321cea20ed5a66e02f99cd3b5321`. This repository-only corrective iteration
does not spend or reset the Work Package's autonomous repair counter or rebind its original
execution base `3caae454661d21d84c3e469a7d76aff5633d5b26`. PR #245 is based on the
explicitly merged workflow main `c3e9ed4e854778dfb2eefbc0182ae53a008025bf`.

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
the downloaded ZIP digest, inventory and exact file set with every file checksum, and fail closed
on any mismatch. Archive traversal, duplicate paths and symbolic links are rejected. No regenerated
or hand-built summary may substitute for the prior artifact. The runner repeats these checks before
any API observation or new inference. GitHub metadata also binds repository, workflow, event,
completed failed run, attempt and dispatch source.

The existing global GCP concurrency group is preserved. All workflow-dispatch history for the existing
evaluation workflow is read before the auth fixture is prepared. The current run must be main-only,
attempt 1, at the exact dispatch SHA and named `PT-2 continuation of 37480519016`. Any earlier
continuation dispatch with that title stops the new run, including when it has a different run ID
and `run_attempt=1`. There is no automatic second continuation batch after partial evidence.

## Recovery before any new inference

Before a new upload, read only the exact case-02 AnalysisJob through the existing authenticated
production API path. Do not resubmit case 02 and do not change its original latency measurement.
Record recovery timestamps separately.

Production ownership is keyed by the authenticated external identity subject. The continuation therefore
uses a newly generated run-specific signing key/JWK but authenticates as the original fixture subject
`case-c-37480519016` (and matching fixture email) so the exact prior job/project remains owner-readable.
The signing-key `kid` remains unique to the continuation run. No prior private key or token is reused,
and this identity continuity is permitted only for this bound recovery/continuation operation with
the exact original numeric run ID. Other helper operations cannot override their current fixture
subject. Authentication validators and owner-scoped production API checks remain unchanged.

If the exact job is already terminal, preserve the terminal response, project readback, Terraform
readback when present, and newly available correlated logs. If it is still PENDING/RUNNING, poll that
same job only within any remaining part of the 1200 seconds from its **original** accepted-response
receipt. Once that bound is exhausted, a nonterminal recovery response stops without uploading
case 03; it does not receive a fresh 1200-second window. A terminal first recovery response is usable
as later semantic/trust evidence even after that bound. An inaccessible or identity-mismatched job,
project or Terraform readback also stops. Recovery errors retain an error record and an all-NOT_RUN
continuation ledger.

For this actual handoff the original 1200-second-from-acceptance bound has already elapsed.
A first recovery read still showing PENDING/RUNNING therefore stops immediately. Continuation
never resets that clock or claims the older censored sample as uncensored.

The recovery observation may complete semantic/trust review for case 02, but its latency sample remains
censored at the original 420-second observation policy with the original 424846 ms observation.
That elapsed censor receipt is not an exact terminal time or proof the job remained running for all
424846 ms; the original last-nonterminal receipts remain available. A later terminal timestamp must
never replace the original censored measurement.

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

Bounds apply to response receipt, not merely GET initiation. A terminal response arriving after 420
seconds remains censored even when its request started earlier. If it arrives within 1200 seconds,
that same response may supply separate drain evidence; no extra inference or duplicate GET is needed.
A terminal response arriving after 1200 seconds is preserved as a late observation and stops before
the next upload. Read-only requests have the original bounded transport timeout; waits are clipped to
the remaining observation window. The accepted job is never cancelled or resubmitted.

The submission entrypoint rejects cases 01/02, previously attempted records and input/truth disguised
as an eligible case. Duplicate prior/new job or project identities stop and preserve the actual raw
acceptance. Main is checked immediately before each POST. Lost/malformed acceptance, auth or identity
failure stops without another POST. Natural terminal failures are retained and may be followed by the
next first submission. Admission rejections are preserved without claiming a complete terminal baseline.

This separation prevents one latency tail from erasing the remaining benchmark while also preventing
overlapping accepted jobs from contaminating queue/latency evidence.

## Runtime and authority boundaries

Reuse the exact retained production AnalysisJob path, GKE/OpenSearch substrate, WIF identity,
ephemeral JWT/JWKS fixture and immutable backend image/source checks already accepted for PT-2.
No product code, prompt/model, RAG, scorer, truth, IAM, infrastructure, backend rollout or runtime
configuration may be changed by this correction.

JWT/JWKS restoration remains fixture-only and fail-closed. Even restoration failure removes this
run's private key, token, signature and Authorization header files, retains an ownership marker for
the un-restored fixture and reports failure. Backend deployment is never restarted. Existing workflow
180-minute budget and 3-hour token expiry remain hard limits; exhausting either yields partial evidence,
not a repeat or overlap authorization.

The original `pt2-realistic-baseline-v2` procedure and run artifact remain immutable evidence.
The continuation gets its own run ID, artifact ID/digest and inventory. Independent review must bind
both artifacts when deciding PT-2 completeness.

The existing USER `LIVE_REALISTIC_BASELINE` approval covers the originally approved ten frozen inputs;
this correction does not add inputs or repeat an inference. Nevertheless, the continuation workflow
must not dispatch until this correction PR is independently accepted and explicitly merged.

Workflow green is not PT-2 acceptance. PT-3 remains blocked until independent review of the combined
baseline + continuation evidence establishes PT-2 disposition. No state-sync/normalization PR is
created solely for the continuation.
