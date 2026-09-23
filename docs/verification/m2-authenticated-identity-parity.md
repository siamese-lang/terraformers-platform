# M2 Authenticated Identity and Ownership Parity

**Status: PASS**

## Verification identity

- Implementation base SHA: `d902e7bf095eadb686d3cf01d433c8425029fa5b`
- Runtime: Kind, namespace `terraformers-portable`, Spring `prod` profile, MariaDB 11.4 and Flyway.
- Command: `bash scripts/checks/kind-portable-authenticated-smoke.sh`
- Authoritative result: **PASS** — M2 Authenticated Identity Parity Verification run #3 at
  `eda4b9469f258b71aae8e28a0b3ec1a226c4412a`.

### Historical run #1

The first workflow run stopped at `rollout restart deployment/terraformers-jwks` because the outer
Kustomization had no namespace. The composed persistent resources landed in `terraformers-portable`,
while the outer JWKS resources landed in `default`. Fixture application therefore only partially
succeeded, and the authenticated runtime was **NOT REACHED**. This is fixture namespace evidence,
not a JWT parity failure. The outer overlay now assigns all composed resources to
`terraformers-portable`, and the verifier asserts that the JWKS ConfigMap, Deployment, and Service
exist there immediately after apply.

### Historical run #2

The second workflow run confirmed `runtime_ready=PASS` and `jwks_ready=PASS`: MariaDB, the JWKS
fixture, and the backend were Ready, and backend health was `UP`. It then stopped immediately before
the first HTTP authentication request because the verifier expanded `method` in the same `local`
statement that initialized it under `set -u`. Authentication requests were therefore **NOT REACHED**;
this was a shell harness initialization failure, not an authentication-parity failure.

### Authoritative run #3

The third workflow run completed successfully. The uploaded machine-readable summary recorded:

```text
runtime_ready=PASS
jwks_ready=PASS
owner_token_accepted=PASS
owner_user_created=PASS
provider_subject_persisted=PASS
numeric_user_id=PASS
same_identity_reused=PASS
second_identity_distinct=PASS
anonymous_protected_rejected=PASS
invalid_token_rejected=PASS
owner_private_access=PASS
non_owner_private_forbidden=PASS
non_owner_modification_forbidden=PASS
owner_modification_allowed=PASS
display_name_update=PASS
display_name_preserved=PASS
cloud_credentials_required=false
```

Observed HTTP statuses were: anonymous `GET /api/projects` → 401, signed wrong-client token → 401,
owner authenticated list/get → 200, non-owner private project get → 403, non-owner visibility
modification → 403, owner visibility modification → 200, display-name update → 204, and subsequent
owner list → 200. MariaDB evidence recorded owner `user_id=1` and other user `user_id=2`, both
selected by `external_identity_provider='cognito'` plus their neutral external subjects. The seeded
ownership fixture used `project_id=1` and ended with visibility `PUBLIC` after the authorized
owner update. No private RSA key, raw bearer token, database password, or unredacted Secret was
uploaded.

The `portable-authenticated` overlay composes the already verified `portable-persistent` fixture and
adds a BusyBox static HTTP server for JWKS. The verifier generates an ephemeral RSA-2048 key with
public exponent 65537, publishes only its public JWK (`kid=terraformers-m2-test-key`), and signs
short-lived RS256 tokens without printing or uploading them. The private key exists only in a
temporary directory removed by the exit trap. This deterministic compatibility fixture exercises
the existing `cognito` adapter; it is **not a production IdP**, does not restore Cognito as an active
deployment target, and makes no Cognito or other live identity call.

## Exact flows and expected evidence

The machine-readable `artifacts/m2-authenticated-identity/summary.txt` records each assertion:

1. Start the persistent runtime, publish the generated JWKS, await MariaDB, JWKS, backend rollout,
   and backend health.
2. Reject an anonymous `GET /api/projects` with 401 and reject a correctly signed token carrying a
   wrong `client_id` with 401. This distinguishes authentication/provider-token validation from
   authenticated authorization failures.
3. Call `GET /api/projects` twice with the owner token. Verify exactly one row selected by
   `external_identity_provider='cognito'` plus
   `external_identity_subject='m2-owner-subject'`, the expected email, and a numeric BIGINT user ID.
4. Call the endpoint with `m2-other-subject`; verify a distinct numeric internal user ID while
   preserving provider-plus-subject uniqueness. The legacy `cognito_sub` mirror is not used as the
   primary identity assertion.
5. Seed one ACTIVE/PRIVATE project directly in the same MariaDB, owned by the resolved owner ID.
   Verify owner get/list return 200, while the authenticated non-owner get returns 403.
6. Verify the non-owner visibility PATCH returns 403, the owner PATCH returns 200, and MariaDB
   records `PUBLIC`.
7. PATCH the owner's display name to `M2 Owner` (204), call the authenticated list again with a token
   deliberately lacking a `name` claim, and verify the custom value remains unchanged.

Sanitized artifacts include health, pod/service state, backend/JWKS logs, provider-plus-subject
identity rows, numeric IDs and project ownership results, HTTP statuses, runtime configuration, and
the public-key fingerprint. They exclude the private key, bearer tokens, database passwords, and
unredacted Secret manifests. No AWS or GCP credentials are required and no cloud API is called.

## Not covered

This verification does **not** cover authenticated uploads, analysis jobs, object-byte persistence,
Terraform generation/read-back, `ObjectReader`/persistent `ObjectWriter` selection, MinIO/GCS/S3,
active model or retrieval integrations, comments, a frontend workload, browser E2E, complete public
project UX, production identity-provider selection, or production topology. Upload and analysis
remain M2-4 scope; broader user/comment/frontend parity remains M2-5 scope.

M2 remains **ACTIVE**. M2-3 is **DONE** based on the authoritative run #3 evidence above. The
immediate next single task is **M2-4 — Upload → analysis → Terraform result parity**.
