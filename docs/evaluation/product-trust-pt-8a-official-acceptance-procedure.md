# PT-8A frozen deployed official acceptance procedure

Procedure `pt8a-official-acceptance-v2` is frozen before any new observation. This PR prepares
execution; it does not authorize it or assert final product acceptance. The preparation base is
`2bdb73d20486262856bfa6b680b7bf221fa23ca0`, read once at activation. The live reviewed source,
publication run, image digest, rollout run and observation runs remain unbound until independent
preparation acceptance, USER merge and the separate live/model/cost gate. Main drift stops execution;
neither the preparation base nor historical corpus provenance is rebound.

Authority: [exact USER request](../evidence/product-trust-pt-8a/approved-request.md),
[Work Package](../../.agents/work-packages/product-trust-pt-8a-official-deployed-acceptance-v1.yml),
[PT-7 review 6038261202](https://github.com/siamese-lang/terraformers-platform/pull/255#issuecomment-6038261202).
The USER-authorized clean-v4 correction addresses only [independent review 6039675842](https://github.com/siamese-lang/terraformers-platform/pull/256#issuecomment-6039675842).
[Superseded v1 bytes](../evidence/product-trust-pt-8a/corrections/clean-v4-lineage-1/superseded-procedure-v1.md)
retain SHA `5baee129bf48603fde3659b03e5c74c026730155b7af3785f63bc56cd9413ab5` as pre-correction
procedure evidence, not an unchanged procedure identity. Version 2 is frozen before outcomes;
this correction authorizes no live activity. Autonomous repair remains 1; human correction is 1.

PT-7 is independently accepted at `b8645e564d4476c9ad8aad798d63d2a84b487787` and USER-merged
as the preparation base. Ruleset 24074505 now requires both `terraform-static-verification` and
`backend-required-verification`, integration 15368; other protections are preserved. This is the
substantive successor reconciliation, not a state-sync PR. Program version remains 4.

## Immutable input and interpretation authority

AWS official candidate revision **2**, identity
`3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757`, and all five exact images,
URLs, hashes, dimensions, media types, order and truth files are immutable. External review/USER
approval freezes the pinned pre-approval snapshots without rewriting their historical false/null
fields. Order is A, B, C, D, E. Preparation makes zero official fetches, uploads or model calls.

Inference receives only the verified image bytes and a neutral run-based project name. It never
receives truth, case label, expected components/relationships, documentation context or scoring
criteria. No final-case material enters the corpus. The observer reads acquisition fields only;
truth is opened by the independent reviewer **after inference**.

Semantic fidelity uses `image_observable_truth` only. `documentation_context` may disambiguate
symbols or repeated logical representations but cannot add invisible mandatory facts.
`draft_technical_closure` applies conditionally to relationships/resources the draft chooses:
coherent wiring/authorization and actual CLI/provider validity are required. Equivalent valid
authorization mechanisms remain acceptable. Unknown deployment-specific account IDs, domains,
certificates, names and user values may be variables, external references or editable inputs under
`REVIEWABLE_IAC_DRAFT`; no fabricated personalized values or silently missing required wiring.

## Phase A: reviewed release and readiness, before official A

1. Bind the actual independently reviewed, USER-merged main **once** as the later live source.
   Reuse `.github/workflows/gcp-backend-image-publish.yml` and the actual `backend/Dockerfile`:
   source revision -> immutable source-SHA tag -> remote sha256 digest. Preserve publication
   evidence. No mutable tag substitution, new publishing workflow or duplicated pin verifier.
2. Reuse the existing retained rollout operation in `gcp-target-runtime-dependencies.yml` under
   its explicit human authority. Record rollout evidence, exact Deployment image, ready replica,
   actual `BUILD_SOURCE_REVISION`, provider/model/retrieval/storage/JWT configuration. No rollout
   or runtime configuration change is performed by the observation operation itself.
3. Rebuild expected broad-v4 document bytes using provider commit
   `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`, actual image AWS schema 5.100.0, and original project
   decision source `1ae69d589ac3965733818819792d98c5638e0ae5`. Require historical full corpus checksum
   `da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a`. This checksum includes all
   document bodies/provenance; a changed decision or builder output fails rather than relabeling
   it with a governance-only SHA. Historical corpus source is **not** the deployed backend source.
   Require schema resources 1526, official/selected 1514, gaps 0, provider chunks 5387, decisions 8,
   total 5395. No new embedding request is made by the exact verifier.
4. **Before the first PT-8A readiness dispatch**, obtain a completed clean re-embedding receipt
   through the existing `gcp-target-corpus-ingestion.yml`. Historical lineage is already known
   unproven: do not dispatch a knowingly failing readiness job or reset its history afterward.
   Require explicit live/model/cost approval for this clean operation, exact reviewed source,
   frozen candidate, **this v2 procedure SHA**, purpose, corpus sources/checksum and model/dimension.
   Select `ingestion_mode=pt8a-clean-v4`, `corpus_version=terraformers-reference-v4`,
   `confirmation=REEMBED_REVIEWED_PT8A_CLEAN_V4`, exact `expected_sha` and `live_approval_comment_id`.
   The historical A7-7 token alone is insufficient. Ordinary v3 and A7-7 v4 modes retain their
   existing tokens, HEAD skips and current-SHA project-source behavior; only clean mode pins
   historical project source `1ae69d589ac3965733818819792d98c5638e0ae5`.

   Before WIF, the runner authenticates the repository USER's structured approval and main via
   GitHub and checks existing runtime dispatch history: any prior non-skipped PT-8A dispatch at
   this live source forbids clean ingestion/history reset. Existing protected environment,
   WIF, cluster, namespace, OpenSearch, service account and serial workflow group are reused.
   Clean mode does not reapply service-account/IAM configuration and refuses an existing
   ingestion pod. The pod rechecks the same public GitHub authority over verified HTTPS before
   embedding and after completion, without receiving the runner's GitHub token. Missing/unreadable
   authority or main drift fails closed. No new credential, IAM grant or GitHub dispatch is made
   by the script. If public authority is inaccessible, stop rather than expand access.

   Rebuild the exact corpus and enforce checksum/coverage/source identities. Run the full read-only
   verification described below **before any vector write**. Exact mapping, full ID universe,
   every non-vector source field and retained UUID must match. Otherwise stop at
   `HUMAN_REQUIRED: DESTRUCTIVE_INDEX_REBUILD_DECISION`; never create/delete/rebuild the index.
   If exact, freshly embed **all 5,395 documents** using `gemini-embedding-2 / 1536`, preserving
   existing title/content embedding semantics and bounded document-transport retry behavior.
   Perform vector-only `POST /{index}/_update/{id}` with no upsert, using every expected existing
   ID once, zero HEAD skips, no non-vector rewrite and no mapping metadata relabeling. A disappeared
   ID fails instead of being recreated. This is a full vector overwrite, not a new-document count.
   Cancel pending tasks after failure and settle active tasks; preserve acknowledged overwrite
   count and failed stage in a partial receipt. Existing transport retries are not a second corpus
   pass; no automatic workflow retry, rerun, resubmission or resume is permitted.

   Refresh, repeat full ID/non-vector/mapping verification, and require the same retained UUID
   before/after and unchanged canonical content identity. Verify representative retrieval using
   one already-fresh vector, without an extra embedding. Only completed successful GitHub run /
   digest-bound artifact with `embedded_this_run=5395`, `skipped_existing=0`, outcome `ingested`,
   exact UUID/pre-post identities/sources/checksum/model/dimension/approval/source/candidate/
   procedure can establish clean lineage. Partial/mixed, failed or unknown receipts remain
   non-reusable. Preserve failure binding/partial receipt artifacts; stop for review, do not rerun.
   In-place writes are not atomic: a failed run leaves model lineage unproven even when some/all
   vectors were updated. No receipt claims a complete rollback or uninterrupted model equivalence.

5. Only after that completed clean receipt, dispatch readiness. Through a temporary port-forward to the existing OpenSearch, inspect mapping fields/types,
   vector method/dimension, corpus metadata/checksum, index UUID and **match-all** total. Scroll
   the full ID universe with `embedding` excluded from `_source`. Require every expected ID once,
   no missing/extra ID, and exact equality of every non-vector source field, including content,
   resource metadata and provenance. Canonical content identity sorts IDs and JSON keys, preserves
   list ordering and UTF-8, excludes vectors, and uses SHA-256. Recheck total/UUID; release scroll
   resources in `finally`. Retain only hashes/counts/bounded differences, never source bodies.
6. Verify model lineage using an authenticated GitHub completed ingestion run/artifact, immutable
   artifact archive digest, receipt source/model/dimension/checksum, retained index UUID, canonical
   non-vector identity, `ingestion_mode=pt8a-clean-v4`, `embedded_this_run=5395`, `skipped_existing=0`. A self-declared current model,
   mapping checksum or dimensionality does not prove how old vectors were generated. No metadata
   write can retrofit that missing evidence.

The six classifications are exclusive. Only the first permits a readiness product job:

| Classification | Meaning/action |
| --- | --- |
| `EXACT_REUSABLE_COMPLETED_V4` | Exact mapping, full IDs/content and completed clean model lineage bound to retained UUID. Reuse. |
| `PARTIAL_INDEX` | Total/universe/snapshot incomplete, including equal-count missing plus extra IDs. Stop. |
| `STALE_OR_MIXED_MODEL_SPACE` | Source/mapping/checksum mismatch, duplicates or observed index drift. Stop. |
| `WRONG_MODEL_OR_DIMENSION` | Wrong vector contract or bound receipt model/dimension. Stop. |
| `MISSING_INDEX` | Exact index absent. Stop. |
| `MODEL_PROVENANCE_UNPROVEN` | Exact visible content may match, but authenticated clean model/index binding is absent. Stop. |

Transport/inspection failures also stop; no completed snapshot is claimed. This is a point-in-time
snapshot plus known ingestion lineage, not proof against an unobserved administrator rewriting
vectors later. The retained index must have no intervening writers. Repeat the read-only exact
inspection before each observation. A writer, unknown intervening ingestion or unresolved lineage
requires human review; counts/UUID alone do not prove continuous immutability.

[Historical audit](../evidence/product-trust-pt-8a/historical-provenance-audit.json) binds successful
corrected run **37344219922**, artifact **11359644809**, model gemini-embedding-2 / 1536 and checksum.
Its receipt lacks retained UUID and fresh-vs-skipped counts; the old ingestion skips existing IDs.
Disposition is **MODEL_PROVENANCE_UNPROVEN**, not a current live-index measurement. Do not claim
that all 5395 vectors were regenerated by that run. The clean reviewed build/embed/ingest above is therefore mandatory **before the first readiness
dispatch**, under later explicit live/model/cost authority.
No automatic rebuild, deletion, re-embedding or ceremonial ingestion is authorized by readiness.
The observer additionally rejects ordinary/historical receipts, a different live source or approval
comment before a readiness product job. The general verifier preserves ordinary all-fresh v4 receipt
compatibility; no old receipt is rewritten or accepted as PT-8A clean evidence.

7. After exact readiness and live authority, prepare the existing ephemeral placeholder JWKS
   fixture, refusing a non-placeholder or another owner. Submit **one** existing PT-1-05
   private-web-fleet controlled reference through authenticated production upload/AnalysisJob.
   It is a serving/retrieval mechanism sample, not PT-2 activity or official/generalization evidence.
   Preserve correlated job-ID retrieval logs with `REQUIRED`, Vertex query embedding, v4 index,
   positive hit count and expected official document provenance. Independent readiness review is
   required before A. This sample is not retried for green. Restore placeholder JWKS and delete
   private JWT/key files in the existing owned cleanup path on success or failure.

## Phase B: once-only authenticated official observations

Use **one case per protected manual dispatch**, followed by independent post-inference review
before the next case. This serial execution unit prevents a human-visible material semantic defect
from being discovered only after the remaining official inputs were consumed. No new queue,
workflow, external lock service or model judge is introduced.

Existing workflow: `gcp-target-runtime-dependencies.yml`, operation `pt8a-official-acceptance`.
Require main, exact `expected_sha=backend_source_sha=GITHUB_SHA`, immutable backend image digest,
`GITHUB_RUN_ATTEMPT=1`, confirmation `RUN_REVIEWED_PT8A_OFFICIAL_ACCEPTANCE_V1`, protected
`gcp-target-apply`, existing WIF/GKE/runtime permissions and USER live approval. Never dispatch
from the PR or rerun a run. GitHub serial runtime concurrency remains unchanged.

The `pt8a_request` JSON has only these fields (no truth):

```json
{"mode":"readiness","liveApprovalCommentId":123,"provenanceRunId":456,"provenanceArtifactId":789}
```

For an official observation use `mode=case`, exact `caseId`, and additionally `priorRunId`,
`priorArtifactId`, `priorReviewCommentId`. A uses the independently accepted readiness artifact;
B–E use the immediately preceding official artifact. Before cloud access and again before upload,
inspect authoritative main and workflow history. The prior artifact must be the latest non-skipped
PT-8A dispatch for that source; a stale readiness artifact cannot reset the ledger. An incomplete,
rerun, artifact-missing or indeterminate prior run stops. Every artifact is independently bound
to run/attempt/source/workflow/archive digest; do not accept a replacement inventory or run.

The repository USER's live approval comment must start `[HUMAN_GATE_APPROVAL:v1]` and bind:

```text
gate: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE
decision: APPROVED
reviewed_source_sha: <actual later reviewed main>
backend_image: <exact immutable full image digest>
candidate_identity: 3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757
procedure_sha256: <SHA-256 of this exact v2 document>
purpose: PT8A_CLEAN_V4_REEMBED_ALL_5395
corpus_checksum: da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a
provider_source: f7a3b98da589ab1d52756b0dcee0dbf2de83d635
project_decision_source: 1ae69d589ac3965733818819792d98c5638e0ae5
embedding_model: gemini-embedding-2
vector_dimension: 1536
```

The same approval comment binds both the preceding clean receipt and later readiness/cases;
its clean purpose fields are additional explicit cost authority, not implied by the observation
token. The observation confirmation token remains the existing `_V1` token for compatibility.

Fetch only the frozen HTTPS `docs.aws.amazon.com` raw image URL into a private temporary directory.
Require HTTP 200, exact URL with no redirects, raw SHA-256/size, HTTP media type, PNG signature and
IHDR dimensions before upload. Reject changed host, replacement, redirect, conversion or restyling.
Do not fetch during preparation tests; tests use synthetic bytes/mocked transport. Do not store
official image bytes in Git or artifacts.

Perform exactly one multipart POST with verified file bytes and neutral project name. No curl
retry. Accepted job/object persistence consumes the case regardless of later outcome. A definite
pre-acceptance rejection is recorded separately and does not consume an accepted sample; it still
stops automatic progression for review. Transport loss, 5xx, malformed/ambiguous successful POST
or missing accepted identity is `INDETERMINATE_ACCEPTANCE`: **never resubmit**, even if no job ID
was observed. Preserve the first attempt and leave later cases `NOT_RUN`.

Poll only the same authenticated owner-scoped job. Preserve accepted project/source/job identity,
persisted timing and terminal state. The polling window is 540 seconds; the already-started bounded
curl read and sleep can add at most 30 seconds. A missing terminal is NOT_PASS with the actual
censored observation duration, never a later replacement. The service contract remains
original `acceptedAt/createdAt` cutoff 480000 ms plus conditional operating-service Δ=P+B+S+T.
The PT-6R2 injected 5000-ms model is not a measured production SLO or outage guarantee. Review
actual accepted-to-terminal and stage evidence against that conditional contract; unexplained
over-age success or unestablished required operating bounds cannot be silently called PASS.

For SUCCEEDED, read the same persisted project and Terraform draft through authenticated APIs.
Check job/result-key consistency, quality and timing. Reuse an owned ephemeral pod with the exact
already-deployed backend image and its real Terraform **1.8.5**, AWS provider **5.100.0**, local plugin
directory and schema. Perform real `init -backend=false` and `validate`, retaining bounded exit/
diagnostic evidence. No AWS plan/apply, deployment values, Docker stub or pass-through validator.

## Phase C: post-inference independent scoring and evidence

The observer produces `REVIEW_PENDING`, never semantic PASS. Technical failure produces NOT_PASS.
The independent reviewer opens preserved first-observation HCL, result/quality/timing, input identity,
correlated retrieval IDs/provenance and frozen truth, and records all ten dimensions per case:

| Dimension | Required review |
| --- | --- |
| Architecture classification | Positive architecture accepted as architecture; ambiguity/rejection does not masquerade as fidelity. |
| Components/service families | All visible major services recovered; accept valid icon/semantic aliases. |
| Directed relationships | Preserve depicted edges/flow and coherent Terraform relationships. |
| Containment/cardinality | Preserve material VPC/subnet/AZ/group/parallel/join semantics, not exact resource names. |
| Forbidden/invented interpretations | No material invented edge/service or forbidden misreading. |
| Resource intent | Resources represent depicted roles rather than coincidental OCR/name matches. |
| Personalized inputs | Editable variables/references are valid; no fabricated user/account-specific truth. |
| Official evidence closure | Selected official evidence appropriate to generated resources; missing exposed trace is explicitly unproven, not fabricated. |
| Real CLI / reviewable draft | Actual image CLI/provider succeeds and chosen wiring/authorization is coherent. Syntax PASS alone is insufficient. |
| Persisted/user-visible trust | Job/project quality and result agree; conditional evidence-backed draft is not unconditional executable/deployment success. |

The current runtime exposes correlated retrieval IDs and stage logs, summaries and persisted quality,
not raw vision facts or a complete generated-resource closure/repair trace. The artifact marks those
limits explicitly. Reconstruct selected document provenance from the exact expected corpus; never
infer an unseen closure result from an initial retrieval hit. A reviewer must distinguish supported
input interpretation, retrieval/grounding, generation/topology, technical and trust failures, marking
unobservable causality as unproven. No production trace changes or invented independent evidence.

The per-case review begins `[PRODUCT_TRUST_REVIEW:v1]`, identifies reviewed source/procedure/candidate,
the run ID, artifact ID and exact artifact digest, readiness or case ID, all ten dimension findings,
and `decision: ACCEPTED` only when all required dimensions PASS. Machine progression additionally
requires `dimension_<name>: PASS` for each observer `SCORING` key (readiness instead requires
`dimension_readiness: PASS`), `material_defect: false` and `false_trusted_success: 0`; failures use CHANGES_REQUIRED/
NOT_PASS, preserve evidence and stop. Readiness review uses `case_id: readiness-only`. A material
defect creates a later bounded correction Work Package under required authority, never a changed
truth, new judge, replacement sample or automatic retry. E still requires independent review;
no final acceptance or PT-8B activation is automated by this observer.

Artifact evidence is bounded: release/binding/readiness, five-row ledger, verified input identity,
accepted attempt/job, sanitized project/quality/timing, correlated retrieval/provenance/stages,
generated HCL after credential screening, real CLI diagnostics, error classification and SHA-256/
size inventory. Raw HTTP/log bodies, keys, JWTs/access tokens, credentials, vectors and official
image bytes remain private or excluded. If HCL resembles a secret, retain hash/result object identity,
stop and require sanitized independent inspection; do not publish it. A hash proves bytes, not safety
or semantic correctness. Private fixture/JWKS cleanup and owned validation-pod removal are mandatory.

Final result needs five preserved once-only observations, ten PASS dimensions each, no material
defect and **FALSE_TRUSTED_SUCCESS=0**, plus independent result acceptance. No aggregate percentage
can hide a positive failure. Scope is exactly five external official positive images and one reviewed
representative runtime; it does not establish universal architecture/negative-input generalization.
PT-1 remains controlled reference-derived, PT-2 remains final INCOMPLETE (03–10 NOT_RUN, aggregates
null, case-02 censor 424846 ms), A7 and PT-6 remain mechanism-scoped. No historical result is promoted.

Preparation ends at **HUMAN_REQUIRED: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE**.
No live execution, official fetch, ingestion, publish, rollout, browser, PT-8B, teardown, self-acceptance
or merge is authorized by this PR or by CI green.
