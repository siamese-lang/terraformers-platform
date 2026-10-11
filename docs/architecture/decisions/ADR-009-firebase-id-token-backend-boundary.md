# ADR-009: Firebase ID Token at the existing backend security boundary

## Status

USER-selected direction in issue #281; F1 implementation proposed for independent review.
No Firebase tenant, hosted frontend, public ingress or live cost is authorized by F1.

## Problem and evidence

The private GKE backend has a reusable JWT Resource Server, stable external identity
mapping and owner-scoped project/job APIs. Its deployed JWKS is a test fixture; there
is no hosted real-user registration or login. An unlisted handler could omit the
per-controller database status check. Upload and direct Job creation can schedule paid
analysis without an enrollment gate. Issue #281 records the browser/runtime evidence
and the USER's selection of Firebase Authentication for the hosted-user path.

## Decision

Keep the existing Spring JWT Resource Server and `(provider, subject)` DB identity key.
In explicit `firebase` mode, verify RS256 signatures with Google's Firebase signing-key
JWK endpoint and require exact `iss=https://securetoken.google.com/<project-id>`, sole
`aud=<project-id>`, valid expiry/issued-at, nonempty bounded UID, `auth_time` and Firebase
provider metadata. Map only the signed UID to `provider=firebase`; an equal email never
links it to a Cognito user. Self-created users remain `USER/ACTIVE`, never ADMIN.

Every protected route checks the mapped account status at the common authorization
boundary, including future unlisted routes. Public GET behavior is retained. Firebase
billable submissions require `email_verified=true` and a server-side UID allowlist;
the allowlist defaults empty. Both existing billable entry points are gated before
creating a Job. A one-minute per-UID, per-process submission guard reduces bursts in
the currently single-replica runtime. Cognito/test mode remains explicit for historical
evaluation. Production refuses JWT-disabled startup, and Firebase release mode requires
the official JWKS URI. Only `local`/`test` may use the development permit-all chain.

## Rejected options and limits

The selected direction rejects Keycloak and copied custom password/session storage.
F1 does not mint tokens, create refresh tokens, or call Firebase Admin on each request.
A valid ID Token remains usable until expiry after Firebase-side revocation. Disabling
the DB account blocks protected requests immediately; forced logout is a server-side
disable action, not proof of remote token revocation. F4 must decide whether stronger
immediate revocation checks are needed for live public use.

The in-process interval is not a distributed quota or total cost ceiling. An empty
allowlist is the safe release default. Before F4 enables paid users, a separate
cost/security decision must bound lifetime and cross-replica consumption. Deterministic
F1 tests do not prove production security, HA or a hosted browser journey.

## Validation and rollback

Offline RS256 signed/unsigned and claim rejection tests, UID mapping and entitlement
tests, common disabled-user protection, existing owner/public-route regressions and
the full backend package test the boundary. Release can remain on explicit Cognito
mode until F4 authorizes Firebase activation. The test JWKS path is retained.

F3/F4 deployment blockers: a hosted release must select the Firebase-only profile,
bind the exact Firebase project issuer and official Google JWKS, and reject any test
JWKS, Cognito fallback or development permit-all configuration. Public routing must
exclude `/actuator/*` and `/internal/*` while preserving private operational access.
The application's anonymous monitoring GETs do not authorize public ingress exposure.
