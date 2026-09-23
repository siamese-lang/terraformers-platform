# M2 User Experience Baseline

**Status: PENDING**

This is an evidence-collection change, not a production behavior change. M2 remains **ACTIVE** and
M2-5 remains **TODO** until the authoritative workflow artifacts have been reviewed. The checked-out
base is `9d904c95655977c14e71fc1ac424dd91ce79550b` (the expected current GitHub `main`; the supplied
checkout has no local `main` ref).

## Runtime identity and evidence boundary

The verifier reuses `infra/kubernetes/overlays/portable-object-store` without modifying it: MariaDB
and Flyway, ephemeral signed JWT/JWKS authentication, filesystem test-runtime object storage, the
integrated Java stub analysis provider, and disabled embedding/retrieval. It uploads the fixed
68-byte PNG used by M2-4. The owner is `sub=m2-ux-owner` / `m2-ux-owner@example.test`; the second
identity is `sub=m2-ux-other` / `m2-ux-other@example.test`. Both tokens share the fixture issuer,
client ID, `token_use=access`, and ephemeral RSA key. Private keys, raw JWTs, and database passwords
are temporary and are not copied to artifacts.

The authoritative command is
`bash scripts/checks/kind-portable-user-experience-baseline.sh`; its machine-readable result is
`artifacts/m2-user-experience-baseline/backend-summary.txt`. Workflow success means that the
baseline was collected and required runtime assertions passed, not that M2-5 is complete.

## Backend actual HTTP matrix

The following expected values are assertions in the verifier. Actual PASS/FAIL values and sanitized
responses are produced by authoritative CI.

| Contract | Identity/state | Expected evidence |
|---|---|---|
| Upload and analysis | owner, private fixture | `POST /api/upload` 201; analysis `SUCCEEDED` |
| Owned list | owner | 200; project ID/name, PRIVATE, source/result/job linkage, `SUCCEEDED` |
| Private get | owner / other / anonymous | 200 / 403 / 403 |
| Terraform read | owner | 200 with generated content and result linkage |
| Terraform update | owner / other / anonymous | 200 / 403 / 401; exact owner read-back |
| Project tree | owner, private | 200; root, source and Terraform nodes, source/result/job IDs, PRIVATE |
| Private comments | anonymous read / authenticated write | 403 / 403 |
| Publish | other / anonymous / owner | 403 / 401 / 200 with PUBLIC response |
| Public project reads | anonymous | project 200; both list endpoints 200 and include fixture |
| Public artifacts | anonymous | source image, Terraform, and project tree each 200 |
| Canonical comments | anonymous read/write, other write | empty 200 / 401 / 201, then listed |
| Compatibility comments | owner write, anonymous read | `/api/addProjectComment` 200; both comments listed by `/api/getProjectComments/{id}` |
| Attribution | database | canonical comment maps to other; compatibility comment maps to owner; spoof fields ignored |
| Deletion | second fixture | other 403, owner 204, subsequent get 404 and absent from owned list |

### Public/private and compatibility boundaries

Private metadata and artifacts are checked before publication. Public metadata, source bytes,
Terraform, tree, and comments are checked only after the owner publishes. The canonical comment API
uses `GET/POST /api/projects/{projectId}/comments`; the active frontend compatibility API uses
`GET /api/getProjectComments/{projectId}` and `POST /api/addProjectComment`. Both intentionally
exercise the same authenticated-user attribution semantics, including ignored spoofed author/email
request fields.

### Terraform, project-tree, and deletion contracts

The update body is deterministic provider-neutral HCL containing a single `terraform_data.m2_ux`
resource. The verifier requires byte-for-byte API read-back, updated database inline content and
checksum, and equality between the database checksum and the filesystem result object SHA-256.
The tree must retain the project root, source folder/file, Terraform folder/main.tf, source file ID,
latest result file ID, latest analysis job, and PRIVATE visibility before publication. Deletion is
covered with a distinct throwaway project so it cannot destroy public/comment evidence.

## Frontend regression and coverage matrix

The frontend job runs the locked install, full Jest suite, production build, and non-empty built
entrypoint check. Its artifact is `frontend-summary.txt`.

| Existing test asset | Mapped user-flow evidence |
|---|---|
| `App.test.js` | guest public routing, private-route login, authenticated routing, logout, expiry |
| `MyProjectsPage.test.js` | owned cards, detail navigation, delete success and failure UX |
| `ProjectDetailPage.test.js` | analysis polling, source/result loading, visibility, failure, delete UX |
| `PublicProjectsReadOnly.test.js` | public list/selection, comment list/create, thumbnail, display name |
| `ProjectTreeReadOnly.test.js` | source-image and tree/file presentation |
| `api.test.js` | bearer attachment, public/private classification, 401 retry/expiry |
| `AuthSessionContext.test.js` | neutral session state, nickname synchronization, logout, expiry |

Authoritative frontend fields are `frontend_tests`, `frontend_production_build`, and
`frontend_entrypoint`, each `PASS` or `FAIL`.

## Provider-specific visible UX classification

The focused classifier inspects only three active generic UI files and deliberately does not scan
historical infrastructure or provider adapters. At this base all three checks are expected to be
FAIL: `PublicProjectsReadOnly.js` presents Cognito as the signed-in identity, `ProjectDetailPage.js`
presents Bedrock as the active waiting model, and `ProjectTreeReadOnly.js` renders historical logical
bucket/key fields as an `s3://` locator. The classifier records:

```text
public_identity_copy_provider_neutral=FAIL
analysis_waiting_copy_provider_neutral=FAIL
project_tree_locator_provider_neutral=FAIL
known_provider_specific_visible_copy=3
```

These findings are evidence only and do not fail the workflow. No frontend production source is
changed in this baseline.

## Browser E2E requirement classification

`browser_e2e_required_for_m2=false`. The actual portable-backend HTTP matrix covers authorization,
visibility, persistence, attribution, and deletion. Existing routed auth/session tests, page and
component interaction tests, API token/public-path tests, plus a production build cover the M2
frontend contract. No concrete M2 exit requirement has been identified that can only be observed in
a real browser. Consequently Playwright, Cypress, Selenium, a frontend container, Nginx, and a new
Kubernetes frontend workload remain deferred; general desirability is not evidence for adding them.

## First confirmed gap

If every runtime assertion passes, the baseline classification is
`first_confirmed_gap=frontend_provider_specific_visible_copy`, with the three detailed FAIL values
above. Any runtime HTTP failure takes priority and its first failing check from `backend-summary.txt`
is authoritative. Review must not advance M2-5 or M2-6 before authoritative artifacts are available.

## NOT COVERED

- Fixing any backend or frontend behavior found by the baseline.
- Real cloud credentials, production object storage selection, or durability beyond the test pod.
- Browser-specific rendering, accessibility, cross-browser behavior, or end-to-end browser smoke.
- Load, concurrency, HA, backup/restore, production retention, or security penetration testing.
- Historical/provider-adapter source strings outside the three active generic UI presentations.

The immediate next single task is to run and review **M2 User Experience Baseline Verification**,
then apply only the smallest evidence-gated fix for the authoritative first confirmed gap.
