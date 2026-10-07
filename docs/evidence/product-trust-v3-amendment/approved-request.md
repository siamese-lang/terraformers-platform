USER approved:

`APPROVE product-trust-v1 v3 residual-closure amendment as audited above.`

Approval time: 2026-10-07T04:15:00Z.

Authoritative current main is:

`f344552fe58db26438a4f0b2c9abb1806e6a99d9`

PR #250 was independently accepted at head
`0b8f7ef0eefc472a0438a3d0eeedee0f1c919c7a`
by review comment `6030408825`, then USER-merged as the current main above.

There are currently no open PRs.

Create exactly one repository-only Product Trust Program amendment PR. Do not implement PT-6R1 or any later production change in this PR.

The purpose is to amend `product-trust-v1` from version 2 to version 3 so the remaining observed defects and final trust gaps must be closed before PT-9.

Preserve all historical evidence. In particular, PT-2 remains permanently:

`PT2_INCOMPLETE_ACCEPTED_AS_FINAL_EVIDENCE_DISPOSITION`

Do not reopen PT-2, authorize PT-2 recovery, alter its artifacts, reinterpret cases 03–10, or convert its null aggregate rates into measured values.

Record PT-6 as COMPLETE using:
- PR #250
- reviewed head `0b8f7ef0eefc472a0438a3d0eeedee0f1c919c7a`
- independent review `6030408825`
- merged main `f344552fe58db26438a4f0b2c9abb1806e6a99d9`
- completion boundary: deterministic browser integration only; no production IdP/model-quality/generalization claim.

Add a durable cross-phase integrity audit document that explicitly separates:
1. production behavior actually improved,
2. measurement/integration evidence only,
3. unresolved defects,
4. explicit portfolio-scope residuals.

The amended mandatory execution order must be:

`PT-6R1 -> PT-6R2 -> PT-7 -> PT-8A -> PT-8B -> PT-9`

PT-6R1 — First-user identity concurrency closure
- objective: close the duplicate external-identity creation race observed during PT-6;
- begin with deterministic reproduction/audit against real MariaDB concurrency, not an assumed fix;
- if reproduced, correct the actual atomicity boundary with the smallest production change;
- acceptance requires concurrent first requests for the same provider/subject to converge on exactly one durable user and the same user identity, with no 5xx/unique-constraint leak and no privilege escalation;
- distinct identities must remain distinct;
- PT-5 authorization matrix must remain intact;
- no browser/model/GCP work is required merely for this defect;
- do not preselect DB upsert vs conflict-and-reread vs another implementation before inspecting the current persistence boundary.

PT-6R2 — Provider terminality / latency-budget closure
- objective: ensure one accepted AnalysisJob cannot remain provider-bound indefinitely;
- preserve the historical PT-2 case-02 censor of 424846 ms as evidence;
- audit the actual Google GenAI client request/deadline capability and current provider/retry/job boundaries first;
- do not invent a timeout value;
- if a numeric provider/job latency budget or retry-policy tradeoff requires a product choice, STOP at a named HUMAN_REQUIRED product-decision gate with measured evidence and options;
- after the approved choice, validate timeout/hang failure injection so accepted jobs reach truthful bounded terminal semantics instead of unbounded RUNNING;
- do not claim this makes Gemini inherently fast;
- PT-4 truthful waiting/status behavior must remain unchanged or improve only if evidence requires it.

PT-7 — CI/CD trust gates and operational handoff
- audit first; do not add verifier/workflow breadth for appearance;
- current audit already found that Backend Local Verification normally runs Maven test/package but skips the production Docker build because `RUN_DOCKER_BUILD` defaults false;
- determine the smallest way to shift the existing production backend Dockerfile build left for relevant backend/Dockerfile PR changes, preferably inside an existing automatic PR workflow rather than a new workflow;
- verify Terraform 1.8.5 / AWS provider 5.100.0 image build contracts through the real Dockerfile rather than duplicating them in another bespoke verifier;
- inspect actual GitHub protected-main/ruleset required checks and reconcile only demonstrated gaps;
- do not create automatic GCP deployment on merge;
- produce a concise source SHA -> immutable image -> digest -> rollout -> evidence -> teardown handoff contract.

