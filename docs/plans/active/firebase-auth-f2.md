# F2 — React Firebase Authentication adapter

Base: `db5695f89bc54565dc158b7f7d2a3ed746f77b02`. Authority: issue #283.
Status: repository-only implementation proposed for independent review and USER merge.

- Keep the existing `authClient` boundary and Cognito/development preview behavior.
  Explicit `REACT_APP_AUTH_PROVIDER=firebase` selects the official modular Firebase
  Auth SDK. Missing or malformed public web identifiers produce a visible,
  fail-closed configuration error instead of an identity fallback.
- Email/password registration sends a verification link. SDK sign-in alone never
  opens protected routes: an unverified user receives guidance and no Backend
  bearer. Confirmation reloads the Firebase user and refreshes the ID Token.
- Auth observer plus SDK restored-state readiness keep reload/tab, logout and
  invalid-session UI state synchronized. Protected API requests use the provider's
  selected token: Firebase **ID Token**, Cognito/preview historical access token.
  One protected 401 may force refresh and retry once; 403 never retries.
- F2 tests use SDK mocks and do not prove hosted Firebase or Backend JWT validation.
  F4 must activate and configure the Firebase project, authorized domains and email
  templates, then verify the hosted browser/Backend journey with a real ID Token.
  F1's server-side UID allowlist and DB authorization remain the cost boundary.
  Valid already-issued token revocation remains subject to F4's decision.

No Firebase/GCP activation, paid analysis, deployment, ingress change or F3 work is
authorized by this PR.
