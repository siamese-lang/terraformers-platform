Start **PT-7 — CI/CD trust gates and operational handoff** from current authoritative main.

This is one substantive repository Work Package and one PR.

Do not perform any GCP deployment, image publication, OpenSearch operation, model call, IAM mutation or teardown in this step.

## 0. Mandatory start gate

Expected current remote main after USER-merged PR #254:

`6228d69b1da604e816b3e0a826563d79b8bcd844`

Read remote GitHub `main` exactly once at activation and bind that exact SHA as the PT-7 `execution_base_sha`.

If remote main differs before binding, inspect why. Once bound, any later drift is:

`HUMAN_REQUIRED: MAIN_DRIFT`

Read:

- `AGENTS.md`
- startup skill
- `.agents/programs/product-trust-v1.yml`
- `.agents/state/product-trust-v1.json`
- `docs/plans/active/product-trust-modernization.md`
- `docs/AI_PROJECT_STATE.md`
- `docs/evaluation/product-trust-v4-evidence-validity-audit.md`
- `docs/evaluation/case-c-immutable-backend-image-delivery-readiness.md`
- merged PR #254
- independent PT-6R2 acceptance review `6037528315`
- `.github/workflows/backend-local-verification.yml`
- `.github/workflows/terraform-static-verification.yml`
- `.github/workflows/gcp-backend-image-publish.yml`
- `.github/workflows/gcp-target-runtime-dependencies.yml`
- `scripts/checks/backend-local-verification.sh`
- `scripts/checks/ci_changed_scope.py`
- `backend/Dockerfile`
- current GitHub `main` ruleset / required-status-check configuration.

## 1. Reconcile PT-6R2 completion in this substantive PR

Do not create a state-sync-only PR.

Reconcile:

- PR #254 USER merge;
- reviewed head `987ec944c84ac74f99f2c2f3ae38b0bc16499f8a`;
- independent acceptance review `6037528315`;
- merged main `6228d69b1da604e816b3e0a826563d79b8bcd844`;
- Option D accepted terminality contract;
- PT-6R2 status `COMPLETE`.

Activate PT-7 on the once-bound main.

Do not alter PT-6R2 historical evidence, counters or accepted claim boundary.

## 2. Audit the demonstrated PT-7 gaps first

Confirm from exact source and GitHub settings:

### Gap A — production image shift-left

Expected present behavior:

- `Backend Local Verification` runs on backend-related PR changes;
- `scripts/checks/backend-local-verification.sh` defaults:
  `RUN_DOCKER_BUILD=false`;
- therefore ordinary backend PR verification does not build the actual `backend/Dockerfile`.

Confirm before editing.

### Gap B — merge protection

Expected current main ruleset:

`protect-main-for-delivery`

Expected required status checks:

- `terraform-static-verification`

and no backend validation required context.

Confirm via GitHub ruleset API/settings before editing.

If reality differs, adapt to observed state rather than this instruction.

## 3. Shift the real production Docker build left, conditionally

Use the existing `Backend Local Verification` workflow.

Do not create a new Docker-validation workflow.

The real authority must be:

`backend/Dockerfile`

A PR must build that real Dockerfile whenever the PR changes production image inputs.

At minimum treat these as production-image-affecting:

- `backend/Dockerfile`
- `backend/pom.xml`
- `backend/src/main/**`

Do not require a full Docker build merely for:

- backend test-only changes;
- documentation;
- governance/state changes;
- unrelated frontend/infra changes.

The existing backend Maven tests/package behavior remains unchanged.

When Docker validation is selected:

- build the real `backend/Dockerfile`;
- pass the exact PR head SHA as `BUILD_SOURCE_REVISION`;
- verify the built image contains:
  `BUILD_SOURCE_REVISION=<exact PR head SHA>`;
- let the Dockerfile itself remain the authority for:
  - Terraform 1.8.5;
  - AWS provider 5.100.0;
  - checksum verification;
  - provider schema extraction;
  - positive Terraform validate;
  - negative invalid-provider-argument validation.

Do not duplicate those Dockerfile checks in Python/string-verifier code.

Do not push the PR image anywhere.

Do not publish `latest`.

Do not access GCP.

