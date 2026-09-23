# M2 Runtime Parity Closure

**Status: PENDING**

This record consolidates already accepted M2 evidence. It does not rerun the heavy Kind, Java,
frontend, Terraform, or cloud workflows. M2 closure is a review of already accepted runtime and
regression evidence, not a new executable verification product. Milestone status changes only after
this evidence chain and the current repository state are reviewed.

## Closure base

- Base/current `main` SHA: `c87ba7b505d41b7471aa993a07ea8d3ce43971ee`.
- Milestone: M2 — Runtime Parity (`ACTIVE`).
- At validation start M2-1 through M2-5 are `DONE`; M2-6 is `TODO`.
- Closure verification reads the accepted documents and current source invariants rather than
  repeating runtime checks whose code path, dependencies, and configuration have not changed.

## Authoritative evidence chain

| Task | Evidence | Accepted identity |
|---|---|---|
| M2-1 portable runtime baseline | `docs/verification/m2-runtime-parity-baseline.md` | validated head `2623303347e15e580d83f583262c51d2573edbbd` |
| M2-2 portable persistent runtime | `docs/verification/m2-portable-persistent-runtime.md` | validated head `02a00efec167a92c8170e8787b68e28ce6dc2339` |
| M2-3 authenticated identity and ownership | `docs/verification/m2-authenticated-identity-parity.md` | validated head `eda4b9469f258b71aae8e28a0b3ec1a226c4412a` |
| M2-4 gap reproduction | `docs/verification/m2-upload-analysis-baseline.md` | accepted baseline evidence |
| M2-4 object-byte storage parity | `docs/verification/m2-object-byte-storage-parity.md` | validated head `99fa2864ae3ef86536332172c4a3e50be612e686` |
| M2-5 user experience baseline | `docs/verification/m2-user-experience-baseline.md` | validated head `a756c46efe88124528b5c0467c5e8c1e4ac461fb` |
| M2-5 frontend correction | `docs/verification/m2-user-experience-baseline.md` | validated implementation head `7d8cdf357cb16a9073c86c6eb5140e617219180d` |

## Runtime identity

The accepted runtime evidence uses Kind in namespace `terraformers-portable`, the Spring Boot
`prod` profile, MariaDB 11.4 with Flyway, and an ephemeral RSA/JWKS identity fixture. The existing
Cognito compatibility token validator is exercised only as the fixture adapter; the fixture is not
a production IdP. The M2-4/M2-5 runtime explicitly selects filesystem `ObjectReader` and
`ObjectWriter` adapters in the `portable-object-store` overlay, uses the `stub-integrated-java`
analysis provider, and disables embedding and retrieval. No live AWS or GCP credential is needed.
The filesystem adapter is a test-runtime-only mechanism and is not the canonical production
storage default.

## Exit criteria

### 1. Portable runtime startup is repeatable
- Result: PASS
- Evidence: M2-2 portable persistent runtime, validated head `02a00efec167a92c8170e8787b68e28ce6dc2339`.
- Limitation: the fixture is not a production deployment topology.

### 2. MariaDB + Flyway persistence contract is verified in an actual runtime
- Result: PASS
- Evidence: M2-2 MariaDB 11.4 readiness, five Flyway migrations, and repository smoke at `02a00efec167a92c8170e8787b68e28ce6dc2339`.
- Limitation: production HA, backup, and restart durability are not covered.

### 3. Authenticated identity to internal user to ownership semantics are preserved
- Result: PASS
- Evidence: M2-3 identity/ownership matrix at `eda4b9469f258b71aae8e28a0b3ec1a226c4412a`.
- Limitation: ephemeral JWT/JWKS is not a production IdP selection.

### 4. Architecture/image upload leads to the project/source-file contract
- Result: PASS
- Evidence: M2-4 object-byte storage parity at `99fa2864ae3ef86536332172c4a3e50be612e686`.
- Limitation: filesystem storage is test-runtime-only.

### 5. Analysis job lifecycle produces a terminal result
- Result: PASS
- Evidence: M2-4 lifecycle reached `SUCCEEDED` through `stub-integrated-java` at `99fa2864ae3ef86536332172c4a3e50be612e686`.
- Limitation: stub analysis is deterministic contract evidence, not AI quality evidence.

### 6. Terraform validation, result registration, and read-back are verified
- Result: PASS
- Evidence: M2-4 validation/registration/read-back and M2-5 Terraform update/read-back at heads `99fa2864ae3ef86536332172c4a3e50be612e686` and `7d8cdf357cb16a9073c86c6eb5140e617219180d`.
- Limitation: no live Terraform provider initialization or apply is claimed.