PT-8A — Final realistic AI/RAG trust acceptance
- this is a NEW final-system acceptance phase, explicitly NOT PT-2 recovery/reopen;
- requires a separate LIVE / MODEL / COST human checkpoint immediately before execution;
- execute against the final post-PT-7 source/runtime identity with broad `terraformers-reference-v4`, `gemini-embedding-2 / 1536`, current final production generation/grounding closure and real Terraform CLI validator;
- use frozen `terraformers-realistic-v1` revision 3 / identity `04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`;
- submit each of the ten frozen inputs through the authenticated production AnalysisJob path exactly once;
- no correctness resubmission, no rerun-until-green, no replacement sample;
- preserve natural failures;
- score only after inference against frozen human truth;
- for each of the six architecture positives evaluate required components, directed relationships, resource intent/cardinality, forbidden interpretations, generated-resource official-evidence closure and real Terraform executable validity;
- for both ambiguous and both non-architecture controls, no trusted Terraform success is allowed;
- aggregate percentages are secondary and must never hide a failing case;
- mandatory trust invariant: `false trusted success = 0`;
- mandatory terminality invariant: every accepted job follows the PT-6R2 bounded terminal contract;
- persisted quality/trust state must agree with the observed semantic/technical result;
- if any positive has a semantic defect, evidence gap, executable defect, or false trusted success, PT-8A is not PASS merely because averages are high;
- classify the failure mechanism and stop for a bounded corrective Work Package;
- after an actual correction, allow only the minimum declared post-change regression required by the affected mechanism, never retry-until-green;
- do not claim generalization beyond this frozen human-reviewed realistic dataset.

Explicitly record why PT-8A is necessary:
- broad-v4 production evaluation run `37419983898` exposed substantial positive false-green/semantic failures;
- PR #237 changed the production generated-resource evidence closure;
- PR #238 changed the evaluator to use the same production orchestration;
- no live AI/broad-v4 remeasurement was executed after those final corrections;
- therefore the corrected final AI/RAG path remains unproven on the realistic acceptance set.

PT-8B — Final integrated release proof
- eligible only after independent PT-8A acceptance;
- bind exact reviewed main SHA -> immutable backend image -> source-SHA tag -> digest -> deployed `BUILD_SOURCE_REVISION` -> exact broad-v4 corpus/index identity;
- compose accepted PT-8A semantic evidence with PT-6 browser/access evidence where source-equivalence permits;
- do not rerun the 10-case model evaluation merely for ceremony if AI-relevant source/runtime identity is unchanged;
- rerun only an affected boundary if later code actually invalidates prior evidence;
- final claim must remain bounded to the reviewed realistic dataset and representative runtime;
- do not claim universal real-world AWS-diagram generalization or deployment-correct IAM.

PT-9 — destructive teardown/final closure
- remains blocked until independent PT-8B acceptance;
- preserve existing explicit USER destructive checkpoint;
- return retained runtime/bootstrap resources to the reviewed final baseline and verify residual absence.

Explicit non-blocking residuals for the current portfolio target:
- hosted production Cognito/IdP lifecycle is not established; deterministic signed identity is not a hosted IdP proof;
- per-user/model-cost abuse quota is not established;
- 12 AWS Provider schema resources have no official resource documentation and must remain explicit knowledge gaps rather than fabricated evidence;
- PT-1 realistic fixtures are human-reviewed reference-derived recreations, not a statistical sample of all real user exports;
- these boundaries must constrain final claims and must not be silently promoted to PASS.

Do not add public ingress, new IdP, new vector DB, evaluator LLM, model voting, Langfuse/OpenTelemetry, Kubernetes redesign, HA/HPA/canary work, or unrelated hardening.

Amend only governance/state/plan/evidence files necessary to make this version-3 contract durable. Do not change backend/frontend/workflows/IaC in this amendment PR.

Do not create a separate state-sync/activation PR after this one. The v3 amendment PR itself is the substantive reconciliation of PT-6 completion and the audited residual plan.

After this amendment is independently accepted and USER-merged, PT-6R1 may be activated from that new main in its substantive implementation PR. Bind its execution base once at that time; do not predict or hardcode a future merge SHA now.

Validation for this amendment is limited to structural YAML/JSON parsing, cross-reference/state consistency, allowed-scope verification and `git diff --check`. No model, browser, live GCP, IAM, workflow dispatch or destructive action is authorized.

Return with:
- PR number
- exact base/head SHA
- exact changed-file list
- concise explanation of the revised DAG
- validation result
- any ambiguity that would prevent the amendment from faithfully representing the approved audit.

Do not self-merge.