Prefer extending the existing `backend-local-verification.sh` minimally rather than duplicating its behavior in another script.

## 4. Make backend merge protection technically stable before asking to change the ruleset

A GitHub required check must exist on **every PR**, otherwise unrelated PRs can become permanently blocked.

The current backend workflow has a top-level `paths` filter, so its existing jobs cannot simply be added to the ruleset as-is.

Correct this inside the **existing backend workflow** with the smallest stable design.

Preferred structure:

### `scope`

A lightweight job that always runs on PRs to `main`.

Determine whether backend verification is required from the same path set currently represented by the workflow:

- `backend/**`
- `scripts/checks/backend-local-verification.sh`
- `scripts/checks/mariadb-schema-validation.sh`
- `scripts/checks/flyway-migration-uniqueness.sh`
- `.github/workflows/backend-local-verification.yml`

Also determine separately whether the real Docker build is required:

- `backend/Dockerfile`
- `backend/pom.xml`
- `backend/src/main/**`

Reuse existing changed-scope machinery where sensible. Do not create a generic workflow framework.

### Existing backend jobs

When backend verification is required:

- run the existing backend local smoke baseline;
- run the existing MariaDB schema/repository validation;
- request the real Docker build only when production-image-affecting files changed.

When backend verification is not required:

- do not spend time running Maven/MariaDB/Docker unnecessarily.

### Stable final gate

Add exactly one tiny final job/check in this existing workflow, with a stable name such as:

`backend-required-verification`

It must always be created for PRs to `main`.

Its semantics:

- if backend verification was not required: PASS;
- if required:
  - backend local smoke must be SUCCESS;
  - MariaDB schema/repository validation must be SUCCESS;
  - production Docker build must have been included when the production-image scope required it;
- skipped/failed/cancelled required work must not produce a false PASS.

This stable final check exists specifically because GitHub required status checks cannot safely require a path-filtered job that may not exist.

Do not add multiple new summary/verifier jobs.

Do not create another automatic PR workflow.

Keep automatic PR workflow policy intentionally small.

## 5. Do not mutate the GitHub ruleset yet

Repository implementation may prepare the stable context, but the current USER instruction does **not** authorize changing main branch protection/ruleset.

Once the new stable check has run successfully on the PR exact head, stop at:

`HUMAN_REQUIRED: PT7_REQUIRED_CHECK_RULESET_CHANGE`

Prepare the exact proposed GitHub setting:

Existing required check remains:

`terraform-static-verification`

Add exactly:

`backend-required-verification`

Do not remove or weaken the existing Terraform check.

Do not add every child backend/MariaDB/Docker job individually.

Do not alter:
- required review count;
- merge methods;
- deletion protection;
- non-fast-forward protection;
- bypass actors.

The eventual ruleset mutation requires explicit USER approval.

## 6. Preserve the existing main-only delivery path

Audit and document, but do not execute, the existing handoff.

The existing intended path is:

`reviewed main`
→ `GCP Backend Image Publish`
→ exact full source-SHA tag
→ remote `sha256` digest
→ immutable digest-qualified backend image
→ explicit retained-GCP `backend-revision-rollout`
→ deployed image equality
→ runtime `BUILD_SOURCE_REVISION`
→ v4 runtime configuration identity
→ later PT-8A corpus/retrieval readiness.

Confirm the existing workflows already enforce the important boundaries.

At minimum record that image publish:

- is manual `workflow_dispatch`;
- runs only from trusted `main`;
- requires `expected_sha == GITHUB_SHA`;
- builds the actual `backend/Dockerfile`;
- embeds `BUILD_SOURCE_REVISION`;
- refuses source-SHA tag movement;
- publishes no `latest`;
- resolves the remote digest;
- performs no Kubernetes/Terraform mutation.

Confirm retained rollout:

- is separately manual/gated;
- requires immutable digest image;
- binds `backend_source_sha`;
- verifies source-SHA tag → target digest provenance;
- verifies expected current image before replacement where applicable;
- rolls out the exact digest;
- verifies deployed image and `BUILD_SOURCE_REVISION`;
- verifies the required v4 runtime configuration identity.

Do not redesign working delivery mechanics merely for PT-7.

