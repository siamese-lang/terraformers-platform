Prepare **PT-8A — Final external official AI/RAG deployed acceptance** from the current authoritative main.

This task is **repository-only PT-8A preparation**.

It must freeze the final procedure and close any repository-side readiness-evidence gap **before model outcomes exist**.

Do not execute the live acceptance yet.

## 0. Mandatory start gate

Expected current remote main after USER-merged PR #255:

`2bdb73d20486262856bfa6b680b7bf221fa23ca0`

Read remote `main` exactly once at Work Package activation and bind that exact SHA as the PT-8A preparation `execution_base_sha`.

Do not require a synthetic/local Codex checkout SHA to equal this remote SHA.

Once bound, later unexpected main drift is:

`HUMAN_REQUIRED: MAIN_DRIFT`

Read at minimum:

- `AGENTS.md`
- startup skill
- `.agents/programs/product-trust-v1.yml`
- `.agents/state/product-trust-v1.json`
- `docs/plans/active/product-trust-modernization.md`
- `docs/AI_PROJECT_STATE.md`
- merged PR #255
- PT-7 independent acceptance review `6038261202`
- `docs/evaluation/product-trust-pt-7-ci-delivery-handoff.md`
- `docs/evaluation/product-trust-v4-evidence-validity-audit.md`
- `docs/evaluation/product-trust-aws-official-truth-candidate.md`
- `evaluation/terraformers-aws-official-v1/candidate-manifest.json`
- `evaluation/terraformers-aws-official-v1/candidate-identity.json`
- current A7-7 broad-v4 Work Package/evidence
- current corpus build/ingestion tests
- current GCP image-publish/runtime/OpenSearch/live-validation workflows.

## 1. Reconcile PT-7 completion in this substantive PR

Do not create a separate state-sync PR.

Record:

- PR #255 reviewed head:
  `b8645e564d4476c9ad8aad798d63d2a84b487787`
- independent acceptance:
  `6038261202`
- USER merge:
  `2bdb73d20486262856bfa6b680b7bf221fa23ca0`
- required checks now protected on main:
  - `terraform-static-verification`
  - `backend-required-verification`
  - both GitHub Actions integration `15368`.

Mark PT-7 COMPLETE.

Prepare PT-8A as the next Work Package, but keep:

`live_execution_authorized: false`

until explicit USER approval of:

`FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE`

Do not predict or hard-code the future post-merge PT-8A live source SHA.

The live reviewed source SHA will be bound only after this preparation PR is independently accepted and USER-merged.

## 2. Preserve the exact frozen external acceptance set

Frozen candidate:

- dataset: `terraformers-aws-official-v1`
- revision: `2`
- identity:
  `3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757`
- cases in exact order:
  - A
  - B
  - C
  - D
  - E
- positives: 5
- model-under-test runs so far: 0.

Do not edit the identity-bound candidate/truth files.

Do not replace:
- URLs;
- raw SHA256 values;
- dimensions;
- case order;
- image-observable truth;
- documentation context;
- technical-closure contract.

Do not fetch a substitute image.

No redraw, crop, restyle or synthetic replacement is allowed.

## 3. Freeze the final PT-8A procedure before outcomes

Prepare one human-reviewable procedure that fully determines what will happen after the live gate.

The procedure must distinguish:

### Phase A — release/readiness

Before any official image is submitted:

1. bind exact reviewed post-merge PT-8A main SHA;
2. publish the real `backend/Dockerfile` through the existing main-only image-publish workflow;
3. capture:
   - full source-SHA tag;
   - remote sha256 digest;
   - immutable image reference;
   - embedded `BUILD_SOURCE_REVISION`;
4. inspect retained GCP runtime before mutation;
5. roll out exactly that source/digest using the existing `backend-revision-rollout`;
6. prove:
   - deployed immutable image equality;
   - `BUILD_SOURCE_REVISION == reviewed source SHA`;
   - actual runtime v4 configuration;
7. prove exact retained OpenSearch v4 equivalence;
8. prove correlated deployed-backend v4 retrieval readiness;
9. only then permit case A.

### Phase B — frozen official cases

Submit A→E sequentially through the real authenticated persisted production path.

Each input receives **one first submission only**.

No result-driven resubmission.

