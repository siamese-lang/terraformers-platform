# PT-8A failure diagnosability correction

USER-authorized repository-only correction at execution base
`2302a85ff828411a24f235919ceddcab3cd6c89e`, decision
[6094591490](https://github.com/siamese-lang/terraformers-platform/pull/271#issuecomment-6094591490).
Implementation and the strengthened measurement contract require independent acceptance and USER merge.
This document grants no live execution or new episode. Frozen v2/v3, the original measurement amendment,
images/truth, historical verdicts and consumption remain immutable.

## Proven mechanism and correction

`executeProviderAndValidate` held the provider result only in a local variable. Its validation exception
prevented return to `AnalysisJobRunner`; `result` stayed null and successful-result storage never ran.
Consequently even completed generation/repair HCL was lost on validation failure. Init diagnostic
reductions were also discarded by `TerraformValidationFailureException.fromSafeReason`.
The observer's failed-job branch recorded no draft and observer CLI NOT_RUN; it could not inspect the
backend validation failure. Archive integrity plus a terminal failure was insufficient diagnosability.

The existing runner now opens a worker-local diagnostic scope. Existing facts extraction and the
existing grounding observer/call boundaries capture originals actually produced, without a second
model call. Initial and repair model contexts are distinguished from final documentation binding.
The initial, final and sanitized CLI-input candidates have independent UTF-8 SHA-256 identities.
Existing draft, init and validate checks still run; invalid candidates never enter the successful
result/file path. Terminal quality keeps its existing vocabulary, including technical FAIL and
TERRAFORM_EXECUTABLE_FAILURE; exact categories belong to diagnostics, not a new quality evaluator.

Before execution, a short generation-fenced transaction records a separate diagnostic object intent.
After success/failure, the bundle is written outside the job lock, read back and hashed, then admitted
by a fresh short transaction checking generation, intent and expiry. A FAILED job can retain its
own attempt's diagnostics, including late output; this cannot restore SUCCEEDED or successful metadata.
Heartbeat/terminal deadline/finalization fencing and Policy D are unchanged. A late diagnostic write
following expiration re-arms cleanup accountability and is compensated. No row lock spans object I/O.

Facts are the extractor's original structured output, not components inferred from final HCL.
The extractor exposes no separate boundary field, so boundaries are explicitly NOT_CAPTURED.
Stages without actual output remain NOT_CAPTURED/STARTED. Stage links use source-file IDs and hashes,
not prompts, image bytes, values or raw responses. Exceptions contribute types/categories only.

## Privacy, access and bounded retention

- Existing ObjectWriter/ObjectReader/ObjectRemover and configured result/source bucket are reused.
  One separate `analysis-diagnostics/<project>/<job>/<generation>.json` object is accountable in DB;
  it is never registered as a project Terraform result. No bucket, IAM grant or infrastructure is added.
- Maximum candidate size: 256 KiB each; maximum serialized bundle/read: 1 MiB. Retrieval snapshots
  are capped at 256 documents per snapshot. Exceeding a limit means incomplete evidence, not truncation
  presented as a complete original. Hashes identify omitted oversized candidates.
- Retention: seven days from diagnostic intent creation. Owner API access ends at expiry, even if
  physical deletion is unavailable. The existing cleanup dispatcher/executor discovers expired intents;
  failed deletion remains discoverable and is retried idempotently. DB/service availability is required
  for physical deletion; expiration is not a claim about bucket backup/versioning retention.
- `GET /api/analysis/jobs/{id}/diagnostics` verifies the existing project owner/admin policy, even for
  public projects, and returns only safe stage metadata, candidate hashes/sizes and CLI reductions.
  `?includeContent=true` is an explicit owner/admin-only, no-store read of the private originals.
  Ordinary job/project/result APIs and application logs do not expose failed candidates or facts.
- CLI reductions retain init/validate phase, observed exit code/time when available, diagnostic classes,
  bounded source line/column and application-allowlisted summaries. Stderr, snippets, detailed provider
  values and raw exception text are withheld. Unknown diagnostic forms remain explicitly unknown;
  the original candidate can be revalidated with the same pinned CLI/provider.
- Metadata-only writes, failed write/readback/export, mismatched hash/generation, expiration or size
  overflow are DIAGNOSTIC_EVIDENCE_INCOMPLETE. Available partial originals may be owner-readable but
  cannot authorize another measurement. A process kill before final bundle persistence still loses
  worker-local snapshots: the durable PENDING intent is incomplete, not invented evidence.

## Measurement amendment

The existing measurement runner's successor contract is `pt8a-reviewable-draft-measurement-v2-diagnostics`.
Its execution base is the corrective base `2302a85ff828411a24f235919ceddcab3cd6c89e`;
the frozen v1 amendment retains its original base. Its canonical hash changes; the original v1 approval cannot authorize it. Exact independent review,
USER policy and live approval must also bind this amendment's actual SHA-256. No predicted source/image
or monetary ceiling is supplied. All existing source/image, frozen truth, retained-v4 risk qualification,
once-only dispatch/upload, independent review, model/time budgets and consumption checks remain.

The observer uses at most seven owner-scoped GETs, with at most 30 seconds of waiting between
polls (inherited HTTP timeout unchanged), to allow the post-terminal storage write to finish.
Unavailable/expired/forbidden evidence is not retried. It fetches only the authenticated safe diagnostic summary; it never requests original content
or exports failed HCL. A failed validation requires its candidate identity and CLI diagnostic reduction;
a provider/facts/RAG failure requires an exact stage/category plus explicit uncaptured stages. A stage and exception class alone do not establish cause. `UNCLASSIFIED_INTERNAL_FAILURE` and
`INTERNAL` are incomplete even when a candidate exists: the existing INTERNAL category does not
distinguish malformed diagnostics, cleanup or other internal mechanisms. Partial originals remain
owner-readable; they cannot justify another paid case. Classified provider/CLI failures retain their
existing treatment and candidate/diagnostic requirements. The summary
must pass strict field/type/identifier allowlists and the owner-scoped storage/readback identity checks.
Independent review additionally states `failure_diagnosability: VERIFIED` and binds
`diagnostic_evidence_sha256` to the private bundle identity. EVIDENCE_VALID_MEASUREMENT_ONLY is therefore
not an archive-only judgment. Missing preservation/export/review fails closed before another paid case.
Measurement completeness and quality PASS remain distinct; a diagnosable failure is never promoted to PASS.

## Immutable stopped episode and future boundary

| Case | Original observation | Disposition retained |
| --- | --- | --- |
| A | run 38028039113 / artifact 11660394315 | Original observation/review retained |
| B | run 38029022214 / artifact 11661990598 | FAILED / NOT_PASS / consumed=true; HCL and exact CLI cause irrecoverable |
| C | run 38029924066 | CANCELLED; no new observation manufactured |
| D/E | No run | NOT_RUN |

There is no backfill, reclassification, consumed-dispatch reuse or old-episode continuation. Existing
cross-source once-only guards still deny consumed cases. A future distinct episode requires a separate
explicit decision/live scope after independently accepted correction and USER merge; this PR creates
neither that episode nor execution authority. PT-8B/teardown remain unauthorized.

Operational limitation: the current `measurement_history()` inspects the stopped A/B/C dispatches
across sources and rejects a new-source Case A. This is intentional fail-closed preservation, not a
ready-to-run new episode. The exact real job names/conclusions (A success, B failure, C cancelled)
are covered by a regression. Changing source, contract hash or approval does not reset eligibility.
Synthetic chain tests exercise conditional runner behavior; they do not prove current live eligibility.
A future USER decision must explicitly define a distinct episode identity/scope, authenticated binding
of all stopped observations, cross-source once-only consumption within that episode and ambiguous
acceptance/non-replacement rules, followed by independent implementation acceptance, USER merge and
new source/image/live cost authority. This PR implements no episode partition or history exception;
a policy/live approval alone cannot unblock the current history.

## Deterministic proof and limitations

The existing controller integration regression first failed with diagnostic GET 404 after an injected
validation failure. Post-change it recovers byte-identical failed HCL and matching SHA-256 through owner
JWT, with safe diagnostics, no successful result and no HCL in ordinary response/logs. The local proof
uses real ProcessCommandExecutor, Dockerfile-pinned Terraform 1.8.5 and AWS provider 5.100.0, checked
against their existing archive checksums. The same test repairs the retrieved synthetic candidate with declared external-input variables and
revalidates it successfully without changing the original FAILED job. Normal CI uses the existing
deterministic validator seam. Replacing only the compiled Orchestrator with the execution-base version
causes this same real-CLI recovery test to fail: the candidate is absent and evidence is incomplete;
restoring the corrected Orchestrator restores recovery. The source tree is not rewritten for this control.

Existing suites cover provider/init failure versus schema failure; CLI init/validate timeout versus
command IOException versus malformed internal diagnostics; actual facts/RAG/repair failure snapshots;
success result compatibility; public-project cross-owner/unauthenticated denial; checksum corruption,
expiry/deletion failure and late-write cleanup; storage loss and missing independent diagnostic review
blocking the real predecessor contract. Validation counts and natural development failures are reported
in the PR, not transformed into product observations or changes to PT-6/PT-2 counters.

No live evidence is generated by this correction. Historical Case B's exact HCL/error cannot be recovered.
The earlier 220-second upstream timeout's physical cause remains unproven; no model, SDK, prompt,
timeout/retry, corpus or provider policy changes are made. Storage operations add at most a 1-MiB object
per retained job plus bounded reads and cleanup; USD cost is not asserted. Process loss and unavailable
storage/DB can still prevent recovery and correctly block measurement. Those residuals require independent review.