### 7. Core user/project/comment contracts are preserved
- Result: PASS
- Evidence: M2-5 portable backend runtime matrix, baseline head `a756c46efe88124528b5c0467c5e8c1e4ac461fb`, revalidated at `7d8cdf357cb16a9073c86c6eb5140e617219180d`.
- Limitation: load and recovery behavior are outside M2.

### 8. Required frontend user-flow evidence is repeatable
- Result: PASS
- Evidence: M2-5 tests, production build, entrypoint, and focused classifier at `7d8cdf357cb16a9073c86c6eb5140e617219180d`.
- Limitation: `browser_e2e_required_for_m2=false`; browser-specific rendering, cross-browser, and accessibility E2E are not claimed.

### 9. Runtime configuration and identity evidence is preserved
- Result: PASS
- Evidence: sanitized runtime identities and artifacts documented by M2-2, M2-3, M2-4, and M2-5.
- Limitation: secrets, raw JWTs, private keys, and passwords are deliberately excluded.

### 10. Provider-neutral application boundary remains intact
- Result: PASS
- Evidence: the current-source M1 closure verifier must report `m1_cloud_decoupling_boundary=passed`.
- Limitation: historical compatibility adapters remain preserved behind boundaries.

### 11. Historical AWS implementation is not restored as the active target
- Result: PASS
- Evidence: current M1 boundary reports historical AWS adapters preserved while the portable runtime requires no live AWS dependency or credential.
- Limitation: preserved historical adapters are not removal candidates in M2.

### 12. No arbitrary GCP product or topology is selected
- Result: PASS
- Evidence: current M1 boundary reports `gcp_provider_selected=false`; the M2 records make no runtime product selection.
- Limitation: this does not deny GCP as the deployment target; it defers product/topology selection beyond M2.

### 13. Residual limitations are explicit
- Result: PASS
- Evidence: the Residual limitations section below is verified by the closure checker.
- Limitation: limitations belonging to M3, M5, M7, or M9 are not converted into M2 failures.

### 14. Consolidated M2 closure evidence exists
- Result: PASS
- Evidence: this closure record links the accepted M2-1 through M2-5 runtime/regression evidence and their validated heads.
- Limitation: the closure record does not create a new runtime claim; M2/M2-6 status changes only after review of the linked evidence and current repository state.

## Residual limitations

M2 does not prove active production LLM quality, active production retrieval quality,
fixed AI evaluation dataset, model quality improvement, production IdP selection, production object storage selection,
or GCP runtime deployment. It does not prove filesystem persistence across pod recreation,
shared multi-replica object storage, process restart recovery, duplicate execution handling,
executor saturation, transactional failure recovery, load/performance targets,
observability root-cause analysis, or production HA/backup. It also does not prove browser-specific
rendering, cross-browser behavior, or accessibility E2E.

The portable filesystem adapter is a test-runtime-only mechanism. Ephemeral JWT/JWKS is not a
production IdP. `stub-integrated-java` is not AI quality evidence. These limitations remain inputs
to later milestones (including M3, M5, M7, and M9), not reasons to broaden M2.

## Historical baseline failures and resolution

- M2-1 observed the local-stub namespace apply failure; it was diagnosed as a missing namespace,
  and the later portable fixture passed the same workload boundary.
- M2-3 observed a JWKS namespace fixture failure and then a shell verifier initialization failure;
  each harness defect was diagnosed, fixed, and followed by the accepted same-condition run #3.
- M2-4 reproduced the metadata-only byte-persistence gap; the test-only filesystem adapter fixed
  that gap and the same byte/checksum path passed. An incorrect filesystem test-vector SHA was
  separately corrected before accepted validation.
- M2-5 observed a verifier image-tag mismatch; the harness aligned to the overlay image and the
  backend matrix passed. Its provider-visible frontend copy gap was then corrected and all three
  focused classifications passed.

These are preserved diagnostic history, not unresolved closure blockers.

## Closure review

M2-6 does not add another verifier or workflow. Review the accepted M2-1 through M2-5 evidence above,
confirm that no production/runtime code changed after the latest successful M2-5 validation in a way
that invalidates those claims, and update the M2 plan, master plan, and project-state documents.
If that review discovers a genuinely uncovered runtime claim, add only the smallest validation that
directly exercises that claim; do not create a closure-specific test of documents or previous tests.

Until this review is completed, M2 remains `ACTIVE` and M2-6 remains `TODO`.
