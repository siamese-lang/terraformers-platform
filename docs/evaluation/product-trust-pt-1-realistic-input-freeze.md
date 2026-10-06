# PT-1 realistic-input candidate and human truth-freeze checkpoint

## Result

**REALISTIC_DATASET_CANDIDATE_READY / HUMAN_REQUIRED: REALISTIC_DATASET_TRUTH_FREEZE**

This is candidate preparation, not PT-1 completion or a realistic-model performance result.
PT-2 remains ineligible. The candidate PR also requires its separate merge checkpoint.

- Program: `product-trust-v1`; Work Package:
  [product-trust-pt-1-realistic-input-benchmark-v1](../../.agents/work-packages/product-trust-pt-1-realistic-input-benchmark-v1.yml).
- Recovery main and the separately read activation main both resolved to
  `0bb244756c2a2e1d3d6d11d0342e98e155f8662c` on 2026-10-06.
- The Work Package contract was already on that main. At first implementation write, remote main
  was read exactly once and bound to `execution_base_sha` and program `activationBaseSha`.
- Branch: `codex/product-trust-pt-1-realistic-input-benchmark`, created directly from that SHA.
  No main refresh, rebase or second active Work Package was used.
- Candidate PR: [#241](https://github.com/siamese-lang/terraformers-platform/pull/241), open for review; no merge authorized.
- Dataset: [terraformers-realistic-v1](../../evaluation/terraformers-realistic-v1/README.md).
- Candidate identity: [candidate-identity.json](../../evaluation/terraformers-realistic-v1/candidate-identity.json),
  SHA-256 `8bc6daebde25045fcbb0a0bdad6deaedf2e9edeb29a28b39290fc107b6e952d8`.
  It pins 23 files: dataset, provenance, README, ten PNG inputs and ten editable SVGs.

## Observed gap and bounded change

The bound repository contained only the canonical/holdout synthetic positive image fixtures and no
additional user/project-created realistic diagrams. Existing positive evidence could support
controlled regressions but could not establish realistic-input generalization.

PT-1 adds six independently recreated public-reference architecture patterns, two incomplete-design
controls and two non-architecture controls. The original drawings use factual service labels instead
of third-party icons. Original geometry, annotations and control documents are repository-owned;
no AWS artwork, screenshots or source-page prose is redistributed. Public source pages were read
without authentication, and their URLs, resolved URLs, observed HTML SHA-256 and access date are
recorded in [provenance.json](../../evaluation/terraformers-realistic-v1/provenance.json).

The existing `m3-evaluation-v1` dataset schema and `EvaluationDatasetLoader` are reused unchanged.
The provenance supplement records semantic resource intent, equivalent descriptions and rejection
reasons without changing evaluation/scoring behavior. One loader test covers the new pinned inputs
and ensures separation from historical synthetic inputs. No parallel evaluator, workflow or fixture
renderer is added to the repository.

## Case review register

Each link opens the image, required components/relationships/resource types, semantic intent,
acceptable variants, forbidden interpretations and source basis together.

| Case | Candidate classification | Source / design basis | Distinguishing condition | Truth uncertainty / approval |
| --- | --- | --- | --- | --- |
| [01 serverless portal](../../evaluation/terraformers-realistic-v1/README.md#pt1-01-serverless-portal) | ARCHITECTURE_DIAGRAM | AWS three-tier architecture blog; explicitly selected HTTP API | Static and dynamic lanes; global CloudFront and regional API/data services | No unresolved factual guess; human review pending |
| [02 order fanout](../../evaluation/terraformers-realistic-v1/README.md#pt1-02-order-fanout) | ARCHITECTURE_DIAGRAM | SNS-to-SQS fanout plus Lambda SQS consumption specifications | One topic, two isolated queues/workers; polling versus direct invocation | No unresolved factual guess; human review pending |
| [03 parallel lookup](../../evaluation/terraformers-realistic-v1/README.md#pt1-03-parallel-lookup) | ARCHITECTURE_DIAGRAM | Step Functions documented Parallel State Example | Two branches join; workflow nodes are not separate deployed services | No unresolved factual guess; human review pending |
| [04 analytics catalog](../../evaluation/terraformers-realistic-v1/README.md#pt1-04-analytics-catalog) | ARCHITECTURE_DIAGRAM | Athena/Glue/S3 specification; explicit crawler/workgroup/output selection | Metadata versus object data; long bent query/read lines | No unresolved factual guess; human review pending |
| [05 private web fleet](../../evaluation/terraformers-realistic-v1/README.md#pt1-05-private-web-fleet) | ARCHITECTURE_DIAGRAM | Documented two-AZ VPC/private-server/NAT/ALB/ASG example | Dense nested subnet/AZ boundaries; shared versus per-AZ resources | No unresolved factual guess; human review pending |
| [06 thumbnail pipeline](../../evaluation/terraformers-realistic-v1/README.md#pt1-06-thumbnail-pipeline) | ARCHITECTURE_DIAGRAM | S3 event/Lambda two-bucket loop-prevention specification | Portrait layout; source-read reverse edge; separate output ownership | No unresolved factual guess; human review pending |
| [07 unresolved design](../../evaluation/terraformers-realistic-v1/README.md#pt1-07-unresolved-design) | AMBIGUOUS | Original design-notes control | Provider/services/boundaries explicitly TBD | Rejection truth is explicit; human review pending |
| [08 partial export](../../evaluation/terraformers-realistic-v1/README.md#pt1-08-partial-export) | AMBIGUOUS | Original incomplete-export control | Clipped endpoints; missing service/provider labels | Rejection truth is explicit; human review pending |
| [09 sprint board](../../evaluation/terraformers-realistic-v1/README.md#pt1-09-sprint-board) | NON_ARCHITECTURE_IMAGE | Original planning-board control | Cloud service mentions are work items without topology | Rejection truth is explicit; human review pending |
| [10 workshop table](../../evaluation/terraformers-realistic-v1/README.md#pt1-10-workshop-table) | NON_ARCHITECTURE_IMAGE | Original nontechnical schedule control | Table/document layout; no deployment intent | Rejection truth is explicit; human review pending |

Codex drafted labels from inspected source specifications and deterministic drawing inspection.
Terraformers/Vertex/Gemini was not called, did not author truth and did not validate itself.
All ten images were inspected before presenting this candidate. Intentional incompleteness in
controls is different from unresolved uncertainty about their rejection truth. No questionable
positive truth was silently weakened to keep a case.

## Original acceptance review before human freeze

These findings concern the Work Package's original `acceptance_criteria_before_human_freeze`;
they do not declare the program's human-frozen dataset acceptance satisfied.

| Original criterion | Evidence / result |
| --- | --- |
| Small, diverse candidate; not repackaged synthetic cases | Ten cases, six positives/two ambiguous/two non-architecture; distinct messaging, workflow, analytics and subnet topologies; all input hashes differ from both historical datasets. Candidate preparation satisfied, external-validity limitations remain below. |
| Pinned bytes or reproducible repository-owned representation | Ten SHA-256-pinned PNGs and ten pinned editable SVG representations; 23-file candidate identity verified. |
| Provenance and redistribution/recreation basis for every case | Per-case source basis and original-asset boundary in provenance/README; no copied artwork. |
| Reviewable positive component/relationship/resource-intent truth | Dataset exact expectations plus README/provenance semantic intent, variants and material forbidden interpretations for all six positives. |
| Explicit ambiguous/non-architecture rejection truth | All four controls set `terraformExpected=false` and `NOT_APPLICABLE`; per-case reason recorded. |
| Model-under-test not run on candidate | Zero Terraformers/Vertex/Gemini inference, embedding or evaluation calls in this execution; only public source reads, drawing construction/inspection and offline loading/contract tests. |
| No output-driven candidate changes | No Terraformers result exists. The one mechanical annotation correction below preceded candidate submission and used drawing/source inspection only. |
| Existing synthetic datasets unchanged | Exact base-to-head path audit includes no canonical/holdout dataset changes. |
| No production or live-cloud change | Diff contains dataset/assets, one test and allowed durable-state/review documents only. |
| Every case and remaining truth uncertainty listed | Register above and detailed README list all cases. No unresolved factual guess retained; every label is still awaiting human confirmation. |
| Program stops at truth-freeze gate | Program/WP are `HUMAN_REQUIRED`; truth approver/freeze timestamp remain null; PT-2 not activated. |

## Validation and bounded repair

1. Initial offline focused validation:
   `mvn -o -f backend/pom.xml -Dtest=EvaluationDatasetLoaderTest,CaseAHoldoutDatasetTest,EvaluationContractTest test`.
   **10 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESS**. It compiled backend/test sources under JDK 17.
2. Drawing inspection exposed two annotation inaccuracies in the initial render: a regional-services
   grouping included global CloudFront, and the shared footer described original controls as
   reference-based redraws. **One bounded mechanical repair** corrected the grouping to explicitly
   name CloudFront as global and the footer to state the original-asset boundary. No topology,
   expected classification, truth obligation, scoring or model behavior changed.
3. Because PNG/SVG identities changed, the existing loader suite was run once against corrected bytes:
   `mvn -o -f backend/pom.xml -Dtest=EvaluationDatasetLoaderTest test`.
   **4 tests, 0 failures, 0 errors, 0 skips; BUILD SUCCESS**.
   The unaffected five contract and one holdout tests retained their initial PASS; no rerun-until-green.
4. One-off standard-library identity/manifest audit: **PASS** for ten PNG hashes, ten SVG hashes/XML,
   all 23 identity entries, 6/2/2 composition, case/provenance alignment, source references and explicit
   positive intent/negative rejection reasons. No new verifier was committed.
5. `git diff --check` and exact allowed-path audit: **PASS**. No production, frontend, infrastructure,
   corpus, workflow, migration or historical dataset edits.

CI status is supplementary and does not replace this acceptance review. No live AI/GCP/OpenSearch,
image publication/deployment, authenticated runtime smoke, Terraform apply or teardown was executed.

## Limits and required human decision

The six positives are independent reference-based recreations, not real user uploads. Their layout
and topology diversity improves the candidate beyond the historical set, but still does not measure
third-party icon recognition, noisy photos, arbitrary screenshots or multilingual input fidelity.
No live fidelity, false-trusted-success, latency, executable-validity or product-success result is
claimed. Existing exact-string/resource-type scores cannot alone decide full semantic equivalence,
resource cardinality or boundary correctness; the review supplement preserves those obligations.

Human review must confirm all ten visible inputs and their full truth at the pinned candidate
identity, or identify a specific candidate correction. The Work Package explicitly requires:
**"Stop at the REALISTIC_DATASET_TRUTH_FREEZE human gate with a reviewable candidate."**
This invocation stops there. Truth approval and PR merge are not inferred from tests or CI. After
review/merge and an explicit truth freeze, PT-2 still needs its separately approved
`LIVE_REALISTIC_BASELINE` checkpoint; it must not start from this candidate-preparation result.
