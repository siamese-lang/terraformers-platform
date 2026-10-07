Continue Product Trust v3 with exactly the next mandatory phase:

`PT-6R1 — First-user identity concurrency closure`

Authoritative remote main after USER merge of PR #251:

`00872520036da38fd1809a9abf61f56d2344fcb5`

PR #251:
- merged: true
- merge commit: `00872520036da38fd1809a9abf61f56d2344fcb5`
- merged at: `2026-10-07T04:46:24Z`
- accepted review on PR #251: `6031040940`

There are currently no open PRs.

## Start gate

Before any write:

1. read remote GitHub `main` exactly once;
2. require it to equal
   `00872520036da38fd1809a9abf61f56d2344fcb5`;
3. read `AGENTS.md`;
4. read `docs/AI_PROJECT_STATE.md`;
5. read `.agents/programs/product-trust-v1.yml`;
6. read `.agents/state/product-trust-v1.json`;
7. read `docs/plans/active/product-trust-modernization.md`;
8. read:
   - `docs/evaluation/product-trust-pt-6-browser-journey.md`
   - `docs/evaluation/product-trust-pt-5-access-safety.md`
   - `docs/evaluation/product-trust-v3-cross-phase-integrity-audit.md`;
9. inspect PR #251 and latest structured review `6031040940`.

Bind PT-6R1 `execution_base_sha` exactly once to the remote main above.

Do not rebase or refresh that bound base. If main changes after activation, STOP with `HUMAN_REQUIRED: MAIN_DRIFT`.

Do not create a separate state-sync or activation PR. PT-6R1 state reconciliation, Work Package activation, evidence, implementation and validation belong in this one substantive PT-6R1 PR.

## Scope

Create/activate a bounded Work Package such as:

`.agents/work-packages/product-trust-pt-6r1-first-user-identity-concurrency-v1.yml`

The logical outcome is one thing only:

> concurrent first requests for one external provider/subject converge on one durable user with no leaked database constraint failure or privilege change.

Do not start PT-6R2.

No browser journey, Vertex/Gemini, GCP, OpenSearch, Kubernetes, Terraform apply, IAM, IdP expansion, frontend change or teardown is authorized.

## Existing evidence and code to inspect

The PT-6 evidence records a real local observation:

> concurrent first-user persistence collided on the unique external identity.

Sequencing later browser-driver calls avoided the collision but did not repair the product.

Current production code already tries to recover:

`AuthenticatedUserService.getOrCreate()`
→ lookup provider/subject
→ `createWithRetry()`
→ `userRepository.save(user)`
→ catch `DataIntegrityViolationException`
→ re-read provider/subject.

There is already a unique constraint:

`uk_users_external_identity`
on:

`(external_identity_provider, external_identity_subject)`

Migration:

`V20260923_005__neutralize_external_user_identity.sql`

The current Mockito test:

`AuthenticatedUserServiceTest.retriesSameProviderAndSubjectLookupAfterConcurrentCreate()`

simulates `userRepository.save()` throwing immediately and then verifies the re-read.

That unit test is not sufficient evidence for actual transaction behavior.

### Root-mechanism hypothesis to test, not assume

A strong current hypothesis is:

`JpaRepository.save()` does not necessarily flush the INSERT before returning.

Therefore, with the real Spring `@Transactional` service and MariaDB:

1. both transactions can observe no existing external identity;
2. both can execute `save()`;
3. `save()` may return without raising the unique violation;
4. one transaction commits;
5. the other receives the unique-constraint error during flush/transaction commit, after `createWithRetry()` has already returned;
6. the current catch/re-read path therefore cannot recover that request.

This is only a hypothesis until reproduced.

Do not preselect `saveAndFlush`, native upsert, conflict-and-reread, locking, isolation changes or another fix before the real MariaDB observation.

## Phase A — deterministic real-MariaDB before-state

Before changing production identity code, reproduce/audit the race against real MariaDB.