No replacement sample.

No truth/prompt tuning after seeing an output.

### Phase C — post-inference scoring

Only after inference, score against frozen human truth.

Do not expose frozen expected semantic truth to the model, prompt or RAG corpus.

No LLM judge.

Use deterministic checks where meaningful plus explicit human semantic review.

## 4. Close the exact-v4 equivalence gap

This is a demonstrated evidence gap and must be fixed before live execution.

Current ingestion behavior must be treated accurately:

`ingest-gcp-target-corpus.py`

currently does approximately:

- HEAD document ID;
- if ID exists, skip it;
- it does not read and compare the existing document body.

Therefore:

- matching index name;
- vector dimension 1536;
- mapping corpus checksum;
- term count 5395

are **not sufficient** to prove exact retained serving contents.

Implement the smallest reusable exact-equivalence mechanism.

Prefer extending existing RAG tooling rather than creating a broad new verification framework.

The live verifier must be able to establish all of the following without issuing new document embedding requests:

### Mapping identity

Verify at least:

- exact index name `terraformers-reference-v4`;
- expected mapping fields/types;
- kNN vector dimension `1536`;
- expected kNN method contract;
- corpus-version metadata;
- corpus checksum metadata.

### Exact document universe

Do not check only:

`count(corpusVersion=v4) == 5395`

Also prove:

- total expected document IDs = 5395;
- live expected-ID set equals the rebuilt expected-ID set;
- no expected ID is missing;
- no unexpected/stale document exists in the v4 index.

### Exact non-vector source content

Read the live indexed source for all expected documents while excluding the vector field.

Canonicalize deterministically and compare it with the expected locally rebuilt corpus.

At minimum compare every persisted non-vector field relevant to retrieval/provenance, including:

- `documentId`;
- corpus version;
- resource/service/type fields;
- title;
- content;
- source URL/provenance fields;
- any other committed indexed metadata.

A changed document with the same ID must fail.

Do not upload or commit all live document bodies merely as evidence.

The durable evidence may retain only bounded hashes/counts/diff summaries.

### Content identity

Compute a deterministic canonical identity for the expected non-vector corpus and the fetched live non-vector corpus.

Exact equality is required.

Do not use only the pre-existing mapping `_meta.checksum` as proof of the live bodies.

## 5. Embedding-model provenance must not be inferred

A 1536-dimensional vector does **not** prove it was created with `gemini-embedding-2`.

Audit the historical A7-7 evidence and GitHub Actions artifacts for a successful corrected-v4 ingestion receipt that can be immutably bound to:

- exact corpus checksum;
- exactly 5395 documents;
- `gemini-embedding-2`;
- vector dimension 1536;
- the current retained v4 index;
- successful completed ingestion.

This is read-only GitHub evidence inspection, not a live GCP query.

If such evidence exists and is sufficiently bound, define exactly how PT-8A later validates it together with current live content equality.

If it does **not** exist or cannot be tied reliably to the retained index:

classify future readiness as:

`MODEL_PROVENANCE_UNPROVEN`

Do not fix that by writing new `_meta` values onto the old index.

Do not infer provenance merely from dimension, count or current backend configuration.

In that case the later live procedure must require a clean reviewed v4 rebuild/ingestion using the existing approved path after USER live/model/cost authority.

## 6. Explicit readiness classification

Before an official input is consumed, exactly one class must be produced:

- `EXACT_REUSABLE_COMPLETED_V4`
- `PARTIAL_INDEX`
- `STALE_OR_MIXED_MODEL_SPACE`
- `WRONG_MODEL_OR_DIMENSION`
- `MISSING_INDEX`
- `MODEL_PROVENANCE_UNPROVEN`

Only:

`EXACT_REUSABLE_COMPLETED_V4`

may continue directly to official case execution.

For every other class, stop before case A.

Do not automatically rebuild or re-embed merely because readiness fails.

Any Vertex embedding/re-ingestion is live/model/cost work under the later USER gate.

## 7. Preserve deterministic broad-v4 authority

Expected v4 contract remains:

- AWS Provider schema resources: 1526
- officially documented: 1514
- selected documented: 1514
- extraction gaps: 0
- provider chunks: 5387
- project decisions: 8
- total documents: 5395
- provider source commit:
  `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`
