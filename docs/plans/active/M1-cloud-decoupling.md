# M1 — Cloud Decoupling

## Status

**ACTIVE**

M0 is complete. M1 implementation is in progress; completed work and the first remaining `TODO` below determine the current execution state.

## Objective

Move AWS-specific integrations that are directly coupled to domain/application concerns behind provider-neutral boundaries while preserving the existing core business behavior. At M1 closure, provider adapters must be replaceable, AWS implementations may remain as compatibility, historical, or optional adapters, and a cloud-neutral configuration contract must exist with relevant business regressions passing.

M1 is not an effort to remove every AWS string or artifact, and it does not deploy a GCP runtime.

## Scope

M1 is limited to these decoupling areas:

1. backend external identity mapping and, only after that persistence boundary is established, JWT/resource-server validation and claim mapping;
2. frontend authentication/session integration;
3. object storage provider integration and configuration;
4. OpenSearch transport and authentication;
5. model and embedding provider selection/configuration; and
6. runtime configuration cleanup plus cross-boundary contract/regression validation.

## Reused contracts

Do not redesign the Spring Boot-centered application architecture, internal numeric `user_id`, user profile/display-name/role/status semantics, project ownership, comment attribution, existing project/file/comment flow, `AnalysisJob` lifecycle, MariaDB + Flyway, `AnalysisProvider`, `EmbeddingProvider`, `ReferenceRetriever`, `ObjectReader`/`ObjectWriter`, `TerraformDraftValidator`, the versioned RAG corpus/contract, Kubernetes-compatible workload contract, or existing deterministic validation assets.

Provider-specific adapters are separated so these contracts can be reused; they are not replaced merely to remove AWS-specific implementations.

## Out of scope

M1 does not include:

- GCP infrastructure deployment, GKE topology selection, or mixing Cloud Shell capacity discovery into M1 implementation;
- reliability experiments or fixes;
- RabbitMQ, Transactional Outbox, or other queue/outbox adoption;
- AI evaluation or improvement, model benchmarking, LangGraph, or agent architecture;
- observability stack deployment;
- database replacement or microservice decomposition; or
- wholesale deletion of historical AWS artifacts.

## Work sequence

Each task records evidence using **Problem / coupling**, **Current evidence**, **Change boundary**, **Validation**, and **Completion evidence**. When a task completes, replace its completion placeholder with its completion SHA or PR/evidence, executed checks, and the remaining task. Do not predict a future SHA.

### M1-1 — Backend external identity neutralization

**Status: DONE**

**Problem / coupling.** The persistent user identity and application lookup use Cognito-specific names: `users.cognito_sub`, `UserEntity.cognitoSub`, and `UserRepository.findByCognitoSub`. `AuthenticatedUserService` consumes the JWT `sub` directly, includes Cognito-specific messages and fallback claim interpretation, and maps it to the application user.

**Current evidence.** The coupling is present in `identity/AuthenticatedUserService.java`, `identity/UserEntity.java`, `identity/UserRepository.java`, `security/CognitoJwtSecurityConfig.java`, and baseline migration `V20260714_001__baseline_backend_schema.sql`. The database already uses numeric `user_id` for ownership and attribution relations.

**Change boundary.** Introduce provider-neutral external provider/issuer plus subject persistence and lookup semantics while preserving numeric `user_id`, profile, role/status, project ownership, and comment attribution. Preserve existing Cognito data compatibility through a non-destructive, additive Flyway migration if schema change is required; never edit an existing migration. Keep JWT validation/claim-adapter redesign for M1-2.

**Validation.** Add focused repository/service and migration compatibility coverage, then run the relevant identity and business regression checks. Verify existing Cognito subjects still resolve to their existing internal users without ownership or attribution changes.

**Completion evidence.** At validated head `edc9eb87e9bb1105c4f5d94f117356768190db92`, the application persistence and lookup boundary uses the composite `external_identity_provider + external_identity_subject` identity. Additive migration `V20260923_005__neutralize_external_user_identity.sql` backfills existing users as provider `cognito` and subject `cognito_sub`, preserves `cognito_sub` as a compatibility mirror and retains the same numeric `user_id`; project/file/board/comment ownership and attribution relations are unchanged. Concurrent-create retry now re-queries the neutral identity. Backend Local Verification was **SUCCESS**: both **Backend local smoke baseline** and **MariaDB schema and repository validation** passed, including Flyway, schema validation, and repository smoke against MariaDB 11.4.