Reuse the existing MariaDB validation infrastructure. Prefer extending the existing:

`MariaDbRepositorySmokeTest`

rather than creating another database harness/workflow.

The existing backend CI already runs that class against a real `mariadb:11.4` service through `mariadb-schema-validation.sh`.

The test must exercise the actual Spring-proxied:

`AuthenticatedUserService`

Do **not** instantiate it with `new AuthenticatedUserService(...)`, because that bypasses the production `@Transactional` proxy and invalidates this measurement.

Use real `CognitoJwtExternalIdentityMapper` semantics with a unique test subject.

Create two independent concurrent first requests for the exact same provider/subject.

The concurrency must be deterministic enough to establish the mechanism. Synchronize the contenders at the initial absent-identity observation using test-only coordination around the existing repository boundary if necessary, while keeping actual MariaDB reads/writes/transactions real.

Do not add a production synchronization hook merely to make the test possible.

A Spring test spy/decorator may coordinate only the test's initial lookup if it still delegates all actual repository operations to MariaDB.

The before-state must establish and retain:

- both callers begin as first requests for the same external identity;
- both initial identity lookups observe absence;
- actual database persistence/transaction semantics are used;
- exact caller outcomes;
- exact exception class/root database constraint if one fails;
- whether `save()` returned before the failure;
- durable row count after both transactions;
- durable provider/subject;
- durable role/status;
- whether the existing catch/re-read path actually ran.

Use bounded barriers/timeouts. Never use arbitrary sleeps as the correctness mechanism.

Clean up all test-created rows.

If the claimed race cannot be deterministically reproduced against real MariaDB, do **not** modify production code from the hypothesis. Record the result and STOP as `INCONCLUSIVE` with the observed transaction behavior.

## Phase B — inspect the mechanism

Only after the real before-state reproduces the failure, determine the actual transaction boundary.

Specifically distinguish:

- failure thrown by `save()`;
- failure thrown by explicit flush;
- failure deferred to method/transaction commit;
- transaction marked rollback-only after a constraint failure;
- visibility of the winning row to a subsequent lookup;
- external-identity unique collision versus email unique collision.

Do not accidentally turn two different external identities sharing an email into the same user.

Preserve the existing security rule:

an email already linked to another external identity must not silently rebind accounts.

## Phase C — smallest corrective implementation

After reproduction, select the smallest correction that actually satisfies the observed MariaDB transaction semantics.

Do not choose an implementation just because the existing Mockito test suggests it.

At minimum compare the viable bounded alternatives exposed by the evidence, for example:

- explicit flush plus a safe recovery transaction boundary;
- conflict followed by re-read in an independent transaction;
- a DB-backed atomic insert/upsert form;
- another smaller repository/service transaction arrangement.

Do not use pessimistic locking of an already-existing row as a fictitious solution to the absent-row race.

Avoid increasing transaction isolation globally.

Avoid schema changes: the external-identity unique constraint already exists. Add a migration only if the evidence proves one is actually required.

Prefer provider/database-neutral application semantics where practical, but MariaDB is the selected durable database and correctness takes precedence over superficial portability.

If selecting the correction requires a meaningful new product/architecture choice, changes identity ownership semantics, changes security boundaries, or creates a material MariaDB portability tradeoff rather than a local transaction correctness repair, STOP with:

`HUMAN_REQUIRED: PRODUCT_OR_ARCHITECTURE_DECISION`

and provide the decision brief required by `AGENTS.md`.

If the evidence instead establishes one clearly bounded transaction/persistence bug with a small existing-abstraction-preserving correction, implement it in this PT-6R1 Work Package without expanding scope.

## Required after-state

Run the same deterministic real-MariaDB concurrency scenario after the actual correction.

Acceptance requires:

1. two concurrent first requests for the same provider/subject both complete successfully;
2. both resolve to the **same `userId`**;
3. exactly **one durable users row** exists for that provider/subject;
4. no raw `DataIntegrityViolationException`, SQL constraint exception or 5xx-equivalent persistence failure escapes;
5. the durable account remains:
   - role `USER`
   - status `ACTIVE`;
