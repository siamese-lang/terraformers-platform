# PT-6 — partial real-browser journey; blocked

**HUMAN_REQUIRED: BOUNDED_REPAIR_LIMIT_AND_BROWSER_MEASUREMENT_BLOCKER. PT-6 is INCOMPLETE.**
Execution base `37e006be4d5995019704ea8a4009e1a59da039b2` remains bound once in the
[Work Package](../../.agents/work-packages/product-trust-pt-6-browser-journey-v1.yml).
The [procedure](product-trust-pt-6-browser-protocol.md) was frozen before outcomes.
No production code, prompt/model/RAG, truth, IAM, CORS, infrastructure, workflow or live cloud changed.

## Authority and implementation

PR #249's [review 6029459989](https://github.com/siamese-lang/terraformers-platform/pull/249#issuecomment-6029459989)
accepted PT-5 head `bc13af0cb889b803a8d83352ef869896c97348f8`; USER merged it at
`2026-10-07T02:16:09Z` as this base. PT-5 completion is recorded inside this substantive PT-6 PR,
with its original base and evidence preserved. Review alone does not authorize progression or merge.

The actual CRA production frontend executes its login form and existing Cognito Amplify adapter
in Chromium `151.0.7922.173`. Only external InitiateAuth/GetUser transport is fulfilled using
short-lived RS256 tokens from the existing shared OpenSSL/JWKS construction. Every application
API crosses HTTP to the unchanged packaged backend, real JWT decoder, H2 persistence, filesystem
storage and stub analysis. A loopback same-origin proxy preserves Host/Origin and Authorization;
it injects no identity and changes no CORS policy. No application API or React module is mocked.

An isolated test-source executor holds before the existing runner's claim, then releases via a
local filesystem marker. The existing executor configuration and runner remain in use. No fake
status/quality, new control endpoint, pass-through Terraform validator, permanent IdP or CI gate
is added. Only this fixture's compiled class is loaded; other test controllers/configurations are
excluded. It is absent from the packaged production jar. Local keys/tokens/browser processes,
objects and H2 state were removed; the retained GCP runtime was untouched.

## Direct browser evidence

[Raw result](../evidence/product-trust-pt-6/03-browser-partial/result.json) records one accepted
job `604890dc-c272-485c-b9d5-95fdede77504`, project/source `1/1`, 20 real API responses and
the exact frontend/JAR/input/source hashes. [Observed runner bytes](../evidence/product-trust-pt-6/runner-at-observation.txt)
match its recorded source SHA. The current runner additionally fails before startup/upload when
the known Terraform prerequisites are missing and serializes JWKS counts on failures; those changes
were syntax checked and the prerequisite refusal was checked without opening a browser or uploading.

| Declared step | Observed outcome | Evidence/limit |
| --- | --- | --- |
| Real login and authorization | PASS | UI profile write 204, owner list 200; anonymous/forged-signature list 401; actual JWKS fetch assertion passed, but its count was not serialized before terminal failure |
| One realistic upload | PASS | POST `/api/upload` 201, binary persisted, source ETag equals frozen input SHA, job PENDING |
| Navigate/reload durable progress | PASS | Same job/source and acceptedAt across owned list/detail/reload; live elapsed label anchored to server time; no second upload/job creation |
| Terminal draft/trust/result | NOT COMPLETED | Original job FAILED with technical FAIL, quality UNKNOWN, TERRAFORM_EXECUTABLE_FAILURE; no result artifact |
| Second identity denial | NOT_RUN | Stopped at preceding failure; PT-5 deterministic evidence is preserved but does not substitute for browser acceptance |

![Original real-browser PENDING](../evidence/product-trust-pt-6/03-browser-partial/01-pending.png)
![Same job after navigation and reload](../evidence/product-trust-pt-6/03-browser-partial/02-pending-after-reload.png)

AcceptedAt `2026-10-07T02:38:35.115933Z`; terminalAt `2026-10-07T02:38:36.698294Z`;
accepted-to-failed-terminal **1582 ms**, including the controlled hold. This is local fixture time,
not model/provider or production latency. The browser's pending image loads are real filesystem
reads; terminal source-byte roundtrip comparison and HCL rendering were not reached.

## Preserved unsuccessful observations and correction boundary

All executions remain individually inspectable; none is replaced by a passing outcome:

