# PT-8A reviewable-draft measurement amendment — decision proposal

**PROPOSED D_DECISION; NOT ADOPTED; LIVE EXECUTION UNAUTHORIZED.** Repository-only preparation
is USER-authorized at main `1460b1ad8f34e0a63ebe16af3c205f01b4c28a0a` after PR #270. This file
is an explicit successor proposal, not a replacement for v2/v3, the original acceptance chain,
diagnostic episodes or the consumed Recovery. Its SHA-256 and the existing runner's canonical
`MEASUREMENT_CONTRACT` hash must be bound in independent review, USER policy and later live authority.
No predicted merge/source/image digest or USD ceiling is supplied.

## Problem, alternatives and decision requested

The frozen v4 truth already makes image-observable facts the semantic authority, permits declared
variables/external resources and excludes invisible page-only deployment facts. Those boundaries,
RAG provenance and actual Terraform 1.8.5 / AWS Provider 5.100.0 validation remain appropriate.
PR #270 removed the excessive ACM issuance-specific quality rule; this amendment adds no such rule.
In frozen Case A, closure explicitly permits declared domain/certificate/zone references and does
not require page-only issuance/DNS mechanics. Cases B-E likewise make wiring conditional on the
chosen implementation. These are coherent draft checks, not a requirement to discover real user
resources. The conflict is not those truth fields or the existing CLI/schema validation.

The conflict is **measurement completion versus quality acceptance**: the original qualified chain
requires all ten dimensions PASS before the next case; Program completion requires that for all
five positives. A genuine product failure therefore leaves later inputs unobserved and the quality
assessment incomplete. The Recovery stops on technical failure; the old single diagnostic is consumed.
None can be reopened by changing source or approval. An UNKNOWN result is evidence of uncertainty,
not a reason to fabricate a successful sample or continue repairing until every frozen answer passes.

Alternatives: retain the acceptance campaign unchanged (safe but cannot finish a representative
measurement after failure); weaken/restart it (rejected: rewrites consumption and conflates success
with completeness); or add one separately authorized measurement episode in the existing runner.
**Proposed choice:** the latter, reusing diagnostic observation, artifact binding, independent review,
read-only retained-v4 admission and the existing protected workflow/job. No new evaluator, model judge,
workflow, backend changes, prompt tuning or Case-specific implementation recipes.

## Definitions and retained product criteria

| Disposition | Meaning |
| --- | --- |
| Measurement COMPLETE | All five new first observations have an identified accepted job, preserved terminal/result evidence, pinned CLI attempt where a draft exists, authenticated independent evidence-validity review and an intact sequential artifact chain. A terminal product failure can satisfy measurement, not quality. Final E review is mandatory. |
| Product quality PASS | Separately, every case has all ten original dimensions PASS, no material defect and zero false trusted successes. Measured FAIL/PARTIAL or technical failure is NOT_PASS; unresolved UNKNOWN/NOT_OBSERVED/NOT_APPLICABLE is UNDETERMINED, never automatic PASS. |
| Technical product failure | The admitted system accepted the input and reached a preserved FAILED result or a draft failed real technical checks. Provider timeout/truncation/failure or missing/malformed draft remains original negative evidence; no retry. Independent review must distinguish it from an infrastructure/authentication/provenance failure. |
| Measurement INCOMPLETE | NOT_RUN, ambiguous acceptance, censored/nonterminal job, inaccessible/missing evidence, failed release/corpus/authority/cleanup integrity, or missing independent review. No later terminal substitutes for the censor; no resubmission. |

Retain image core services, depicted directions/boundaries/cardinality and forbidden inventions;
keep syntax/Provider/reference validity, official-resource evidence and truthful persisted trust.
Do not require unseen account/region/domain/ARN/DNS ownership, real Lambda package or deployed
certificate issuance state. Coherent declared external interfaces may express visible intent;
unrelated resource blocks do not prove an edge. Review the chosen code's references/permissions,
without imposing a particular documentation provisioning recipe or claiming deployment success.
The ten dimensions and frozen truth are unchanged. Product quality PASS is not a prerequisite for
recording the next first observation; **valid independently reviewed evidence is**.

## Proposed one new episode; immutable old evidence

Operation `pt8a-draft-measurement`, mode `draft-measurement-case`, cases A-B-C-D-E in frozen order.
Exactly one distinct dispatch / attempt 1 and at most one POST per case **across all sources and
approval comments**, even when preflight failed. No free-form episode ID can reset that budget.
The five new samples are an explicit successor; they never replace/promote the original A/B,
Recovery A or successful DIAGNOSTIC_ONLY generation. All original outcome/artifact identities remain
bound. Existing modes retain their once-only and quality-PASS progression requirements.

Proposed ceilings requiring separate exact live approval: **5 dispatches, 5 uploads, 30 reserved
model calls including embedding (6 per case), 35 workflow minutes per dispatch, 540-second accepted
job observation**, unchanged Policy D. A bounded final read/sleep retains the existing censor behavior.
USD cost is not established; call/time ceilings are not a dollar guarantee. No resubmission, model
retry or result-driven repair budget is added. Publication/rollout, if necessary, are separate existing
protected operations and require their own authority, not this measurement approval.