This is a narrowly scoped persistence/application lookup change. It does not include frontend auth changes, IdP selection, GCP auth integration, a full JWT/security rewrite, storage, OpenSearch, or model changes.

### M1-2 — Backend JWT / Resource Server boundary

**Status: DONE**

**Problem / coupling.** `CognitoJwtSecurityConfig` validates Cognito-specific `token_use` and `client_id` claims, while `AuthenticatedUserService` contains Cognito-specific JWT assumptions and wording.

**Current evidence.** `security/CognitoJwtSecurityConfig.java` constructs issuer/JWK validation and Cognito claim checks; `identity/AuthenticatedUserService.java` reads subject and profile claims directly. M1-1 must first establish neutral persistence/lookup semantics.

**Change boundary.** Separate generic issuer/audience/resource-server validation from provider-specific claim mapping into internal external-identity semantics. Cognito compatibility may remain as an adapter. Do not select an exact IdP.

**Validation.** Verify accepted and rejected token/claim cases, internal-user mapping, security routes, and existing authorization regressions.

**Completion evidence.** At validated head `9b32f0ded961da74e5a83f4af7ac80b91872cc81`, generic `JwtResourceServerSecurityConfig` owns the resource-server security policy and issuer/JWK decoder wiring, while `JwtProviderTokenValidator` isolates provider-specific validation through `CognitoAccessTokenValidator`. `JwtExternalIdentityMapper` and `CognitoJwtExternalIdentityMapper` map JWT claims into provider-neutral `AuthenticatedExternalIdentity` values before application persistence, and `AuthenticatedUserService` no longer reads Cognito-specific claims or hard-codes the Cognito provider key. Current Cognito compatibility remains intact: provider key `cognito`, JWT `sub`, `token_use=access`, configured `client_id`, display-name precedence/fallback, email collision handling, and concurrent-create retry. Backend Local Verification was **SUCCESS** at this head: both **Backend local smoke baseline** and **MariaDB schema and repository validation** passed.

### M1-3 — Frontend auth/session boundary

**Status: DONE**

**Problem / coupling.** `AuthSessionContext.js` and `utils/api.js` call `aws-amplify/auth` directly, while `awsConfig.js` constructs Cognito configuration and provider-specific errors leak into UI paths.

**Current evidence.** The current frontend exposes `checking`, `authenticated`, and `guest` session states, protected navigation, login/logout behavior, token attachment, API 401/403 handling, and profile synchronization around direct Amplify/Cognito calls.

**Change boundary.** Preserve those UI and business semantics behind a provider-neutral auth/session client, with provider-specific behavior in an implementation adapter. Do not choose an OIDC library or IdP in this task.

**Validation.** Run focused auth/session, protected-route, API error-handling, and existing route/business-flow tests against the boundary.

**Completion evidence.** At validated head `1e72c4d9320f3e4af82aeede8fb4c63366e80de9`, frontend application/UI code consumes provider-neutral `authClient.js` capabilities, while current Cognito/Amplify behavior is isolated in `auth/providers/cognitoAmplifyAuthClient.js`. `AuthSessionContext`, `utils/api.js`, login/sign-up/reset/confirmation components, and application bootstrap no longer import Amplify auth APIs directly. Existing `checking`/`authenticated`/`guest` state, protected-route behavior, neutral user shape, nickname profile synchronization, bearer token selection, exactly-once 401 retry, auth-expired signaling, login/logout/sign-up/reset/confirmation flows, and Cognito compatibility are preserved. Guest detection and Amplify user/token/configuration details remain inside the provider adapter, including sequential current-user then attribute resolution. Frontend CI was **SUCCESS**: dependency installation, all frontend tests, production bundle build, production entrypoint verification, and artifact upload passed. Frontend Delivery Contract Verification also passed.

### M1-4 — Object storage decoupling completion

