# PT-7 — CI/CD trust gates and operational handoff

Bound execution base: **6228d69b1da604e816b3e0a826563d79b8bcd844**, remote main read once.
[Work Package](../../.agents/work-packages/product-trust-pt-7-ci-delivery-trust-v1.yml),
[authority/ruleset snapshot](../evidence/product-trust-pt-7/authority.json) and
[deterministic validation](../evidence/product-trust-pt-7/validation.json) bind this repository-only work.
PT-7 independent acceptance and USER merge are pending. GitHub owns PR lifecycle and exact-head CI.
The next gate after successful normal PR CI is
**HUMAN_REQUIRED: PT7_REQUIRED_CHECK_RULESET_CHANGE**; no ruleset write is authorized here.

## Predecessor reconciliation and limits

[PR #254 review 6037528315](https://github.com/siamese-lang/terraformers-platform/pull/254#issuecomment-6037528315)
accepted head `987ec944c84ac74f99f2c2f3ae38b0bc16499f8a`; USER merged it as the bound base.
PT-6R2 is COMPLETE for the independently accepted **conditional operating-service terminal contract**:
original accepted age 480000 ms plus Δ=P+B+S+T. The injected model gives Δ≤5000 ms only with
P≤2s and B/S/T≤1s. This is not a measured production DB SLO, outage guarantee or hard JVM/thread
cancellation. Original base `406981a8c629a02503f5660453dcc7921b8d5177`, automatic repair 1,
human corrective iteration 1, all successful/failed evidence and Option D behavior stay unchanged.
Normal CI is regression evidence, never a new PT-6R1 before/after measurement.

PT-2 remains final INCOMPLETE: cases 03–10 NOT_RUN; four aggregate realistic rates null;
case-02 latency censored at **424846 ms**; recovery runs remain zero-product-observation failures.
No additional PT-2 activity. Frozen AWS revision 2 identity
`3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757` remains externally approved,
with immutable pre-approval candidate bytes, model-under-test count 0 and execution unauthorized.

## Demonstrated gaps and correction

At activation, Backend Local Verification had a top-level PR paths filter and always inherited
`RUN_DOCKER_BUILD=false`; main ruleset `protect-main-for-delivery` (24074505) required only
`terraform-static-verification` (GitHub Actions integration 15368). A backend regression could
therefore escape protected-main checks, and Maven success did not validate the production image.

The existing workflow now classifies every main PR, selects its existing Maven/package and MariaDB
jobs, and always creates the single final check **`backend-required-verification`**. Scope failure,
missing/malformed flags, any required job failure/cancellation/skip, or missing selected image
verification fails the final check. Non-backend work passes that check while skipping expensive jobs.

| Changed path | Maven + existing MariaDB | Real production image build |
| --- | --- | --- |
| Non-backend docs/governance/frontend/infra | Skip | Skip |
| Backend tests/docs | Run | Skip |
| Existing backend check workflow/scripts or associated classifier/tests | Run | Skip |
| `backend/src/main/**`, `backend/pom.xml`, `backend/Dockerfile` | Run | Run |

Manual dispatch conservatively selects both flags, but is not used in PT-7. PR checkout and
`BUILD_SOURCE_REVISION` use `github.event.pull_request.head.sha`, rather than the synthetic merge SHA.
The selected script builds the **unchanged actual backend Dockerfile** and checks the resulting
image's exact embedded revision. Dockerfile owns Terraform 1.8.5, AWS provider 5.100.0, checksum,
schema and positive/negative validation; no second pin verifier is introduced. No image push.
This PT-7 PR changes CI wiring, not production image inputs: backend verification is selected,
Docker build is not. Local command doubles validate script binding/failure behavior only; they are
not evidence of a real image build. Selected future PRs run the real Dockerfile in normal CI.

Proposed settings change, requiring explicit USER approval after exact-head CI success:

- Keep `terraform-static-verification`, integration 15368.
- Add only `backend-required-verification`, same observed GitHub Actions integration (verify in CI).
- Preserve strict-policy=false, do-not-enforce-on-create=false, review count 0, all review options,
  merge/squash/rebase methods, deletion/non-fast-forward protection, default-branch selector and
  empty bypass actors. Do not require each child job separately.

Until that separately approved addition is applied, the backend check exists but is **not required**
by protected main. CI green does not establish independent acceptance or final product trust.

## Existing delivery contract: static audit only

The source-bound workflows and actual Dockerfile remain byte-identical to activation; their hashes
are in authority.json. No blocking source→digest→runtime contract defect was found in this static
audit. No registry, cluster, OpenSearch or model was queried; current live readiness is unestablished.
Historical [Case C image-delivery readiness](case-c-immutable-backend-image-delivery-readiness.md)
proves earlier delivery mechanics, not deployment of this source.

| Existing path | Enforced boundary | Evidence to retain at the later approved execution |
| --- | --- | --- |
| `gcp-backend-image-publish.yml` | Manual trusted main, protected `gcp-image-publish`, dedicated WIF publisher, `expected_sha == GITHUB_SHA`, explicit `PUBLISH_REVIEWED_BACKEND_IMAGE` | Exact workflow/source SHA and run identity |
| Same publish path | Actual Dockerfile, exact embedded revision, full source-SHA tag; refuses an existing tag, no `latest`; resolves and rechecks remote sha256 digest; no Kubernetes/Terraform mutation | Source tag, remote digest, immutable image reference, embedded source |
| `gcp-target-runtime-dependencies.yml`, `backend-revision-rollout` | Separate manual main-only `gcp-target-apply` gate, exact workflow SHA and `APPLY_REVIEWED_CASE_C_BACKEND_REVISION_ROLLOUT_1`; full `backend_source_sha`, digest-qualified image, source-tag→target-digest check | Approved source SHA, exact target digest and expected current image |
| Same rollout | Checks expected current deployment image/readiness before replacement, rolls out exact target digest, bounded rollout/rollback, deployed image and `BUILD_SOURCE_REVISION` equality | Before/after deployed identity, rollout result/health and any retained failure |
| Same rollout | Verifies actual `CORPUS_VERSION`/`INDEX_NAME` v4, `gemini-embedding-2`, dimensions 1536 after configuration and image rollout | Runtime identity; subsequent exact serving-content evidence still required |

## Later PT-8A handoff — all live steps separately gated

PT-7 acceptance, required-check reconciliation and USER merge do not authorize these operations.
Before consuming any frozen official input, the later approved Work Package must:

1. Select the exact reviewed PT-8A source SHA and confirm no main drift under its activation contract.
2. Use existing manual main-only image publication with that SHA; retain source tag, remote digest,
   immutable image and embedded revision. No automatic publish/deploy on merge.
3. Inspect the retained representative GCP runtime and record its exact current deployment image
   before mutation; confirm the separately approved budget and existing identity boundaries.
4. Use existing reviewed backend revision rollout with that exact digest/source and expected current
   image. Retain failures; no silent rollback-success substitution or new runtime.
5. Prove deployed image equality and `BUILD_SOURCE_REVISION == reviewed source`.
6. Prove actual runtime `CORPUS_VERSION=INDEX_NAME=terraformers-reference-v4`,
   `VERTEX_EMBEDDING_MODEL_ID=gemini-embedding-2` and vector dimensions **1536**.
7. Only then, under its separate OpenSearch gate, establish exact v4 content/checksum/model/index
   equivalence and correlated real retrieval readiness. Required universe remains schema 1526,
   official/selected 1514, gaps 0, provider chunks 5387, decisions 8, total 5395. Configuration
   names alone do not establish serving-content readiness; ingestion/repair is not automatic.
8. Only after readiness and explicit **FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE** may A–E
   first submissions occur, once each in frozen order, with natural failures preserved and frozen
   image-observable truth / documentation / conditional draft closure scored independently.

PT-8B later composes accepted evidence under source equivalence. Retain this one runtime until the
Program's separately reviewed destructive/cost checkpoint; PT-9 teardown requires explicit USER
approval, source/resource-bound plan, operation evidence and cost/resource closure. No teardown now.
No new IdP/IAM/security boundary, deploy-on-merge, model invocation, ingestion or live repair is implied.