- embedding model:
  `gemini-embedding-2`
- dimension:
  `1536`
- index/corpus:
  `terraformers-reference-v4`.

Do not silently change these numbers.

## 8. Prepare the official-case execution path using existing delivery/runtime workflow boundaries

Do not create a new GitHub workflow merely for PT-8A.

Prefer adding a bounded PT-8A operation to the existing protected runtime/live-validation workflow and narrowly scoped scripts where necessary.

The final live operation must be impossible to trigger through pull_request or push.

It must remain:

- manual;
- trusted-main only;
- explicit confirmation;
- protected GCP environment;
- exact expected main SHA;
- exact immutable backend digest/source SHA.

Do not execute it now.

## 9. Official-image acquisition contract

At live execution time, for each case:

1. use the exact frozen AWS-hosted image URL;
2. require the expected AWS host;
3. require HTTP success;
4. verify exact raw SHA256 before upload;
5. verify expected media type;
6. verify frozen dimensions;
7. fail before product submission on mismatch.

Do not silently update the manifest.

Do not persist AWS image bytes into the repository.

Do not include the image bytes in the final GitHub evidence artifact unless redistribution authority is established.

Hashes/URLs/dimensions/status are sufficient evidence of input identity.

For repository tests now, use local synthetic byte fixtures/mocks.

Do not download the five production candidate images merely to make the preparation tests pass.

## 10. Authenticated product execution contract

For each future official case, use the real product path:

`authenticated image upload`
→ persisted source object
→ durable `AnalysisJob`
→ image fact extraction
→ query embedding
→ exact live v4 OpenSearch retrieval
→ generation
→ generated-resource evidence closure
→ optional single grounding repair
→ real Terraform CLI draft validation
→ persisted result
→ persisted `evidence-quality-v1`
→ authenticated API readback.

Do not bypass the AnalysisJob API by calling the model directly.

Record at least:

- case ID;
- source URL + SHA;
- project/file/job/result IDs;
- accepted timestamp;
- terminal timestamp;
- terminal state;
- PT-6R2 deadline/terminal classification;
- stage latency available from the accepted product path;
- result quality contract and reasons;
- generated Terraform result;
- correlated retrieval evidence identity/count/provenance;
- real CLI init/validate result.

Do not record credentials/JWT/private keys.

## 11. Real Terraform CLI validation

The final HCL must be validated against:

- Terraform `1.8.5`
- AWS provider `5.100.0`.

Use the already accepted backend image/Dockerfile pin authority.

Do not AWS `plan` or `apply`.

Reviewable draft semantics remain:

- unknown account/user-specific values may be declared variables;
- coherent external references are allowed;
- editable valid placeholder inputs are allowed;
- fabricated concrete identifiers are not rewarded;
- missing visible core architecture or required chosen-resource wiring remains a defect.

## 12. Preserve first-submission evidence

Prepare the live runner so it treats a successfully accepted product request as consumption of that case's one official submission.

Infrastructure/preflight failure **before the product accepts the case** does not consume the case.

Once an authenticated AnalysisJob is accepted:

- preserve its natural result;
- do not rerun that case for correctness;
- do not replace it;
- do not suppress FAILED/timeout/degraded results.

Guard same-run GitHub reruns where practical, e.g. reject `GITHUB_RUN_ATTEMPT != 1`.

Do not invent a large new persistence/locking system solely to prevent a malicious second manual dispatch; the reviewed operational protocol and durable evidence remain authoritative.

## 13. Material-defect behavior

Freeze this before outcomes.

If one official case produces a material semantic/technical/trust defect:

- record `NOT_PASS`;
- preserve its first submission evidence;
- stop further correctness execution;
- do not resubmit it;
- return for a new bounded corrective Work Package.

Already completed earlier official cases remain historical evidence.

Unsubmitted later cases remain unsubmitted, not failed and not passed.

No aggregate percentage may hide the defect.

## 14. Freeze the ten scoring dimensions

Exactly these dimensions:

1. architecture classification
2. core components/services
3. directed relationships
4. material containment/cardinality
5. forbidden/invented interpretations
6. Terraform resource intent
7. unknown/personalized-input handling
8. generated-resource official evidence closure
9. real Terraform CLI REVIEWABLE_IAC_DRAFT validity
10. persisted/user-visible trust consistency

