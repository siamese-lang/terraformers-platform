# Human-authorized PT-6R2 test-contract correction 1

USER explicitly authorizes `AUTHORIZE_ONE_PT6R2_TEST_CONTRACT_CORRECTION_ONLY` following independent review [6037186101](https://github.com/siamese-lang/terraformers-platform/pull/254#issuecomment-6037186101) of head `2ac19e2ca7d782bec1d47c6f29fb58543cbb3640`.

Continue the same Work Package / PR #254 / branch with bound execution base `406981a8c629a02503f5660453dcc7921b8d5177`. Exactly one additional human corrective iteration; autonomous repairs remain consumed at 1/1.

Only these existing tests may change:

1. `AnalysisJobPartialSuccessBaselineTest.java`: only log ordering, execution success → finalization failure → compensation success. Preserve durable FAILED, no result metadata, compensation/cleanup, failure metrics and rollback assertions.
2. `AnalysisJobRestartBaselineTest.java`: only `claimedRunningJobIsNotStolenAndLeaseExpiryFailsWithoutASecondAttempt`. No second worker/reclaim; existing sweep must commit FAILED, attemptCount=1 / claimGeneration=1, no generated result or success.
3. `MariaDbRepositorySmokeTest.java`: only stale durable discovery/reclaim expectations and supporting assertions. Expired consumed attempt is not eligible; concurrent reclaims both fail; assert existing explicit FAILED terminalization where practical. Untouched attemptCount=0 first claim retains exactly one winner. PT-6R1 identity-concurrency code/expectations/counters are immutable.

One bounded cycle: affected local tests once; if passing, full existing offline backend tests/package once; push this same PR without CI skip and allow one normal automatic Backend Local Verification on the new exact head. No manual workflow rerun or dispatch. Automatic MariaDB CI is ordinary regression, not a PT-6R1 before/after acceptance measurement.

No production edit. If corrected tests reveal a genuinely new production defect or any failure remains/changes class, preserve it and STOP for independent review without another repair. No SDK/prompt/model/retrieval, policy D budgets/attempts/cutoff, fallback/closure/repair/finalization/sweep/cleanup/post-commit, frozen candidate or PT-2 history change. No model/cloud/OpenSearch/PT-7/new PR/self-acceptance/merge.

Success returns only to `PT-6R2 IMPLEMENTATION COMPLETE / INDEPENDENT ACCEPTANCE PENDING`; it does not complete independently accepted phase closure.
