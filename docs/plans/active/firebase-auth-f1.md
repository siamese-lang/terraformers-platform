# F1 — Backend Firebase authentication and authorization

Base: `33a98dd9ae21415b74fb22580a8fffaace0f7483`. Authority: issue #281.
Status: repository-only F1 PR, independent review and USER merge pending.

1. Reuse Spring JWT Resource Server and identity repository. Add explicit Firebase
   RS256/issuer/audience/time/UID checks and fail-closed release configuration.
2. Map signed UID to `(firebase, uid)` without email auto-linking; keep stable internal
   user ID, transaction-safe create, USER role and database ACTIVE/DISABLED state.
3. Enforce account state for all protected requests, retain intended public GETs,
   and default-deny paid upload/Job creation without verified server entitlement.
4. Run focused security/identity/upload tests, then the existing backend package once.

F1 excludes React, Firebase tenant activation, GCP/IAM/Ingress changes, live browser
journey, model calls and PT-8A outcome changes. F2–F5 require their own authority.
The F4 live checkpoint must review the token revocation gap, per-process throttle and
missing distributed/lifetime cost quota before allowing public billable users.
F3/F4 hosted deployment is blocked until the release configuration proves Firebase-only
JWT mode with the exact project issuer and official Google JWKS (no test JWKS, Cognito
fallback or development permit-all profile). The public ingress must exclude
`/actuator/*` and `/internal/*`; their backend health, metrics and runtime handlers
are for private operational access, even where the application permits anonymous GET.
F1 neither changes that ingress nor authorizes a hosted release.