Semantic mandatory authority:

`image_observable_truth`

AWS documentation page text:

`documentation_context`

only.

It cannot add invisible semantic requirements.

Technical closure is conditional on the generated resource/relationship choices.

`FALSE_TRUSTED_SUCCESS` must remain zero.

## 15. Evidence artifact design

Prepare a bounded artifact format before outcomes.

It must contain enough for independent review, including generated HCL and relevant sanitized product/API/retrieval evidence.

It must not contain:

- JWTs;
- private keys;
- access tokens;
- cloud credentials;
- embedding vectors;
- secret values.

Do not embed frozen truth labels into the inference/request payload.

Keep the human truth document separate and use it only after inference.

## 16. Do not accidentally promote historical evidence

Preserve:

- PT-1 as controlled regression only;
- PT-2 FINAL INCOMPLETE;
- case-02 `424846 ms` censor;
- cases 03–10 NOT_RUN;
- prior broad-v4 evaluation as historical evidence;
- A7-7 as mechanism evidence;
- PT-6 local browser evidence as browser mechanism evidence;
- PT-6R1 MariaDB evidence as concurrency mechanism evidence;
- PT-6R2 as terminality mechanism;
- PT-7 as delivery/merge-gate mechanism.

None of them substitutes for PT-8A external official deployed evidence.

## 17. No live actions in this preparation PR

Strictly zero:

- image publication;
- Artifact Registry push;
- Kubernetes rollout;
- GCP runtime mutation;
- GCP live inventory/readiness query;
- live OpenSearch access;
- corpus ingestion/re-embedding;
- Vertex/Gemini requests;
- official AWS product submissions;
- browser execution;
- teardown;
- IAM/security changes.

Normal repository PR CI is allowed.

Read-only GitHub history/artifact inspection is allowed.

## 18. Repository validation

Add only proportional deterministic tests.

At minimum cover:

- exact expected document set succeeds;
- same IDs/count but one changed non-vector document fails;
- one missing + one unexpected document fails even when count remains 5395;
- wrong vector dimension fails;
- wrong/missing corpus checksum fails;
- missing trustworthy embedding-model provenance cannot classify as reusable;
- exact frozen image identity parser rejects wrong hash/media/dimensions;
- procedure keeps truth data out of inference request construction;
- once-only case-state logic distinguishes pre-acceptance infrastructure failure from accepted job outcome;
- material-defect stop preserves later cases as NOT_RUN;
- existing v3 ingestion behavior is not weakened if shared code changes.

Do not attempt a real 5395-document embedding run.

## 19. Scope discipline

Prefer a small number of directly necessary files.

Potentially justified areas:

- one PT-8A Work Package;
- Program/state/plan reconciliation;
- PT-8A procedure/evidence schema;
- existing RAG ingestion/equivalence tooling and tests;
- a narrowly scoped official-acceptance runner;
- existing GCP runtime/live-validation workflow operation.

Do not:

- add an LLM evaluator;
- add another vector DB;
- add LangGraph;
- redesign backend generation;
- change prompts based on historical expected answers;
- modify frozen truth;
- change embedding model;
- introduce new runtime architecture;
- add automatic GCP deploy;
- create a new workflow solely for PT-8A;
- create a state-sync-only PR.

If closing exact equivalence requires a materially broader architecture choice, STOP rather than expanding scope.

## 20. End state of this PR

The preparation PR must end at:

`HUMAN_REQUIRED: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE`

Return:

- bound preparation execution base SHA;
- PR number/head SHA;
- exact changed files;
- PT-7 reconciliation;
- frozen PT-8A procedure;
- exact-equivalence gap and implementation;
- historical ingestion/model-provenance audit result;
- readiness classification logic;
- official input acquisition contract;
- once-only submission behavior;
- artifact/evidence schema;
- deterministic test results;
- normal required PR CI results;
- confirmation of zero live/model/GCP/OpenSearch/official-input actions.

Do not self-accept.
Do not merge.
Do not publish/deploy.
Do not run readiness against retained GCP yet.
Do not start official case A.

Stop at:

`HUMAN_REQUIRED: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE`