# Terraformers Evaluation Dataset v1

## Identity

- Dataset version: `terraformers-eval-v1`
- Evaluation schema: `m3-evaluation-v1`
- Case count: 6
- Fixture format: repository-owned lossless WebP
- Dataset file: `dataset.json`

This dataset is the fixed input baseline for M3. It is intentionally small: its purpose is to make
the current AI/RAG pipeline comparable and diagnosable before any quality-improvement change, not to
claim broad benchmark coverage.

## Case composition

| Case | Classification | Primary evaluation purpose |
| --- | --- | --- |
| `arch-vpc-three-tier` | architecture | component/relationship extraction, VPC/ALB/RDS retrieval, security-group project decision |
| `arch-cloudfront-private-alb` | architecture | CloudFront/private-ALB relationship and mandatory private-origin project decision |
| `arch-private-aoss` | architecture | private AOSS topology plus reader/ingester access-separation project decisions |
| `arch-s3-metadata-split` | architecture | object-content versus relational-metadata ownership project decision |
| `ambiguous-cropped-service-sketch` | ambiguous | reject incomplete/cropped topology and avoid Terraform generation |
| `non-architecture-deployment-dashboard` | non-architecture | distinguish operational UI from an architecture diagram |

The four positive cases are deliberately tied to concepts that the current versioned corpus can
support. Three of them require repository-owned `PROJECT_DECISION` documents so M3 can distinguish
"vector search returned something" from "the RAG path retrieved the project constraint it needed."

## Fixture ownership and hashes

The diagrams are synthetic fixtures created for this repository. They do not contain production
identifiers, credentials, account IDs, or external copyrighted diagrams.

`EvaluationDatasetLoader` reads each fixture as raw bytes and validates the SHA-256 recorded in
`dataset.json` before a case can be used. Hashes therefore identify the actual WebP bytes, not a
rendered screenshot or filename.

## Expectation semantics

The case contract uses three kinds of text/resource expectations:

- `required`: evidence that must be present for the corresponding stage to be considered correct;
- `acceptable`: additional evidence that is compatible with the case but not mandatory;
- `forbidden`: evidence that would be unsupported by the case.

The dataset does not require one exact natural-language explanation. Components and relationships are
canonical annotations for later normalization/scoring. Terraform expectations are expressed mainly
as resource types so M3 can detect unsupported resources without judging superficial formatting.

Retrieval expectations may require specific `PROJECT_DECISION` IDs because those documents encode
repository-owned constraints. Provider documentation/schema hits are otherwise evaluated by authority
and resource coverage rather than by one exact ranked document ID.

## Baseline freeze rule

Once this dataset is used for the M3 current-system baseline, failing cases must not be rewritten to
make the current implementation look better. Changes to prompts, corpus, ranking, models, or
orchestration belong to M4 and must be compared against the same dataset/configuration.

A later dataset version may add cases when a coverage gap is identified, but it must not silently
replace `terraformers-eval-v1`.

## Scope boundary

This dataset does **not**:

- select AWS as the modernization deployment target;
- change the current v2 RAG corpus;
- tune retrieval ranking or prompts;
- introduce LangChain, LangGraph, a judge model, or an additional vector database;
- run a live model or OpenSearch query.

The AWS-shaped positive cases reflect the currently versioned historical corpus used to establish the
baseline. GCP remains the modernization deployment target at the deployment layer.

## M3-3 handoff

M3-3 should reuse `EvaluationDatasetLoader` and the M3-1 `EvaluationTrace` contract to run all six
cases through one evaluation runner. It must record observed stage provenance rather than embedding
case-specific execution logic in the runner.
