# PT-5 — access safety and authentication boundary

Status: repository audit and bounded route correction; independent acceptance and USER merge
pending. Execution base `0c4fa0e9616881e281aa027a18a9d741ff614699` was read once and is preserved
in the [Work Package](../../.agents/work-packages/product-trust-pt-5-access-safety-v1.yml).
No live API/model/GCP action, deployment, IdP selection or IAM grant is performed.

## Authority and observed mechanism

[Review 6029174275](https://github.com/siamese-lang/terraformers-platform/pull/248#issuecomment-6029174275)
independently accepted PT-4 head `68110fc0429d88b3211c5811b1c470770bc4c3f7` against its original
criteria. USER merged PR #248 as the execution base on `2026-10-07T01:52:07Z`.
The approved Goal and Program DAG authorize repository-only PT-5; the review itself does not.
PT-4 completion is recorded inside this substantive PR, without a state/activation PR.

An authenticated user uploads a PRIVATE project, polls its job, reads its source/result and may
edit/share/delete the project. Public sharing permits the selected reads and discussion, while
project mutations remain owner/admin operations. Private reads also retain the existing persisted
ADMIN exception; JWT claims do not assign that role. No sharing or role policy is changed.

On the base, the JWT-enabled `JwtResourceServerSecurityConfig` enumerated protected paths but ended
with `anyRequest().permitAll()`. Existing service checks generally prevented private-project IDOR;
the demonstrated defect is that an unlisted handler inherits anonymous access. A controller with
no additional identity check could expose a future API. A test-only handler reproduces that exact
mechanism: six unlisted requests, including `/api/projects/export`, returned 200 instead of 401.
This is a deterministic exposure mechanism, not evidence that a current private file was stolen.

Keeping default-open routing fails the declared audit objective. A complete deny-by-default list
of all private handlers would duplicate route maintenance. The minimum correction declares only
the supported public GETs and authenticates the remainder using the existing chain. Numeric project
path constraints prevent a future `/api/projects/export` handler from inheriting public access.
Authenticated unlisted handlers still need their own ownership checks; authentication alone is
not authorization. A new IdP, IAM grant or authorization framework does not solve this route default.

## Supported access matrix

All observed HTTP results below come from the existing MockMvc/H2/stub test environment.
`jwt()` inserts a trusted test principal and bypasses signature/issuer/expiry verification.
The table demonstrates routing, identity mapping, ownership and linkage; it is not a live IdP proof.

| Flow | Owner | Other authenticated USER | Anonymous | Mechanism / validation |
| --- | --- | --- | --- | --- |
| Private project metadata/tree/Terraform read | 200 | 403 | 403 | `requireAccessibleProject`; parameterized matrix |
| Private source metadata/image read | 409 in metadata-only fixture | 403 | 403 | Authorization precedes storage; existing reader test verifies no denied object read |
| Public metadata/tree/Terraform read | 200 | 200 | 200 | Explicit GET allowlist plus visibility check |
| Public source metadata/image read | 409 in metadata-only fixture | 409 in fixture | 409 in fixture | Access passes; unavailable binary remains unavailable, not a successful content read |
| Public becomes PRIVATE again | Retains access | 403 | 403 | Each GET checks current visibility; both readers are denied after revocation |
| Private AnalysisJob lookup | 200 | 403 | 401 | Job's persisted project ID is authorized before returning its snapshot |
| Public AnalysisJob lookup | 200 | 200 | 401 | Existing authenticated job-read contract; public project does not make job route anonymous |
| Private or public visibility/edit/delete/new job | Allowed | 403, no mutation/job acceptance | 401, no mutation/job acceptance | Existing `requireModifiableProject`, with ADMIN exception preserved |
| New job with another project's source / deleted source | 400 / 404, no job accepted | Project mutations denied | 401 | Source file must be active and belong to authorized project |
| Job-bound source/result points to foreign or missing active file | Unavailable (not found) | Project access checked first | Public/private policy first | Existing artifact guard; negative and valid binding tests |
| Private comments, including compatibility read | 403 | 403 | 403 | Discussion is public-only, even for owner |
| Public comments read, both route names | 200 | 200 | 200 | Explicit public GETs |
| Public comments create, both route names | Allowed | Allowed as own authenticated author | 401 | Public discussion is intentionally not an owner-only mutation; supplied author/email cannot impersonate |
| Upload / owned project and tree lists / profile mutation | Authenticated | Own identity only | 401 | Existing security, upload and identity tests |
| Unlisted handler outside public contract | Authenticated | Authenticated | 401 | Six before/after route-default regressions; no production test handler added |

Rejection tests compare persisted job/file counts, original draft, visibility and deleted state.
Owner positive controls still edit, change visibility, accept a stub job and delete. No failed
request is replaced with a successful product sample. New source references cannot target another
project; artifact resolution checks both active-file presence and matching project ID. Existing
latest-job binding tests retain the rule that newer unrelated physical files cannot replace the
job's source/result and a failed latest job cannot surface an old successful draft.

## Exact public and authentication boundary

Only GET is public for:

- `/api/projects/public`, `/api/public-projects`;
- `/api/projects/{numericId}`, its `/terraform/main.tf`, `/source-object`, `/source-image`, `/comments`;
- `/api/project-tree/{numericId}`, `/api/getProjectComments/{numericId}`;
- `/actuator/health` and health probe subpaths, `/actuator/info`, `/actuator/prometheus`;
- `/internal/runtime/required-config` (names and presence booleans, never environment values).

Monitoring/readiness GET access is preserved from the existing chain and retained workflow/probe
contract; no new route is published. Numeric patterns include query-bearing reads in regression
coverage. Non-GET requests to these paths receive the authenticated fallback. OPTIONS remains
permitted for normal preflight handling, but an unconfigured foreign-origin upload preflight is
403: Spring's existing CORS handling grants no new origin. No CORS allowlist is selected here.

The existing resource-server decoder uses Nimbus JWKS signature verification, standard timestamp
and exact issuer validation, plus `CognitoAccessTokenValidator` requiring `token_use=access` and
the configured `client_id`. This composition is source-audited, not newly proved with a live issuer.
Existing provider tests exercise wrong/missing token-use and client claims. The new filter test
also rejects a malformed bearer on a public route. Existing frontend tests cover access-token
attachment, one 401 refresh attempt, expiration notification and session invalidation; they are
mocked integration controls, not an end-to-end session-security claim.

`AuthenticatedUserService` resolves `(provider, subject)` to a persisted user; client project/body
identity is not the owner. It creates USER/ACTIVE, rejects conflicting email identity and inactive
users, and never maps a JWT role claim to ADMIN. Existing migration and identity tests are reused.
JWT-disabled/default-local mode retains its permit-all development chain, disabled object reader,
metadata-only writer and stub provider. The selected `prod` runtime explicitly enables JWT.
PT-5 does not turn local mode into a production security configuration.

## Authentication and abuse residuals

The retained GCP ConfigMap points to the deterministic `identity.example.test/case-c` issuer and
internal JWKS fixture. `ephemeral-jwks-fixture.sh` checks ownership/placeholder state, creates a
short-lived run-specific signed token and removes private material during restoration. These are
representative evaluation identities, not a permanent production IdP, self-service login or account
lifecycle. No fixture is installed, no live token is minted, and no credential is read by this audit.
Choosing a permanent IdP or expanding IAM/security remains HUMAN_REQUIRED if a later product claim
needs it. Existing frontend Cognito adapter tests do not establish that it logs into this fixture.

The prod servlet contract limits files to 10 MB and requests to 12 MB. Upload checks nonempty file
and project name, sanitizes the filename, creates a private owned project and stores server-chosen
object references. JSON job creation uses registered project/file IDs, not client bucket/key input.
MockMvc bypasses real multipart parsing limits; those limits are inspected configuration, not a
new oversized-wire-request proof. MIME is supplied by the client; this phase adds no content scanner.

Authenticated repeat upload/job requests have no per-user rate limit, deduplication or model-cost
quota. Existing bounded dispatcher/provider attempts do not bound total accepted requests/cost.
CORS is not a cost-abuse defense. A new quota/cost policy would require a product decision, and is
not silently introduced. Direct GCS/IAM access and public monitoring exposure are outside this
application ownership proof; their existing contracts are preserved. No concurrent visibility
change/streaming revocation, penetration test, browser E2E, real binary GCS read or live rollout is
claimed. PT-6 owns browser-observable integration; deployment and final live proof remain PT-8 gated.

## Deterministic validation and preserved evidence

The [validation record](../evidence/product-trust-pt-5/validation.json) binds commands, outcomes,
per-suite counts, source hashes, controlled before-failure and scope/frozen-state audits.
The existing tests are extended rather than adding an evaluation framework, workflow or verifier.
This phase is an access audit: negative boundary coverage is needed because current tests mainly
covered owner success and individual routes, not the full private/public/anonymous matrix.

- Before correction: 6 expected failures (401 expected, 200 observed), no errors/skips.
- Focused offline backend: 101 tests, zero failures/errors/skips.
- Existing frontend auth/API tests: 21 passed across 3 suites. An initial command named a nonexistent
  `authClient.test.js` (ENOENT, API's 7 tests passed); the actual existing paths were then selected.
  No product code/criteria changed and no autonomous repair was consumed.
- Full offline backend `mvn -o clean package`: 552 tests, zero failures/errors; 2 external MariaDB
  tests skipped because `SPRING_DATASOURCE_URL` is absent (550 passed). Package succeeded.
- Scope, durable-state consistency, all 26 frozen files and `git diff --check`: recorded in evidence.

PT-2 remains `PT2_INCOMPLETE_ACCEPTED_AS_FINAL_EVIDENCE_DISPOSITION`, not ten-case realistic
generalization. Cases 03–10 remain NOT_RUN, neither pass nor fail. No aggregate classification,
hallucination, semantic-success or executable-validity rate is fabricated. Case 02 stays censored
at `424846 ms`; runs `37500000739` and `37509368210` remain zero-product-observation measurement
failures. Further PT-2 execution/harness recovery is prohibited. Frozen identity
`04f65a5c2c82a5afcab9ffe567018d0190b95877adb7c87a22797f0193e5c014`, original bases, attempt history
and repair counters are unchanged. Model calls and live cloud actions in PT-5 are zero.

The bounded improvement is that an unlisted JWT-mode handler cannot inherit anonymous access;
existing application ownership/sharing policy continues to decide returned data. CI is supporting
evidence only. Stop for independent original-criteria review and explicit USER merge. PT-6 is
blocked until both are fulfilled; this PR neither self-accepts nor grants new live/security authority.