The existing workflow reuses its WIF, protected `gcp-target-apply`, serial runtime concurrency,
retained Deployment, owned JWKS fixture, pod and artifact cleanup. Runtime source/image/model/index
must match exact approved values. Use the original authenticated clean-v4 receipt and verify mapping,
all 5,395 IDs/non-vector content, retained UUID and finite 1536-dimensional vectors before/after;
known writer history must be unchanged. Explicitly accept `VECTOR_WRITE_CONTINUITY_UNPROVEN`, not
cryptographic vector continuity. No index write, embedding admission request or reembedding.
The next case also requires the predecessor's inventoried `JWKSRestored: true` receipt and the
owned-validation-pod removal step's successful GitHub conclusion. Cleanup failure is an integrity
blocker even when the accepted product job reached a terminal state.

## Decision, review and execution authority

Independent `[PRODUCT_TRUST_REVIEW:v1]` on this amendment PR must bind `decision: ACCEPTED`,
`reviewed_head`, `execution_base_sha`, `measurement_contract_sha256` and `amendment_sha256`.
USER policy approval is a distinct `[HUMAN_GATE_APPROVAL:v1]` on that same PR, with fields from
`measurement_policy_fields()` plus `reviewed_head`. Gate is
`PT8A_REVIEWABLE_DRAFT_MEASUREMENT_AMENDMENT_REVIEW`. The PR must then be USER-merged;
source must contain that merge. Repository approval/CI/merge alone never grants live execution.

Later `[HUMAN_GATE_APPROVAL:v1]`, gate `FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE`,
must bind every field returned by `measurement_live_fields(request, source, image)`: exact actual
source/immutable image, proposal/contract/v2/v3/candidate hashes, amendment PR/review/policy IDs,
original failed/successful artifact digests, clean-v4 origins, risk acceptance and numeric ceilings.
Earlier approvals are insufficient. Existing protected environment reviewers remain required.

Inputs use existing `expected_sha`, `backend_source_sha`, `backend_image`, `pt8a_request` and
confirmation `RUN_REVIEWED_PT8A_DRAFT_MEASUREMENT_CASE_ONCE`. The request has only:
`mode`, `caseId`, `liveApprovalCommentId`, `provenanceRunId`, `provenanceArtifactId`,
`amendmentPullRequest`, `amendmentReviewCommentId`, `policyApprovalCommentId`,
`priorRunId`, `priorArtifactId`, `priorReviewCommentId`. A's prior IDs are exactly zero;
B-E bind the immediately preceding measurement run/artifact/review. Truth is never submitted.
No source/image/authority rebinding within the episode; complete all-source GitHub history is checked
before cloud access and again before upload. Missing/ambiguous or duplicate history fails closed.

Each artifact is `pt8a-measurement-<run_id>`, classification
`REVIEWABLE_DRAFT_MEASUREMENT_ONLY / NOT_ACCEPTANCE`. Keep backend status/quality, RAG IDs,
model termination/timing, HCL identity and real CLI diagnostics. A product failure may keep a failed
workflow conclusion and still be evidence-valid; neither workflow green nor red alone is a quality verdict.

Before the next case, authenticated `[PT8A_DRAFT_MEASUREMENT_REVIEW:v1]` binds source, image,
candidate, contract, run/artifact/archive digest, case and observed status. It requires
`decision: EVIDENCE_VALID_MEASUREMENT_ONLY`, `observation_class: PRODUCT_OBSERVATION`,
`infrastructure_auth_provenance: VERIFIED`, `official_acceptance: NOT_ACCEPTANCE`,
`vector_write_continuity: VECTOR_WRITE_CONTINUITY_UNPROVEN`, each `dimension_<SCORING key>`,
`material_defect`, numeric `false_trusted_success` and honest derived `product_quality`.
`allow_next_measurement: true` for A-D; `false` for E. FAIL/PARTIAL/UNKNOWN is retained in those fields,
not rewritten to PASS. Integrity/authority/admission/censor failure cannot receive this progression tag.

## Finalization and downstream meaning

After independent E review, run the **existing runner's read-only `finish`**, not another dispatch:
use E's source/image/run/attempt and measurement request with `caseId=aws-official-e` and prior IDs
pointing to E itself and its review. Original authority and the full A-E archive/review chain are
reverified. Write a new local summary directory, never into an original artifact. It reports separate
`measurementState`, `productQuality`, terminal failure count, `releaseAcceptance: NOT_GRANTED` and
no generalization claim. This is aggregation of authenticated findings, not another evaluator.

**Proposed durable adoption after USER approval:** an independently accepted COMPLETE measurement
may close PT-8A measurement with PASS, NOT_PASS or UNDETERMINED quality retained as a separate verdict.
It does not automatically grant product/release acceptance or start PT-8B. Negative/unknown residuals
must receive an explicit final USER disposition (accepted documented limitations or a separately
approved bounded correction) before any downstream release claim. This proposal does not amend
PT-8B's dependency or fabricate phase completion today. The original five-positive sample cannot
establish arbitrary-diagram generalization, a production timeout SLO or deployed AWS correctness.

STOP for independent amendment review and USER decision/merge. No live execution is authorized
by this repository-only implementation. Original artifacts, v2/v3, candidate/truth, consumption,
Policy D and prior repair counters remain immutable.
