# M1 cloud-decoupling closure verification

**Status: PENDING**

This is the first M1-8 closure PR. `PENDING` remains authoritative until every job in
`m1-cloud-decoupling-closure-verification.yml` passes on GitHub; a local pass does not mark M1-8 or
M1 complete. The inspected base is `de5f6661b6b371f9a58311151c2f569d6bca757e`. The checkout had
no configured Git remote, so that SHA was confirmed from the local branch/commit and matched the
requested expected main SHA; GitHub Actions will validate the resulting PR against current `main`.

## Boundary evidence matrix

| Boundary | M1 task | Provider-neutral contract | Provider-specific compatibility adapter | Preserved business behavior | Exact validation/test | Residual limitation | Final result |
|---|---|---|---|---|---|---|---|
| Backend external identity | M1-1 | `JwtExternalIdentityMapper`, `AuthenticatedExternalIdentity`, and provider-plus-subject repository lookup | `CognitoJwtExternalIdentityMapper`; historical `cognito_sub` mirror | Internal user ID, profile/status/role, ownership, and attribution | Static closure verifier; `AuthenticatedUserServiceTest`; full backend suite; MariaDB repository smoke | A replacement IdP and subject migration are not selected | **PENDING CI** |
| Backend JWT/resource server | M1-2 | Generic resource-server wiring delegates additional validation to `JwtProviderTokenValidator` | `CognitoAccessTokenValidator` owns `token_use`/`client_id`; Cognito mapper owns claim interpretation | Existing authorization and Cognito token/user compatibility | Static closure verifier; security/validator tests; full backend suite | Cognito remains the compatibility provider | **PENDING CI** |
| Frontend auth/session | M1-3 | `authClient` and `AuthSessionContext` separate application/UI state from the provider SDK | `cognitoAmplifyAuthClient.js` and provider configuration | checking/authenticated/guest, protected routes, login/logout, signup/confirmation/reset, access/ID tokens, missing-token redirect, once-only 401 retry, and auth-expired event | Static import inspection; `CI=true npm --prefix frontend test -- --runInBand`; production build | Amplify and `REACT_APP_AWS_*` configuration remain; no new IdP is selected | **PENDING CI** |
| Object storage | M1-4 | `ObjectReader`, `ObjectWriter`, `ObjectReference`, and explicit provider/persisted write results | `AwsS3ObjectReader`, `AwsS3ObjectWriter` | Source upload/read and generated artifact/file metadata behavior; metadata-only stub semantics | Static core import/eTag inspection; storage and `ProjectArtifactService` tests; full backend suite | Historical `s3_bucket`/`s3_key` schema names remain | **PENDING CI** |
| OpenSearch transport/auth | M1-5 | `OpenSearchReferenceRetriever` depends on `OpenSearchTransport`; query/parser/filter contracts remain | `SignedOpenSearchHttpClient` owns credentials, region, SigV4, and `aoss`/`es` | Endpoint/query, corpus/provider filters, ranked parsing, disabled/optional/required retrieval | Static dependency inspection; OpenSearch query/parser/transport tests; full backend suite | Batch ingestion remains AWS-bound | **PENDING CI** |
| Analysis/embedding provider | M1-6 | `AnalysisProvider`, `EmbeddingProvider`, and neutral selectors | Bedrock generation, embedding, facts extraction, and properties adapters | Analysis job lifecycle, retrieval/model orchestration, Terraform validation and result persistence | Static properties/import inspection; provider/retrieval tests; full backend suite | Exact target models/providers are gated | **PENDING CI** |
| Runtime configuration | M1-7 | `application-prod.yml` neutral selectors and neutral Kubernetes/runtime-secret contract | `application-aws-compat.yml` contains Cognito/S3/Bedrock/SQS/SigV4 settings | Deterministic local/stub runtime and optional historical AWS compatibility runtime | Static key inspection; `bash scripts/checks/runtime-contract-verification.sh` | GCP product and topology choices remain gated | **PENDING CI** |