## 7. Produce a PT-8A delivery handoff contract

Use one concise PT-7 evidence/runbook document.

It must state that after PT-7 is independently accepted and USER-merged, PT-8A still cannot consume any frozen AWS official input until all of the following occur under the appropriate later human gate:

1. choose the exact reviewed PT-8A source SHA;
2. run the existing main-only image-publish path for that SHA;
3. capture exact source tag, remote digest and immutable image reference;
4. verify retained GCP current runtime before mutation;
5. run the existing reviewed backend revision rollout using that exact digest/source SHA;
6. verify deployed image and:
   `BUILD_SOURCE_REVISION == reviewed source`;
7. verify actual deployed runtime configuration:
   - `CORPUS_VERSION=terraformers-reference-v4`
   - `INDEX_NAME=terraformers-reference-v4`
   - `VERTEX_EMBEDDING_MODEL_ID=gemini-embedding-2`
   - vector dimensions `1536`;
8. only then perform PT-8A OpenSearch exact-equivalence/retrieval readiness;
9. only after that and the separate live/model/cost gate may the five frozen AWS official images be consumed.

PT-7 itself performs none of these live operations.

## 8. Retained runtime and cost boundary

Preserve:

- existing retained representative GCP runtime is preferred for PT-8A;
- no second fake/local/cloud runtime;
- no deploy-on-merge;
- no publish-on-merge;
- no automatic model invocation;
- no automatic OpenSearch ingestion.

Image publication, GCP rollout, OpenSearch readiness/repair, model calls and final official inputs remain later explicit human checkpoints.

## 9. Scope discipline

Allowed substantive areas should remain narrow:

- PT-7 Program/state/Work Package/plan/evidence;
- `.github/workflows/backend-local-verification.yml`;
- `scripts/checks/backend-local-verification.sh`;
- `scripts/checks/ci_changed_scope.py` only if genuinely needed for the stable existing-workflow scope classification;
- narrowly associated tests for changed scope logic;
- PT-7 handoff documentation.

Do not modify:

- production backend behavior;
- model/prompt/retrieval code;
- frozen AWS official candidate;
- Terraform/GCP runtime resources;
- image-publish/live-runtime behavior unless the audit finds an actual contract bug;
- frontend;
- SDK versions;
- PT-6R2 accepted implementation.

If the existing publish/rollout workflow contains a demonstrated defect that prevents the documented source→digest→deployed revision chain, record it and stop rather than expanding scope silently.

## 10. Validation before the ruleset gate

Run deterministic repository validation only.

At minimum prove on the PR:

### Non-backend change fixture
- stable `backend-required-verification` exists;
- expensive backend/MariaDB/Docker work is not required;
- final backend gate passes.

### Backend test-only fixture
- backend verification is required;
- Docker build is not required merely because a test changed.

### Production backend source fixture
- backend verification is required;
- production Docker build is required.

### Dockerfile/pom fixture
- production Docker build is required.

### Exact-head PR execution
For this actual PT-7 PR:
- normal automatic workflow, no manual workflow rerun;
- stable final backend gate is present;
- if this PR changes the workflow/script itself, it must exercise the appropriate backend validation path;
- real Dockerfile build must run if the changed-scope contract selects it;
- normal Terraform Static Verification remains intact.

Do not use `[skip ci]`.

Do not rerun until green.

One ordinary bounded repair remains subject to Program repair policy.

## 11. Stop before branch-ruleset mutation

Once:

- repository implementation is complete;
- exact-head CI has produced the new stable backend check successfully;
- delivery handoff evidence is prepared;

STOP at:

`HUMAN_REQUIRED: PT7_REQUIRED_CHECK_RULESET_CHANGE`

Return:

- bound PT-7 execution base;
- PR number/head SHA;
- exact changed files;
- observed current ruleset required checks;
- exact proposed new required check;
- evidence that the new context exists and is stable on the PR;
- backend Docker shift-left behavior;
- actual CI results;
- source→publish→digest→rollout handoff audit;
- residual risks;
- confirmation of zero GCP/model/OpenSearch/live actions.

Do not self-approve.
Do not merge.
Do not modify the GitHub ruleset without explicit USER approval.
Do not start PT-8A.