# PT-2 bounded continuation procedure

Procedure `pt2-realistic-continuation-v3` is a bounded correction to the already-executed
`pt2-realistic-baseline-v2`. It exists only because authoritative run `37480519016` produced
valid partial evidence: case 01 reached terminal success, case 02 was accepted once and remained
non-terminal beyond the frozen 420-second measurement window, and cases 03-10 were never submitted.

This procedure does not replace, reinterpret, or rerun those observations. It preserves the original
420-second latency censoring for case 02 and permits only read-only recovery of that exact accepted
job plus first submission of the previously NOT_RUN cases.

The independently accepted v2 procedure (SHA-256
`1658b5c328fdfde9671542373309e68165d7aae6f4b973d0b781f03890b097ee`) was dispatched exactly once,
as run `37500000739`, and failed closed during history verification before JWT preparation or any
product API observation/submission. It is preserved as a zero-inference mechanical preflight failure,
not a consumed case attempt. [Independent recovery review 6021688827](https://github.com/siamese-lang/terraformers-platform/pull/245#issuecomment-6021688827)
and explicit USER approval authorize only this v3 repository-only correction preparation and
deterministic validation. No live dispatch is authorized before independent acceptance and explicit
merge of the correction. This human-authorized iteration does not consume or reset autonomous repair.

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

The existing global GCP concurrency group is preserved. Paginated **repository-wide** workflow-dispatch
history is read before the auth fixture is prepared, with a no-cache request header. This avoids relying
on the workflow-name history endpoint whose preserved response omitted both original/current runs;
the underlying stale-response cause remains unknown. Every page must report the same total count,
and the combined unique run count must equal that count. API pagination caps, truncated/mixed/stale
pages, duplicates or missing exact expected runs fail closed; acquisition is not retried until green.
Original/current/exception runs must also match repository, workflow ID/path, main, event, attempt
and their exact source identities. The current run must be main-only,
attempt 1, at the exact dispatch SHA and named `PT-2 continuation of 37480519016`. Any earlier
continuation dispatch with that title stops the new run, except for the single evidence-bound
zero-inference source below. Every other continuation with that title (including a concurrent later
dispatch) stops. No second recovery continuation is allowed after the one permitted by this v3
exception, regardless of outcome, run ID or attempt. There is no general ignore-failed-run switch.

## Exact zero-inference exception

Only these identities can make the source dispatch non-consuming:

- source run `37500000739`, attempt 1, completed failure, workflow-dispatch on main;
- dispatch SHA `026adab4dec17fd3b57ee950f174f277ed7c6d80`;
- workflow ID `368779787`, `.github/workflows/gcp-target-evaluation-baseline.yml`;
- single failed `baseline` job `112394279733`, same run/attempt/dispatch;
- artifact `11429522041`, `pt2-realistic-continuation-37500000739`, unexpired;
- archive digest `sha256:49b017e98cb05a546a2c7900044ae6d742762df2c59fdd10a5abe2a4a64a179f`;
- inventory SHA-256 `0875d2814b3e42f87eccd6f15cf7d309c2f6684cdd6e09b331ba24447f0d2888`.

GitHub run/artifact metadata, the actual downloaded ZIP and exact attempt's job/steps must all agree.
Job steps are bound by number, exact name, completed status and conclusion: 43 prior evidence/history
verification FAILURE; 44 JWT preparation, 45 original observer, 46 recovery/continuation SKIPPED;
47 fixture restore, 48 inventory seal, 51 artifact upload, 53 artifact handoff and 60 ephemeral-pod
cleanup SUCCESS. Missing, changed, duplicate or extra jobs invalidate the exception.
All 63 recorded job steps also have a pinned canonical SHA-256
`45b6bb1cf24009a7cdf991988ab7f2155b2bb517ec2d4136f07d3889fb2fd305`: the ordered array
of each step's `number`, `name`, `status`, `conclusion`, encoded as sorted-key, compact JSON UTF-8.
Any changed/extra/missing step invalidates that identity; mutable runner labels are not acceptance proof.

The sealed artifact must have exactly the eight original preflight/runtime/history files plus its
inventory. Verify every file checksum and exact file/directory set; reject unsafe archive paths,
duplicates, links, case directories, ledgers, recovery responses, accepted identities and model output.
Its runtime/procedure binding must still describe the original v2 dispatch and original execution base.
These checks run before authentication fixture preparation and again before any runner API observation.
The untouched ZIP, extracted source evidence and GitHub metadata are included in future evidence
inventory. The original failed run/artifact is never rewritten or rerun, even under its original ID
with attempt 1. This correction cannot declare v2 green.

Full history must include the exact exception run as well as the authoritative original baseline and
the new current run. Ignoring an ID without all of the proof above is prohibited. Any other prior
continuation blocks, including failed/cancelled/preflight-only runs; there are no inferred exceptions.
Natural failures or partial new-case evidence in the next recovery remain consumed observations.

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
