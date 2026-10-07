# Product Trust v3 — cross-phase integrity audit

## Authority and scope

USER approved `APPROVE product-trust-v1 v3 residual-closure amendment as audited above.` at
`2026-10-07T04:15:00Z`. The [approved request](../evidence/product-trust-v3-amendment/approved-request.md)
is the decision authority. This audit reconciles main
`f344552fe58db26438a4f0b2c9abb1806e6a99d9` in one repository-only amendment PR.
It changes governance, durable state and the plan; it implements no product correction and executes
no database, browser, model, cloud, workflow-dispatch or destructive validation.

[Review 6030408825](https://github.com/siamese-lang/terraformers-platform/pull/250#issuecomment-6030408825)
independently accepted PR #250 at `0b8f7ef0eefc472a0438a3d0eeedee0f1c919c7a`. USER merged it at
`2026-10-07T03:53:53Z` as the main above. PT-6 is therefore COMPLETE for **deterministic browser
integration only**. Its original execution base `37e006be4d5995019704ea8a4009e1a59da039b2`, one
autonomous repair, one separate human-authorized correction, failed observations and limited claims
are preserved. Independent acceptance did not itself grant merge or another execution authority.
The [review snapshot](../evidence/product-trust-v3-amendment/review-6030408825.json) preserves its body.

## 1. Production behavior actually improved

| Accepted change | Actual production behavior | Evidence and limits |
| --- | --- | --- |
| [PT-3 / PR #247](product-trust-pt-3-origin-authorization.md) | Existing final generated-contract inspection and quality assessment expose missing supplied read authorization for an identifiable CloudFront OAC origin pointing at a newly created S3 bucket. Quality becomes DEGRADED with a reason while schema/CLI PASS and the editable draft remain valid separate dimensions. | Deterministic omission regression, compatible authorization controls and independent review `6028631570`. Does not automatically supply permissions or prove effective/deployed IAM. |
| [PT-4 / PR #248](product-trust-pt-4-trust-status-waiting.md) | Same-job quality and durable acceptance/terminal timing reach existing APIs and frontend. UNKNOWN/DEGRADED/absent quality is distinct from processing completion; unsupported duration/provider-stage promises are removed. | Deterministic backend/frontend and persistence evidence, review `6029174275`. No measured model-speed improvement or production latency SLO. |
| [PT-5 / PR #249](product-trust-pt-5-access-safety.md) | JWT-enabled routing explicitly allows supported public GETs and authenticates the remaining routes; existing ownership, public reads, comments and persisted ADMIN exception remain intact. | Before/after unlisted-route regressions and authorization matrix, review `6029459989`. Authentication is not authorization, and deterministic identity is not a hosted IdP proof. |
| [A7-7 / PR #237](https://github.com/siamese-lang/terraformers-platform/pull/237) | Production generated-resource official-evidence closure was corrected, preserving the existing one-time MAX_TOKENS compact generation retry and at most one single-attempt grounding repair. | Existing deterministic evidence is retained. Resource-type knowledge/closure is not complete relationship semantics or final realistic acceptance. |

This amendment changes none of those implementations. PT-6 driver sequencing and local validator
paths did not repair the first-user persistence race. No current result establishes generally
correct image interpretation, universally usable Terraform or deployment-correct IAM.

## 2. Measurement and integration evidence only

| Evidence | What is established | What remains outside the claim |
| --- | --- | --- |
| PT-1 revision 3 | Ten human-reviewed reference-derived realistic recreations: six architecture positives, two ambiguous and two non-architecture controls, frozen truth/provenance. | No statistical sample of all actual user exports; no model-authored truth. |
| [PT-2 final disposition](product-trust-pt-2-final-disposition.md) | Independently accepted final INCOMPLETE evidence from the observed subset and measurement failures. | No completed ten-case benchmark or aggregate realistic rates. |
| [PT-6 accepted correction evidence](../evidence/product-trust-pt-6/correction-1/browser/result.json) | Actual built browser frontend and real backend JWT/JWKS/ownership boundary; exactly one new upload, durable PENDING across navigation/reload, same-job terminal draft/trust/HCL binding, and second-identity denial. All five steps pass; real Terraform CLI/process validation uses the exact Dockerfile Terraform 1.8.5 / AWS provider 5.100.0 artifacts. | Local H2/filesystem/stub provider and controlled worker hold; not live model quality, retrieval fidelity, production IdP lifecycle, cloud rollout or production latency. |
| [PT-6 failed observations](product-trust-pt-6-browser-journey.md) | Initial FAILED job `604890dc-c272-485c-b9d5-95fdede77504` / 1582 ms and pre-upload failures remain preserved. Distinct accepted correction job is `345d4807-bf84-4b81-9a8e-4144339abc50` / 6642 ms local fixture time. | No successful recovery/reclassification of the original job. Earlier lost in-memory events/assertion cannot be reconstructed; only surviving logs and read-only empty inventory are claimed. |
| [PR #238](https://github.com/siamese-lang/terraformers-platform/pull/238) | Evaluator uses the same production orchestration rather than an independent generation path. | Harness alignment is not a live quality result. Synthetic/canonical/holdout results remain controlled regression evidence. |

Frozen identity remains
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014` at
`evaluation/terraformers-realistic-v1`, revision 3. All pinned bytes remain unchanged.
All existing evidence files, including PT-6's 21 pre-correction files and its separate correction
directory, are preserved. Completion metadata records later review/merge; it does not rewrite raw
evidence's historical pending-review markers.

PT-2 remains permanently `PT2_INCOMPLETE_ACCEPTED_AS_FINAL_EVIDENCE_DISPOSITION`:

- Cases 03–10 remain NOT_RUN, neither pass nor fail.
- Aggregate classification accuracy, hallucination rate, semantic-success rate and executable-validity
  rate remain null; no ten-case realistic generalization is established.
- Original case 02 remains latency-censored at **424846 ms**, never replaced by a later observation.
- Runs `37500000739` and `37509368210` remain zero-product-observation measurement failures.
- No PT-2 dispatch, recovery, reopening, continuation exception, resubmission or harness repair is
  authorized. PT-8A is a new final-system acceptance phase, not a reinterpretation of PT-2.

## 3. Unresolved defects and mandatory final trust gaps

| Finding | Supported mechanism / uncertainty | Required closure |
| --- | --- | --- |
| Concurrent first-user identity collision | PT-6 observed a local unique-identity collision. `AuthenticatedUserService.getOrCreate` performs lookup/create and catches a save integrity exception within its transactional boundary. Real MariaDB flush/commit/rollback visibility and concurrent convergence have not been audited/reproduced. An assumed conflict-and-reread fix is not accepted evidence. | PT-6R1: begin with real MariaDB concurrent first requests. Select the smallest correction only after causal inspection. Same provider/subject must converge on one durable user and same identity, with no 5xx, constraint leak or privilege escalation; distinct identities and PT-5 matrix remain intact. |
| Accepted job's provider-bound terminality is unproven | Case 02's censor is observed, but the exact upstream cause is unknown. `VertexRuntimeConfiguration` specifies `HttpOptions.apiVersion("v1")` without an explicit deadline. That source observation does not establish actual SDK defaults or prove a specific provider hang. Existing job retries/leases alone do not prove bounded provider execution. | PT-6R2: audit actual Google GenAI request/deadline capability and provider/retry/job boundaries. Do not invent a number. If a numeric budget/retry tradeoff is a product choice, stop at **HUMAN_REQUIRED: PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION** with measured evidence/options. After the choice, timeout/hang injection must prove truthful bounded terminal semantics while preserving PT-4. |
| Pre-publication production-image coverage gap | Existing Backend Local Verification runs Maven test/package, but `scripts/checks/backend-local-verification.sh` defaults `RUN_DOCKER_BUILD=false`; its automatic workflow does not override that default. Actual protected-main/ruleset required checks still require inspection; no ruleset gap is asserted without it. | PT-7: audit first; prefer the smallest existing automatic PR-workflow correction for relevant backend/Dockerfile changes. Verify pins through the real Dockerfile, not a duplicate verifier. Reconcile only demonstrated required-check gaps, with human authority for security/ruleset expansion. No automatic GCP deployment on merge. |
| Final corrected AI/RAG path lacks full realistic acceptance | Approved audit reports substantial positive false-green/semantic failures in [broad-v4 run 37419983898](https://github.com/siamese-lang/terraformers-platform/actions/runs/37419983898). Its GitHub SUCCESS conclusion is not semantic acceptance. PR #237 changed closure; PR #238 subsequently aligned evaluator/production orchestration. No complete live AI/broad-v4 remeasurement after those final corrections established final realistic acceptance. Later PT-2's partial subset does not close this gap. | PT-8A: separately gated NEW ten-case final-system realistic acceptance using broad-v4 / embedding-2 / 1536 and final generation/closure/real CLI. Each input first-submitted exactly once, natural failures retained, frozen human scoring after inference. No failing positive may be hidden by averages. |
| Final integrated release equivalence remains unproven | Accepted local browser proof and earlier AI/runtime evidence are not automatically proof of one final reviewed source/image/runtime/corpus identity. | PT-8B: only after independent PT-8A acceptance, bind reviewed source -> immutable image/source tag -> digest -> deployed BUILD_SOURCE_REVISION -> exact broad-v4 corpus/index. Compose only where equivalence permits; rerun only an actually invalidated boundary. |
| Retained resources and final closure remain outstanding | Runtime stays ACTIVE_RETAINED; A7-8 remains deferred. No destructive authority has been granted here. | PT-9: only after independent PT-8B acceptance and explicit USER destructive checkpoint, return runtime/bootstrap to reviewed final baseline and verify residual absence before independent final closure. |

The amendment intentionally selects neither identity upsert/conflict strategy nor a numeric
timeout/retry policy. Future Work Packages must freeze the supported procedure and original
acceptance before implementation/outcomes, retain natural failures and stop at declared decisions.
No verifier, workflow or test count can substitute for the original acceptance criteria.

## 4. Explicit portfolio-scope residuals

These are USER-approved non-blocking boundaries for the current portfolio target. They constrain
claims and remain explicit; they must never be silently promoted to PASS.

| Residual | Claim boundary |
| --- | --- |
| Hosted production Cognito/IdP lifecycle is not established | Deterministic signed identity and real JWT/JWKS verification do not prove hosted password/session lifecycle. No new IdP, ingress or IAM boundary is introduced. |
| Per-user/model-cost abuse quota is not established | Byte limits and bounded attempts do not prove a total authenticated cost quota. No cost-abuse protection claim. |
| 12 AWS provider schema resources lack official resource documentation | Keep explicit knowledge gaps; never fabricate official evidence. This portfolio residual does not waive PT-8A's per-case evidence requirement or turn an evidence gap into PASS. |
| PT-1 diagrams are human-reviewed reference-derived recreations | Acceptance is bounded to this frozen reviewed dataset and representative runtime, not all user exports or universal AWS-diagram generalization. |

Missing historical PT-6 events are an additional preserved evidence limitation, not a repaired
product defect. No deployment-correct IAM claim follows from a supplied authorization declaration,
provider schema, CLI validation, browser integration or the final bounded portfolio proof.

## Remaining DAG and acceptance boundaries

`PT-6R1 -> PT-6R2 -> PT-7 -> PT-8A -> PT-8B -> PT-9`

PT-6R1 becomes eligible only after this amendment is independently accepted and USER-merged. Its
substantive implementation PR binds actual remote main exactly once at activation. No future merge
SHA is predicted, no phase base is bound now and no separate state-sync/activation PR is needed.
Each subsequent phase requires independent predecessor acceptance and authorized merge; CI green
does not advance acceptance. GitHub alone holds transient active PR/branch lifecycle.

PT-8A requires **HUMAN_REQUIRED: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE** immediately
before execution. Use final post-PT-7 source/runtime identity, `terraformers-reference-v4`,
`gemini-embedding-2 / 1536`, current final production grounding closure and real Terraform CLI.
Freeze that identity/procedure before observing outcomes. Exactly ten once-only authenticated
production AnalysisJob submissions; no correctness resubmission or replacement sample.

For every positive, inspect required components, directed relationships, resource intent/cardinality,
forbidden interpretations, official generated-resource evidence closure and executable validity.
For all four controls, no trusted Terraform success is allowed. Mandatory invariants are
**false trusted success = 0**, every accepted job following the independently accepted PT-6R2
bounded terminal contract, and persisted/user-visible trust agreeing with actual semantic/technical
results. Any positive semantic/evidence/executable defect or false trusted success prevents PASS,
regardless of aggregate percentages. Classify its mechanism and stop for a bounded corrective Work
Package; only an actual correction can justify a minimum declared post-change regression, never
retry-until-green. No truth tuning from model output or new evaluator/judge is authorized.

PT-8B composes accepted PT-8A and PT-6 evidence with explicit source-equivalence. Unchanged
AI-relevant source/runtime identity does not justify ceremonial ten-case remeasurement; later code
may invalidate only specific boundaries, which require their own minimal authorized proof.
Final claims remain dataset/runtime bounded. PT-9 remains blocked on independent PT-8B acceptance
and explicit USER destructive approval.

No public ingress, new IdP/vector DB/evaluator LLM/model voting, Langfuse/OpenTelemetry,
Kubernetes redesign, HA/HPA/canary work or unrelated hardening belongs to this amendment.
The [structural validation record](../evidence/product-trust-v3-amendment/validation.json) covers
parsing, references/state consistency, allowed scope, historical preservation and whitespace only.
No ambiguity in the approval prevents recording this contract; future timeout/implementation
choices are deliberately unresolved and retain their named gates.
