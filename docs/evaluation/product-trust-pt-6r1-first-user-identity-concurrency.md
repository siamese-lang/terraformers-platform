# PT-6R1 — first-user identity transaction recovery

**HUMAN_REQUIRED: MERGE_CHECKPOINT.** [Review 6031593870](https://github.com/siamese-lang/terraformers-platform/pull/252#issuecomment-6031593870)
accepts the engineering implementation and actual MariaDB before/after evidence at
`9e668a5cd4bce17e2808b82b2521cc58c6d08894`. The governance correction's independent acceptance and
USER merge remain pending. The first measurement failure below remains historical evidence.

## Authority and bound base

USER approved exactly PT-6R1 after [review 6031040940](https://github.com/siamese-lang/terraformers-platform/pull/251#issuecomment-6031040940)
accepted v3 amendment head `360fc827e2b6146f0961442b53e3eb172a8aa4e8`, then USER merged PR #251
at `2026-10-07T04:46:24Z` as `00872520036da38fd1809a9abf61f56d2344fcb5`.
Remote main was read once and bound to that exact SHA. The
[Work Package](../../.agents/work-packages/product-trust-pt-6r1-first-user-identity-concurrency-v1.yml)
freezes the outcome, acceptance, bounded coordination and one before/one after procedure in this
substantive phase branch. No rebase/refresh/rebind or separate activation/state-sync PR.

The supported prior finding is a local unique external-identity collision during PT-6.
`AuthenticatedUserService` tries lookup -> save -> integrity-exception catch -> identity re-read.
The existing Mockito unit test is not actual MariaDB transaction evidence. `UserEntity` uses
`GenerationType.IDENTITY`, and the existing external-identity/email constraints are preserved;
neither deferred-commit nor immediate-save/rollback behavior is assumed proven.

## The preserved first attempt

Reused the existing cached CI MariaDB 11.4 image, bound to immutable digest
`sha256:1292844148b311e4ed4300022a996d39083f415a963e970cf47cad1b3b18e3a6`, in an isolated local
database-only container. Actual server: `11.4.13-MariaDB-ubu2404`, isolation `REPEATABLE-READ`.
The existing production Spring test context runs Flyway and Hibernate schema validation.
No cloud, model, browser, IdP or workflow dispatch is involved.

Executed once:

```sh
source /tmp/pt6r1-db-env.sh
mvn -o -f backend/pom.xml '-Dtest=MariaDbRepositorySmokeTest#concurrentFirstExternalIdentityRequestsConvergeAgainstMariaDb' test
```

The test injects the actual Spring-proxied service and real Cognito identity mapper. However, its
initial `@SpyBean` instrumentation used `invocation.callRealMethod()` for the derived repository
query. This fails with `org.mockito.exceptions.base.MockitoException: Cannot call abstract real
method on java object!` before either initial identity lookup delegates to MariaDB. Both callers
receive that measurement error. Save is never invoked; absence/barrier coordination is never
established. The expected product convergence assertion fails: 1 test, 1 failure, 0 errors/skips;
Maven exit 1. This is an instrumentation failure, **not** a reproduced product uniqueness failure.

The independent JDBC durable readback does run and sees zero rows for the unique test identity.
Cleanup leaves zero matching rows, and both caller threads stop. These read-only/cleanup facts do
not substitute for the missing service-level lookup/create/transaction measurement.

[Raw derived result](../evidence/product-trust-pt-6r1/before-state-measurement-failure.json),
[exact log bytes](../evidence/product-trust-pt-6r1/before-state-measurement-failure.log.gz), and
[original test source bytes](../evidence/product-trust-pt-6r1/before-state-test-source.java.gz)
preserve the failed attempt. Nothing is converted into a passing or unrun attempt.

## Historical test-only preparation and permission stop

One bounded test-only correction replaces the spy with a test-context repository decorator. Its
interceptor uses `MethodInvocation.proceed()` through the real repository proxy for every operation;
only the initial real absent lookup may wait at the bounded test barrier. No production hook, fake
repository result, database mutation, transaction/isolation override or service reconstruction is
introduced by this decorator. At that preparation point, production identity code remained byte-identical to the bound base.

The corrected test source compiles. Existing `AuthenticatedUserServiceTest`,
`CognitoJwtExternalIdentityMapperTest` and `ExternalIdentityMigrationTest`: **14 PASS**, zero
failures/errors/skips, offline Maven exit 0. [Draft validation](../evidence/product-trust-pt-6r1/draft-validation.json)
binds the command, source/log hashes and limitation. This does **not** validate the decorator at
runtime or establish concurrent identity safety.

At that stop, no second before-state or after-state had been executed. An explicit request for one additional
before-state execution was pending because the declared single attempt was consumed. The instruction was: do not create
a PR that automatically runs the revised real-MariaDB test before that additional authority is
resolved. Independent review/CI are not substitute execution permissions.

At that stop the product root mechanism was **UNCONFIRMED**. Explicit flush with safe recovery, independent
transaction re-read, DB-backed atomic insert/upsert and another minimal transaction arrangement
remain unselected; compare them only after a valid real-MariaDB reproduction. No email/account
linking, role/status, display-name, schema or global isolation policy change is made.

PT-2 remains final INCOMPLETE with 03–10 NOT_RUN, null aggregate rates, original 424846 ms censor
and zero-product recovery failures. All original evidence/frozen bytes and prior phase counters
remain preserved. PT-6R2 is not activated; no future phase base is predicted. GitHub owns transient
PR/branch lifecycle. Current autonomous repair count: 1, entirely test instrumentation; human
corrective iterations at that stop: 0 pending additional authority. No accepted product result is claimed.


## USER-authorized additional before-state and confirmed mechanism

USER explicitly replied `추가 before-state 1회 승인`. Human corrective iteration 1 permits exactly
one additional before execution; autonomous instrumentation repair stays 1 and is not reset.
The additional execution finished at `2026-10-07T05:08:17Z`: one expected convergence failure,
zero errors/skips, exit 1. [Before result](../evidence/product-trust-pt-6r1/before-state.json),
[raw log](../evidence/product-trust-pt-6r1/before-state.log.gz) and
[executed source](../evidence/product-trust-pt-6r1/before-state-test-source-approved.java.gz)
bind the unchanged production and corrected test-only observation.

Both real initial identity SQL lookups observe absence inside actual Spring transactions. One
IDENTITY INSERT returns and commits; the other `save()` throws `DataIntegrityViolationException`
immediately (SQL state 23000, MariaDB 1062, `uk_users_cognito_sub`, the rollback-compatible mirror
of this same Cognito identity). There is no email in this scenario. The failed transaction is
rollback-only. The existing catch DOES attempt the second provider/subject lookup, but Hibernate
autoflush on the damaged persistence context throws `AssertionFailure: null id ... don't flush
the Session after an exception occurs`. One caller succeeds, one fails; one durable USER/ACTIVE
row remains, then test cleanup removes it. Both threads stop. This refutes deferred-commit as the
mechanism for this IDENTITY mapping; it confirms the supported first-user persistence/recovery gap.

## Alternatives and bounded decision (recorded before production correction)

| Alternative | Assessment from this reproduction |
| --- | --- |
| `saveAndFlush` alone | INSERT already occurs at save; another flush cannot repair rollback-only session reuse. |
| Clear session / re-read inside the failed transaction | Transaction remains rollback-only; REPEATABLE-READ also began with absence. Cannot provide successful commit. |
| Independent insert and conflict recovery transactions | Roll back/close the failed insert before catch; fresh recovery sees the committed winner and retains existing synchronization guards. Selected. |
| Native atomic insert/upsert | Introduces vendor-specific SQL, ID/lifecycle handling and risk of updating another account. Unnecessary for this local transaction-boundary bug. |
| Absent-row lock or globally stronger isolation | No existing row to lock, broader contention/policy change; unnecessary. |
| Separate transactional identity helper | Can isolate the same boundaries but adds another service solely for proxy dispatch. Existing Spring TransactionTemplate is sufficient in this service. |

Use a single reusable `TransactionTemplate` with `REQUIRES_NEW` around only the new-user INSERT and
its existing conflict-recovery lookup/synchronization. Catch surrounds template execution so an
insert/commit failure is rolled back before recovery. The existing outer service transaction,
mapper, unique constraints, email rejection, role/status and display-name rules are retained.
Recovery attempts once, never inserts again, and rethrows the original integrity failure if no
same identity exists. No helper, native SQL, migration, lock, global isolation or account-linking
policy is introduced.

This deliberately commits identity bootstrap independently of an enclosing operation, as needed
to isolate its losing INSERT. Current normal callers resolve identity at controller/upload entry
before domain writes. Profile rename keeps its existing outer transaction and merges the returned
user for the rename. A later enclosing-operation rollback may therefore leave the bootstrapped
USER account; it cannot leave a partially committed project or grant privileges. This bounded
transaction tradeoff is reported explicitly for independent review.


## Same-condition after-state and regression evidence

[After result](../evidence/product-trust-pt-6r1/after-state.json),
[repository log](../evidence/product-trust-pt-6r1/after-state.log.gz),
[Junit XML](../evidence/product-trust-pt-6r1/after-state-junit.xml.gz) and
[executed source](../evidence/product-trust-pt-6r1/after-state-test-source.java.gz) preserve the
single local after execution. Same immutable MariaDB image/server, REPEATABLE-READ, real Cognito
mapper, proxied service, two callers, matching identity and barrier after two actual absent reads;
no sleeps or replacement SQL. Random subjects prevent interference across measurements. Additional
assertions observe the same scenario, not a second run.

One INSERT still loses the unique race inside its independent transaction. The annotated outer
service status remains clean; the actual inner JPA transaction is rollback-only. After that inner
rollback, exactly one recovery read sees the winner. Both callers return userId 6, USER/ACTIVE,
and independent JDBC readback confirms one matching row. No persistence exception escapes either
caller. The internal expected SQL error remains in the raw log and is not hidden. Cleanup removes
that row and stops both threads. The controls demonstrate two distinct identities/two rows, reject
a third identity's attempt to reuse owned email, preserve a custom nickname with fallback claims,
and accept a provider explicit name. JWT role/status claims cannot assign privileges.

New focused unit cases also verify rollback precedes recovery, recovery has REQUIRES_NEW, one
INSERT/no create retry, absence of a same-identity winner rethrows the original constraint error,
recovery cannot take another identity's email, and persisted ADMIN/DISABLED state cannot be changed
by the loser. The original six identity cases remain. These mocked cases support the real database
proof; they do not replace it.

| Validation | Result |
| --- | --- |
| Identity/mapper/migration offline | 17 passed, 0 failures/errors/skips |
| Existing MariaDB schema/repository script | PASS, 4 tests, 0 failures/errors/skips; Flyway + Hibernate validation |
| Existing PT-5 access regressions | 87 passed, 0 failures/errors/skips |
| Full offline `clean package` | 557 tests, 0 failures/errors, 4 external MariaDB skips; 553 passed, package succeeds |
| Final scope/state/frozen/history audit and `git diff --check` | See [validation](../evidence/product-trust-pt-6r1/validation.json) |

The existing MariaDB script and Backend Local Verification workflow remain byte-identical. CI
automatically invokes the extended existing repository test; there is no workflow or dispatch.
GitHub alone holds exact-head CI/PR lifecycle. No source changed after these successful validations.
[Original implementation changed files](../evidence/product-trust-pt-6r1/changed-files.txt) and validation bind source/log
checksums. Final database readback has zero users; all eight existing migrations are successful;
only the owned disposable local container is stopped/removed. Retained GCP runtime is untouched.

## Residuals and actual stop

This is deterministic two-caller MariaDB evidence, not load, pool-capacity, browser, hosted IdP,
cloud rollout or every database vendor proof. Identity bootstrap uses independent commit; a later
enclosing-operation rollback can leave a USER account. Role/status, email ownership, display rules,
PT-5 authorization and schema are unchanged. The first measurement failure is preserved separately
and never converted to a product sample. Autonomous repair: 1 (instrumentation); human corrective
iteration: 1; initial product implementation: 1; product corrective retries: 0. Local before attempts:
2 (one invalid, one valid); local after executions: 1. No further local measurement is authorized.

PT-2 is permanently final INCOMPLETE: 03–10 NOT_RUN, null aggregate rates, 424846 ms original censor,
zero-product recovery failures; all frozen/historical bytes remain preserved. No PT-6R2 activation
or future base, model/browser/cloud/IAM/IdP/teardown action. Do not self-accept or self-merge. Next
actual gate is independent review of the governance correction and explicit USER merge of this substantive PR.

After PT-6R1 independent acceptance and USER merge, the durable gate is
**HUMAN_REQUIRED: PRODUCT_TRUST_EVIDENCE_VALIDITY_REASSESSMENT**. PT-6R2 is execution-unauthorized
until a subsequent evidence-validity amendment is reviewed, explicitly USER-approved, independently
accepted and USER-merged. This governance-only correction leaves all implementation/evidence bytes,
execution base and repair/correction counters unchanged; no database measurement is rerun. It does
not select that amendment's contents, a new Program version or a future execution base.