**Status: TODO**

**Problem / coupling.** Provider-neutral `ObjectReader` and `ObjectWriter` already exist, but S3 adapter selection/configuration and bucket/key/provider naming must be inspected for leakage into application services and DTOs.

**Current evidence.** `storage/AwsS3ObjectReader.java` and `storage/AwsS3ObjectWriter.java` implement the existing ports; `SourceObjectReaderService`, `UploadObjectStorageService`, and `ProjectArtifactService` consume the ports. Production configuration exposes S3-specific enablement and bucket values, and the baseline schema contains historical storage fields.

**Change boundary.** Reuse the ports and make only the minimum selection/configuration and type-boundary changes needed to keep AWS SDK types and resource assumptions out of application contracts. Retain the S3 adapter unless evidence supports a separate removal; do not automatically select GCS or redesign storage.

**Validation.** Inspect application/service signatures for vendor types and run relevant upload, source-read, result-storage, and project/file regressions with adapter selection coverage.

**Completion evidence.** Pending. Record completion SHA or PR, tests/checks, and remaining task here.

### M1-5 — OpenSearch transport/auth boundary

**Status: TODO**

**Problem / coupling.** `OpenSearchReferenceRetriever` depends directly on `SignedOpenSearchHttpClient`, which owns AWS credential signing and the configurable `aoss` service assumption.

**Current evidence.** `ReferenceRetriever`, `OpenSearchKnnQueryBuilder`, and `OpenSearchResponseParser` express reusable retrieval behavior, while `reference/opensearch/SignedOpenSearchHttpClient.java` provides the provider-specific transport/authentication path. Batch ingestion transport must be checked where applicable.

**Change boundary.** Preserve retrieval behavior, query builder, response parser, and corpus/index contract; introduce a provider-neutral OpenSearch transport/auth boundary with SigV4/AOSS retained as a provider adapter. Do not replace OpenSearch with another vector database.

**Validation.** Verify query payload and parsing regressions independently from transport, then exercise transport adapter selection/auth behavior and applicable ingestion checks.

**Completion evidence.** Pending. Record completion SHA or PR, tests/checks, and remaining task here.

### M1-6 — Model / embedding provider configuration

**Status: TODO**

**Problem / coupling.** `AnalysisRuntimeProperties` uses Bedrock-specific model fields and enablement, while `BedrockAnalysisProvider`, `BedrockArchitectureFactsExtractor`, and `BedrockEmbeddingProvider` bind those values to AWS runtime clients.

**Current evidence.** Provider-neutral `AnalysisProvider` and `EmbeddingProvider` already exist. The Bedrock implementations and `BedrockRuntimeConfiguration` are concrete adapters, while retrieval and analysis currently share Bedrock-specific settings.

**Change boundary.** Retain `AnalysisProvider`, `EmbeddingProvider`, `AnalysisResult`, provider-neutral prompt/result contracts, and corpus contract; separate generic provider selection/configuration from Bedrock-specific runtime and model identifiers. Do not select a specific model/provider or change the embedding model as an M1 objective.

**Validation.** Verify provider selection/config binding and existing analysis, embedding, parsing, validation, and disabled/stub behavior without changing quality claims.

**Completion evidence.** Pending. Record completion SHA or PR, tests/checks, and remaining task here.

### M1-7 — Runtime configuration neutralization

**Status: TODO**

**Problem / coupling.** `application-prod.yml`, `AnalysisRuntimeProperties`, security configuration, and storage conditionals use AWS product names as generic application selection/configuration concepts.

**Current evidence.** Production required environment entries and settings directly name Cognito, S3, Bedrock, AOSS/SigV4 service selection, and AWS-specific adapter enablement.

**Change boundary.** Establish provider-neutral identity, analysis, embedding, storage, and OpenSearch configuration concepts based on the implemented boundaries. Preserve an AWS compatibility profile/configuration where needed. Exact names are chosen only from the implemented code/config contract; this task does not implement GCP runtime configuration.

**Validation.** Verify configuration binding, startup/required-variable inspection, adapter selection, compatibility configuration, and absence of provider-specific settings masquerading as generic application contracts.