| Evidence directory | Result | Submissions |
| --- | --- | --- |
| [00-startup-portability-failure](../evidence/product-trust-pt-6/00-startup-portability-failure/result.json) | Shared key bootstrap required unavailable `xxd`; use existing Python stdlib byte conversion instead | No browser/API/upload |
| [01-login-probe-failure](../evidence/product-trust-pt-6/01-login-probe-failure/result.json) | Concurrent first-user persistence collided on existing unique identity; driver cleanup then masked its assertion and lost in-memory events | No recorded upload; read-only recovery owner inventory empty |
| [02-profile-contract-failure](../evidence/product-trust-pt-6/02-profile-contract-failure/result.json) | Real profile write returned the existing correct 204; driver incorrectly expected 200 | One profile write; no upload |
| [03-browser-partial](../evidence/product-trust-pt-6/03-browser-partial/result.json) | Auth/upload/pending navigation passed; original job terminal FAILED | Exactly one upload, no resubmission |
| [04-known-prerequisite-refusal](../evidence/product-trust-pt-6/04-known-prerequisite-refusal/result.json) | Expected fail-closed preflight after the observed missing-tool failure | No server/browser/API/upload |

One bounded **measurement-driver** corrective iteration covered sequencing supplemental controls
after the actual first UI profile write, the correct 204 contract and cleanup lifetime. The local
bootstrap portability fix is recorded separately. No natural product/model case was rerun for PASS.
The first-user creation collision remains a supported local concurrency observation, not a repaired
product defect or a proven hosted/production failure. Serializing supplemental driver probes does not
prove concurrent first-user safety. The lost events/original assertion of execution 01 cannot be
reconstructed; only its surviving backend errors, cleanup traceback and read-only empty inventory
are claimed. Cleanup was completed manually for that orphaned local process; private material was removed.

## Blocker and proportional validation

Build/helper/backend failure logs are gzip archives of their exact original bytes, with
[uncompressed SHA bindings](../evidence/product-trust-pt-6/raw-log-bindings.json). Compression
avoids changing raw log whitespace while keeping the patch whitespace-clean.

`TerraformCliValidator` uses `/usr/local/bin/terraform` and `/opt/terraform-plugins` even for the
stub path. Both are absent in this minimal backend compile/test environment. Backend `Dockerfile`
pins Terraform **1.8.5** and AWS provider **5.100.0** with SHA-256 checks. The absolute locations
are outside the writable roots and owned by another OS user. The runner passed compilation but
failed runtime validation with `terraform_internal`; this is a **local measurement prerequisite
failure**, not evidence of invalid generated HCL or a new production Terraform defect.

No pass-through validator, production path/config change, Docker stack or live dependency is used
to make the journey pass. A bounded follow-up would need reviewed local installation/path handling
for those exact real validator prerequisites and explicit authority beyond the spent correction
boundary before another complete journey. This PR does not execute or authorize that follow-up.
It must not be accepted as completed PT-6 or used to start PT-7.

The concrete proposed correction is to download the existing Dockerfile-pinned CLI/provider into
the writable local tool cache and let only the isolated test fixture construct the **same real**
`TerraformCliValidator` with those local paths and its existing `ProcessCommandExecutor`. Do not
change the production validator or replace validation with a stub. Preserve this FAILED job and
authorize at most one distinct post-change browser regression, separately from the spent autonomous
counter. This proposal is not implemented and needs independent review plus explicit bounded
corrective-iteration authority; no model/GCP/IdP/CORS/security expansion is requested.

- Offline backend `mvn -o -DskipTests package`: PASS; compiles production and isolated fixture
  sources, packages unchanged product; tests intentionally skipped. No new backend product change
  requires repeating PT-5's independently accepted full suite (552 tests, 2 external MariaDB skips).
- Existing shared JWKS helper regressions: **7 tests PASS**, no failures/errors. Protected
  prepare/restore identity and cleanup guards remain intact; no protected runtime was accessed.
- Existing frontend production build with nonsecret fixture Cognito configuration: PASS. No
  production frontend source or dependency/lock change. Chromium/Playwright were preinstalled.
- Bash/Python syntax, frozen 26-file identity, scope/durable-state/evidence audit and diff check:
  see [validation](../evidence/product-trust-pt-6/validation.json). These do not turn the incomplete
  journey into PASS. No new workflow/CI gate or broad E2E suite.

Frozen realistic revision 3/identity `04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`
is unchanged. Its pt1-06 bytes are a local upload fixture, **not** an AI observation or PT-2 sample.
PT-2 remains final INCOMPLETE: cases 03–10 NOT_RUN, no fabricated aggregate rates, case 02 censored
at **424846 ms**, and both continuation/recovery runs zero-product-observation failures. No PT-2
dispatch/rerun/exception is authorized. Model calls and live cloud actions here are **0**.

Independent review and a human-authorized bounded correction are required. CI green or this PR's
creation is not PT-6 acceptance, merge permission, PT-7 authority or Product Trust final closure.
