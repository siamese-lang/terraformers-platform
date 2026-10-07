# PT-6 frozen browser procedure

Frozen before browser outcomes on execution base `37e006be4d5995019704ea8a4009e1a59da039b2`.
[Work Package](../../.agents/work-packages/product-trust-pt-6-browser-journey-v1.yml) binds scope,
acceptance and one bounded repair. No production IdP/security/CORS/cloud decision is made.

Use one Chromium run with isolated owner/other contexts. Build the existing frontend unchanged;
serve it through a loopback same-origin API proxy. Start the actual backend using local H2,
filesystem object storage, stub analysis and JWT enabled with the existing portable issuer/client.
Only a test-source executor decorator holds work until a local marker is released. It creates real
PENDING time, not a fake API response or client clock. Existing upload nudge ignores dispatch-enabled,
so disabling its scheduled scanner alone would not provide that hold. No control HTTP endpoint,
backend auth/quality/runner override or new production profile is added. Isolate that fixture class
from other test classes and keep it outside the packaged production jar.

Reuse shared OpenSSL RS256/JWKS construction in `ephemeral-jwks-fixture.sh` for two short-lived
identities. The browser executes the existing login form/Cognito adapter. Only external Cognito
InitiateAuth/GetUser responses are fulfilled locally with these tokens and attributes. All `/api`
requests cross real HTTP into the unchanged JWT filter/decoder, persistence, storage and ownership
services. Do not intercept or replace application API responses or React modules. Reject unexpected
identity operations and block external egress. This proves fixture-session integration and application
authorization, not password verification, hosted Cognito/IdP availability or permanent identity.

## Single ordered journey

1. Owner signs in through the login form; the real backend accepts its signed bearer. Anonymous
   protected access and a forged-signature control must return 401. Require an actual backend JWKS
   fetch. Do not persist credentials or record a HAR/trace/storage-state file.
2. Upload frozen `pt1-06-thumbnail-pipeline.png` exactly once from the real file input and submit
   button. Verify its SHA `b41f87b2f8afa2fb60496b8a68289d804748a6d881d8d46cba2df13241b6578c` and
   frozen dataset identity before startup. Capture the real 201 acceptance, project/source/job IDs.
   Stub metadata processing does not run the model or alter PT-2's NOT_RUN history.
3. Observe PENDING on project detail and real job/project readback. Navigate to owned projects and
   back, then reload. Assert same job/source identity and server acceptedAt, visible elapsed time
   anchored to that timestamp, and no additional upload/job-create request. Capture screenshots.
4. Release the local executor hold once. Poll only the original job for at most 60 seconds; do not
   resubmit on failure. Verify terminal SUCCEEDED, durable terminal time, project/job timing equality,
   frontend terminal wording, absent-quality wording and conditional editable/reference-draft limits.
   A stub has no AI quality snapshot; never fabricate one. Check displayed HCL equals the bound
   backend Terraform artifact, and the served source-image bytes equal the uploaded frozen bytes.
5. Sign in as a second identity in an isolated context. Navigate to the owner's private URL: the
   actual frontend request must receive 403 and reveal no source/HCL. From that browser attempt
   metadata/tree/source/Terraform/job reads and visibility/Terraform/new-job/delete mutations; all
   are 403. Anonymous private metadata is 403 and anonymous job lookup is 401. Confirm owner job,
   original HCL, source binding, PRIVATE visibility and owned-project inventory are unchanged.
6. Stop only the local processes this run started and remove temporary private keys/tokens/browser
   profiles/object data. Retain sanitized response evidence, screenshots, source/build/input hashes,
   correlated server-stage lines and a manifest of evidence hashes. No retained cloud teardown.

## Repetition and failure policy

One complete journey is the default. No automatic browser retries; maximum one upload attempt per
execution. Preserve each failure and unexecuted step. Do not rerun unchanged work to obtain PASS.
At most one same-mechanism correction is allowed by the Work Package, followed only by a declared
post-change regression execution if necessary; retain the first result. A new failure class or
identity/security/CORS/product/cloud decision stops at HUMAN_REQUIRED.

The executor hold has a 120-second hard bound. Include its duration in evidence and label measured
accepted-to-terminal time as fixture time including the hold, never production/provider latency.
This phase does not score image classification, topology, retrieval, Terraform execution or realistic
generalization. The fixed stub HCL is intentionally unrelated to input truth. PT-2 remains final
INCOMPLETE; PT-8 owns separately approved live proof. PT-7 owns CI/CD gates; add no E2E CI gate here.