**Completion evidence.** Pending. Record completion SHA or PR, tests/checks, and remaining task here.

### M1-8 — Contract/regression verification and closure

**Status: TODO**

**Problem / coupling.** M1 cannot close from boundary changes alone; cross-boundary compatibility and preserved business behavior require consolidated evidence.

**Current evidence.** The repository contains existing deterministic unit/integration and business regression assets, while each preceding task records its focused evidence.

**Change boundary.** Perform closure inspection and validation only; address any verified boundary regression with the smallest attributable fix rather than expanding M1 architecture.

**Validation.** Inspect provider-neutral contracts; run relevant unit/integration and business regressions; check vendor SDK/type leakage in domain/application contracts, schema/data compatibility, and generic configuration boundaries; consolidate M1 evidence and M2 Runtime Parity readiness.

**Completion evidence.** Pending. Record closure SHA or PR, exact checks/results, residual limitations, and M2 readiness here before changing M1 to `COMPLETE`.

## Exit criteria

M1 becomes `COMPLETE` only when repository evidence shows all of the following:

- backend external identity semantics use a provider-neutral boundary;
- frontend auth/session integration is behind a provider-neutral boundary;
- the object-storage application contract remains provider-neutral;
- OpenSearch retrieval logic is separated from AWS transport/authentication;
- `AnalysisProvider`/`EmbeddingProvider` selection and configuration are provider-neutral;
- a generic runtime configuration contract exists;
- internal user/domain/business semantics, including ownership and attribution, are preserved;
- MariaDB/Flyway contracts and existing data compatibility are preserved;
- relevant existing business regression tests pass;
- provider-specific SDK/types are not unnecessarily exposed through domain/application contracts;
- AWS-specific implementations remain only as adapters or historical/reference assets;
- no arbitrary GCP product is selected by M1;
- M1 closure evidence is recorded; and
- M2 Runtime Parity readiness is recorded.

Removing every AWS string is explicitly not an exit criterion.

## Deferred / gated decisions

M1 does not approve any deferred technology. RabbitMQ, Transactional Outbox, LangGraph, a persistent Python AI worker/service, Keycloak, Redis, Kafka, and multi-agent architecture remain **DEFER/GATED** under ADR-004.

The following GCP/runtime choices also remain **GATED**: GKE topology, node/VM sizing, managed database, exact IdP, exact object storage implementation, exact OpenSearch hosting/auth/network topology, generation model/provider, embedding provider, and observability topology. Any new message broker, database, service split, AI service, queue/outbox, agent framework, or comparable architecture must independently satisfy the evidence gate.

## Immediate next work

**M1-4 — Object storage decoupling completion**

기존 `ObjectReader`/`ObjectWriter` application contract를 유지하면서 S3 adapter 선택/configuration과 bucket/key/provider naming이 application/service/DTO 경계에 누출되는지 검증하고, 확인된 provider coupling만 최소 수정한다.

This task does not select GCS or redesign storage; the current S3 implementation may remain as a compatibility adapter.

## Evidence and references

This plan follows the repository source-of-truth order and the evidence model above. Its initial code evidence was inspected at base SHA `16bbe8316344f8a616a2478cd85b85af2ba033c4`; task completion evidence must record actual future SHAs rather than predictions.

- [Repository working contract](../../../AGENTS.md)
- [AI project state](../../AI_PROJECT_STATE.md)
- [Modernization master plan](../MASTER_PLAN.md)
- [Completed M0 plan](M0-baseline-and-governance.md)
- [Component inventory](../../architecture/component-inventory.md)
- [Target architecture](../../architecture/target-architecture.md)
- [GCP deployment architecture](../../architecture/deployment-gcp.md)
- [ADR-001: Project scope](../../architecture/decisions/ADR-001-project-scope.md)
- [ADR-002: Cloud-neutral boundaries](../../architecture/decisions/ADR-002-cloud-neutral-boundaries.md)
- [ADR-003: Evaluation before complexity](../../architecture/decisions/ADR-003-evaluation-before-complexity.md)
- [ADR-004: Change gates](../../architecture/decisions/ADR-004-change-gates.md)