6. the loser of the create race cannot overwrite or elevate role/status;
7. distinct provider/subject identities remain separate users;
8. an email already owned by another external identity retains the existing rejection semantics;
9. existing explicit-display-name/fallback behavior is not silently weakened;
10. no duplicate account is created.

Record the before and after using the same concurrency conditions.

This is deterministic database regression validation, so one before-state execution and one post-correction execution are appropriate. Do not repeatedly rerun the corrected test until it happens to pass.

## Existing tests / regression preservation

Update the existing unit test only if necessary to reflect the real corrected mechanism.

Do not leave the Mockito test as the sole concurrency proof.

Reuse existing validation rather than adding another workflow.

Required proportional regression:

- focused identity unit tests;
- the new/extended real MariaDB first-user concurrency test;
- existing MariaDB schema/repository validation;
- PT-5 authorization/access regression tests relevant to owner/non-owner/anonymous/public/admin semantics;
- full backend test suite if production identity code changes;
- Flyway/schema validation if persistence mapping/repository behavior changes;
- `git diff --check`.

CI green remains supporting evidence, not acceptance.

## Allowed production paths

Keep production changes narrowly within the demonstrated identity persistence boundary, expected primarily under:

- `backend/src/main/java/com/terraformers/modernization/identity/AuthenticatedUserService.java`
- `backend/src/main/java/com/terraformers/modernization/identity/UserRepository.java`

Only add another identity-package production helper if the reproduced transaction semantics make it clearly necessary.

Expected test path:

- `backend/src/test/java/com/terraformers/modernization/verification/MariaDbRepositorySmokeTest.java`
- `backend/src/test/java/com/terraformers/modernization/identity/AuthenticatedUserServiceTest.java`

`scripts/checks/mariadb-schema-validation.sh` may change only if required to keep the demonstrated regression inside the existing MariaDB CI boundary. Prefer no script change if the existing `MariaDbRepositorySmokeTest` invocation already covers it.

No frontend/workflow/IaC change is expected.

Governance/evidence paths may include the PT-6R1 Work Package, state, plan, AI project state and one PT-6R1 evaluation/evidence document.

## Durable state

In this same substantive PR, reconcile the post-#251 state:

- Product Trust version 3 amendment is now USER-merged;
- `lastCompletedPhase = PT-6`;
- activate `PT-6R1`;
- bind its execution base to
  `00872520036da38fd1809a9abf61f56d2344fcb5`;
- no PT-6R2 activation;
- no future execution base prediction.

PT-2 remains permanently:

`PT2_INCOMPLETE_ACCEPTED_AS_FINAL_EVIDENCE_DISPOSITION`

Do not alter any PT-2 evidence or rates.

## Stop conditions

STOP rather than broadening the task if:

- remote main changes after PT-6R1 activation;
- real MariaDB does not reproduce the supported race;
- the failure mechanism differs materially from the PT-6 observation;
- a schema redesign is required;
- identity ownership/account-linking semantics need changing;
- a security/IAM/IdP decision appears;
- a meaningful architecture/product choice is required;
- a second unrelated failure class appears;
- the fix would require frontend/browser/cloud/model work;
- the same corrected deterministic failure repeats after the bounded correction.

## PR / report

Use one substantive PT-6R1 PR only.

Do not self-merge.

Return with:

- PR number;
- exact base/head SHA;
- Work Package ID;
- exact changed-file list;
- real MariaDB before-state result;
- confirmed root mechanism;
- alternatives considered after reproduction;
- selected correction and why it was the smallest correct one;
- same-condition after-state;
- user/account/security invariants preserved;
- focused/full validation results;
- exact-head CI status;
- auto-repair count;
- unresolved residuals;
- whether any HUMAN_REQUIRED decision remains.

Do not begin PT-6R2.