The verifier writes `boundary-summary.json`, `boundary-summary.md`, and
`verification-summary.txt` under `artifacts/m1-cloud-decoupling-closure/`. Results are calculated
from the inspected tree rather than predeclared: any boundary failure or non-allowlisted production
AWS SDK import makes the command fail.

## AWS SDK allowlist and historical compatibility

Production Java `software.amazon` imports are accepted only in exact, reviewed compatibility
files: Bedrock adapters/configuration, Bedrock facts/embedding adapters, SQS progress publisher,
the signed OpenSearch client, S3 reader/writer, and historical CloudWatch configuration. New imports
elsewhere fail the verifier. Finding Cognito, S3, Bedrock, SQS, AWS SigV4, `aoss`/`es`, or
`AWS_REGION` in `application-aws-compat.yml` or those adapters is not itself a failure.

Historical database compatibility columns (`cognito_sub`, `s3_bucket`, and `s3_key`) are likewise
not removal targets. The closure checks application lookup and storage semantics, not wholesale
vendor-string absence. M1-8 adds no migration.

## Regression found and minimal correction

Static allowlist inspection found one real boundary regression: `AnalysisJobRunner`, a generic
analysis lifecycle service, imported AWS SDK timeout exception types. Before the fix, an exact
production-source import inventory reported that non-adapter file. The smallest correction adds a
provider-neutral `AnalysisProviderTimeoutException`; the Bedrock adapter translates AWS timeout
exceptions into it, while the runner retains the same safe user-facing timeout classification.
The focused runner/provider tests and full backend suite provide same-condition after-validation.
No other production behavior or architecture was changed.

## Accepted residual limitations

1. The Python batch RAG ingestion path remains coupled to `boto3`, S3 package/receipt handling,
   AWS4Auth/AOSS, Bedrock embedding, and CodeBuild-oriented execution. M1-5 deliberately separated
   runtime retrieval; M1-8 does not rewrite this historical compatibility implementation.
2. CloudWatch exporter/configuration is a historical AWS observability concern. Its modernization
   belongs to M7 and is not an M1 closure failure.
3. The frontend Cognito/Amplify adapter and `REACT_APP_AWS_*` provider configuration remain. The
   application/UI boundary is neutral, but no new IdP has been selected.
4. GCP IdP, object storage, model provider, OpenSearch hosting/auth, Kubernetes topology, managed
   database, and observability products remain unselected by deliberate gate. That is not M1
   failure evidence.

## Business, schema, and runtime regression evidence

The dedicated workflow runs five credential-free jobs in parallel:

- `boundary-contract`: static verifier plus uploaded source-boundary artifacts;
- `backend-regression`: full clean backend test/package contract, including identity, security,
  project metadata/tree/comments/files, analysis lifecycle, Terraform draft validation, storage,
  retrieval, provider selection, and runtime adapter validation;
- `mariadb-regression`: MariaDB 11.4, Flyway, Hibernate schema validation, and canonical repository
  smoke queries using only the neutral `prod` profile;
- `frontend-regression`: clean install, complete tests, production build, and non-empty entrypoint;
- `runtime-contract`: existing deterministic Java/Terraform/Kubernetes contract verification.

No job uses live cloud calls, AWS OIDC, AWS/GCP credentials, plan/apply, or provider deployment.

## M2 readiness

M1 closure evidence checks that provider-neutral application boundaries, canonical neutral runtime
configuration, deterministic local/stub runtime, business regression assets, MariaDB/Flyway
compatibility, and the Kubernetes-compatible workload contract remain. It also checks that
historical AWS assets are not treated as the active target and that exact GCP products/topology
remain gated.

After authoritative closure, M2 may plan evidence for the existing end-to-end chain:

`architecture/image input → upload/source object → project/file → analysis job → retrieval/model → Terraform validation → result persistence`

and user/project/comment parity. M1-8 does not implement that flow or activate M2. The immediate
next single task after an authoritative CI pass is to write the M2 active plan from current
repository evidence—not to begin M2 implementation in this PR.

## Closure rule

M1-8 remains **TODO**, M1 remains **ACTIVE**, and M2 remains **PLANNED**. A separate status-only
update may record closure after this workflow passes on the PR/head intended for merge.
