# PT-4 — distinguish processing, output trust and elapsed time

Status: COMPLETE within the deterministic trust/status/timing correction.
[Independent review 6029174275](https://github.com/siamese-lang/terraformers-platform/pull/248#issuecomment-6029174275)
accepted exact head `68110fc0429d88b3211c5811b1c470770bc4c3f7`; USER merged PR #248 as
`0c4fa0e9616881e281aa027a18a9d741ff614699` on `2026-10-07T01:52:07Z`.
No live/model/GCP action or rollout is performed. The original execution base remains
`5f623d820f5bf379636f933f5773f46177b5caf3`, read and bound once in the
[Work Package](../../.agents/work-packages/product-trust-pt-4-trust-status-waiting-v1.yml).

## Authority and observed scenario

PR #247 was independently accepted by
[6028631570](https://github.com/siamese-lang/terraformers-platform/pull/247#issuecomment-6028631570)
at exact head `f127e71a5d36c9e8565c0e3e72260e67a5e71865`, then USER merged it as this task's base
at `2026-10-07T01:05:47Z`. That fulfills the Program dependency. USER's approved Goal / repository
DAG authorizes PT-4; independent review alone did not authorize it. PT-3 completion remains bounded
to deterministic omission exposure, not live improvement or effective authorization proof.

The observed PT-2 subset supplies the mandatory inputs, preserved in the
[final disposition](product-trust-pt-2-final-disposition.md) and untouched raw archives:

- case 01 persisted technical PASS / knowledge COMPLETE / quality UNKNOWN / project decision UNKNOWN,
  but project/API/frontend presented completed processing without the quality snapshot;
- case 02 remained censored at `424846 ms`, contradicting unconditional `1~3분` waiting guidance;
- PT-3 exposes one known omission as semantic DEGRADED while technical validation can remain PASS.

Before PT-4, `ProjectResponse` and `ProjectTreeResponse` omitted existing bounded job quality. The
detail and list rendered completion alone. A 30-second client timer asserted model-response waiting
without a durable provider stage. `AnalysisJob.updatedAt` changes on lease renewal, retry and cleanup;
it is not a terminal timestamp. These are directly inspectable mechanisms, not inferred model causes.

## Decision within the approved phase

Reuse Case B's existing durable/fenced job state and Case A's persisted `evidence-quality-v1` snapshot.
Expose quality and timing from the same latest job through existing job/project/list/tree APIs;
reuse the existing project-visibility/ownership checks. A shared presentation component serves
project detail, owned-project cards and the selected public-project detail.

Keeping completion-only presentation fails the approved phase criteria. Inferring provider stages
or terminal duration from a stopwatch/updatedAt would manufacture evidence. New telemetry services,
confidence scorers, status machines or model judges add responsibility without resolving the missing
projection. The selected additive timestamp and projection stay inside current contracts. No new
architecture, product choice, identity or security boundary is introduced.

- SUCCEEDED means processing completed, not trustworthy deployment.
- UNKNOWN, DEGRADED, NOT_APPLICABLE, absent and unrecognized quality remain distinct from the
  conditional EVIDENCE_BACKED label. Technical, knowledge, project-decision and reason fields are
  preserved. PT-3's missing authorization reason is visible while retaining the editable draft.
- Evidence-backed wording is explicitly conditional on extracted facts/selected evidence. Technical
  PASS or a supplied authorization declaration does not prove effective IAM access or deployment.
- Only persisted PENDING/RUNNING/SUCCEEDED/FAILED status is presented; no provider stage is inferred
  from time. No confidence score, completion percentage, SLA/SLO or replacement duration promise.
- A failed detail poll preserves the last snapshot and explicitly marks it as a last confirmed record.

## Timing semantics and durable boundary

`V20261007_008` adds nullable `analysis_jobs.terminal_at`. Success records it after draft storage and
artifact registration in existing owned finalization, before persisting the terminal row. Failure's
existing generation/lease-fenced SQL transition records its supplied transition instant. Rollback
does not publish a durable success timestamp; stale workers cannot replace a terminal row. Heartbeat,
retry and result cleanup do not write terminal_at.

The shared API timing is `acceptedAt` (persisted job created_at), `terminalAt`, and
`acceptedToTerminalMs` (nonnegative duration between those two instants for terminal rows).
This measures backend job admission to terminal transition; it excludes pre-job upload and browser
poll/receipt delay and is not a new measurement of the original PT-2 requests. It records the
transition timestamp, not an independently instrumented database-commit instant. Server clock
anomalies may leave duration unknown instead of producing a negative value.

Historical terminal rows are deliberately not backfilled. No terminal_at means unknown terminal
duration even if updatedAt is available. The original case-02 censor stays `424846 ms` and is never
replaced by a calculated, later or terminal observation.

For active jobs the screen anchors approximate elapsed time to the persisted acceptedAt, using the
browser clock. Navigation/remount therefore does not restart elapsed time. Clock skew, missing or
invalid timestamps are explicit limitations; no completion estimate follows. Terminal elapsed time
uses the API's frozen duration and stops advancing. UI whole-second formatting does not replace the
API's millisecond record. Persisted job status/quality/timing are fetched again after reload. Public
tree detail is a fetched snapshot, not a newly instrumented provider-stage stream.

## Deterministic verification and remaining gates

The [validation record](../evidence/product-trust-pt-4/validation.json) records exact commands, counts,
source hashes, frozen identity and scope/state checks. Focused backend: 45 tests PASS;
focused frontend: 40 tests PASS; full offline backend clean package: 526 tests, 0 failures/errors,
2 existing external MariaDB skips (524 passed); full frontend: 82 tests PASS across 13 suites;
frontend production build PASS; eight unique Flyway migrations PASS. Autonomous repairs used: 0. The initial controlled UI regression fails
on the pre-correction page because persisted UNKNOWN is not displayed. That intentional red test
is separate from autonomous repair and is not a new live product/model sample.

Regression coverage checks same-job API quality/timing equality, private/public access invariants,
explicit legacy null timing, fenced failure and cleanup persistence, success timestamp placement,
UNKNOWN/DEGRADED versus PASS, PT-3 reason and draft preservation, future/missing quality semantics,
re-entry timing, terminal freeze, polling failure and lack of invented provider waiting or percent.
Existing backend persistence/runner tests and frontend upload/auth/public flow tests remain reusable.

Local tests use H2/mocked/stub providers and existing cached dependencies, not Vertex/Gemini/GCP.
No local MariaDB server or application stack is started. Existing PR CI supplies real MariaDB
Flyway/Hibernate compatibility evidence; the local H2 suite alone does not establish that compatibility.
Browser integration and final live product proof remain later Program phases; component tests/builds
are not those acceptance claims. No workload/model latency reduction is claimed.

PT-2 stays final INCOMPLETE: 03–10 NOT_RUN, no ten-case generalization or aggregate realistic rates,
case-02 censor unchanged, recovery runs zero product observations, no further dispatch/recovery.
Frozen truth/corpus, generation/prompt/model/retrieval/scoring, runtime/IAM/infra and auth policies
are unchanged. No teardown. GitHub holds transient PR lifecycle; state retains no active PR/branch.

The original independent-review and explicit-merge checkpoint was fulfilled by review 6029174275
and USER's PR #248 merge. CI supported that review; it did not grant acceptance or merge authority.
The approved Goal / Program DAG selects repository-only PT-5 next; any new security/identity/live/
product decision retains its own human gate. Historical PT-4 validation remains unchanged.
