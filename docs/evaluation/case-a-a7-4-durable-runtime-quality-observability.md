# Case A A7-4 — durable runtime quality observability

Status: **COMPLETE — ACCEPTANCE PASS — A7-5 NOT STARTED — LIVE VALIDATION NOT PERFORMED**

## Closure evidence

- implementation PR: #213
- implementation merge SHA: `ab1b1f410b6aabf674af51d6161a2efd0f6df6a4`
- bounded repair commit: `0e457f7bd9339fc44f901bdd90b2c95f40c081b0`
- first Backend Local Verification: run `37209621451` — failure
  - MariaDB schema/repository validation already passed
  - Maven reported one error in `AnalysisJobStateServiceTest.onlyOwnedFailurePublishesFailedProgress`
  - root mechanism: the legacy four-argument `markFailedOwned` overload no longer entered its own
    `REQUIRES_NEW` transaction before self-invoking the quality-aware overload
- final Backend Local Verification: run `37210008792` — success
  - Maven clean tests and package passed
  - MariaDB Flyway/Hibernate schema validation passed
  - canonical repository smoke queries passed
- Terraform Static Verification: run `37210008786` — success
- independent A7-4 acceptance review: **PASS**
- no live Vertex/Bedrock call
- no live GCP action
- no v4 embedding/OpenSearch ingestion
- A7-5 is not started

## Decision

A7-4 will persist the bounded quality interpretation at the terminal AnalysisJob transition.

It will not recompute historical quality on API read. The current job record survives backend
restart, while the facts/reference context used by `EvidenceQualityAssessment` does not. A later
recomputation could therefore reinterpret an old job under a changed corpus or rule set.

The selected path reuses:

- `EvidenceQualityAssessment` / `evidence-quality-v1`;
- the existing durable `analysis_jobs` boundary and Case B ownership fencing;
- `AnalysisJobResponse`;
- Micrometer in `AnalysisObservability`;
- `analysisJobId` MDC correlation.

No second quality oracle or observability product is selected.

## Pre-implementation measured gap

A7-3 closure merge:

`1038263076dc267d090e12503fc8670138c6b48c`

A7-4 frozen execution base:

`9beec226a1cc5c314d6e6f27a62e6b63f20abab1`

Before A7-4 implementation, the repository had:

- versioned in-memory A7-1 quality representation;
- explicit A7-3 provider partial-failure mechanics;
- durable job lifecycle/result storage;
- API read-back for result/failure fields;
- low-cardinality operational metrics and job-correlated logs.

It did **not** yet have:

- persisted technical/knowledge/quality/project-decision/runtime-boundary state;
- persisted bounded quality reasons/version;
- quality state in the AnalysisJob API;
- terminal quality metrics/log event;
- restart-proof quality read-back.

## Recovered A7-0 official-knowledge gap evidence

The A7-4 preflight initially stopped because the exact 12-resource official-documentation gap set
was not committed and the Codex environment could not download the pinned provider artifacts.

The gap set was subsequently reproduced independently from the **same pinned provider source
commit** used by A7-0:

- provider source commit: `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`
- commit tree: `5a6a39a237cfdb28cf9f4fdb4a6e00fdf1affe36`
- `internal/service` tree: `08ac3f85622b439f826da9b5677414f0f0b86fda`
- `website/docs/r` tree: `fda313b7ec2d69be5077056340ba25d6dc763b0d`
- generated service-package registries inspected: **256**
- unique managed resource `TypeName` values from only `FrameworkResources()` and
  `SDKResources()`: **1,526**
- official `website/docs/r/*.html.markdown` files in the pinned docs tree: **1,520**
- managed resources with matching official documentation filenames: **1,514**
- managed resources without matching official documentation: **12**

Only the managed-resource registry functions were parsed. Data-source `TypeName` values were
explicitly excluded.

The exact sorted gap set is:

- `aws_account_region`
- `aws_alb`
- `aws_alb_listener`
- `aws_alb_listener_certificate`
- `aws_alb_listener_rule`
- `aws_alb_target_group`
- `aws_alb_target_group_attachment`
- `aws_api_gateway_rest_api_put`
- `aws_ec2_image_block_public_access`
- `aws_pinpoint_email_template`
- `aws_rds_custom_db_engine_version`
- `aws_securityhub_configuration_policy_association`

Therefore:

```text
1,526 managed schema/source registry resources
- 12 schema-known resources without official resource documentation
= 1,514 officially documented managed resources
```

This exactly matches the accepted A7-0 measurement. The result is derived from pinned provider
source structure, not current retrieval hits, not the historical v3 corpus, and not a fabricated
allowlist.

A7-4 may now package this exact set as compact versioned read-only coverage metadata, preserving the
pinned provider commit/tree provenance above. This recovery does not imply that v4 is served or
ingested.

## Persistence boundary

A7-4 uses one additive Flyway migration. Existing rows are not backfilled with guessed quality.

A terminal snapshot contains only bounded version/enum/reason values:

- contract version;
- technical status;
- knowledge status;
- quality status;
- project-decision status;
- runtime quality boundary;
- deterministic bounded reason names.

Raw prompts, images, provider payloads, generated HCL, reference content, or arbitrary exception /
Terraform diagnostic text are not quality persistence.

Success snapshot fields are committed with the owned SUCCEEDED transition. Final failure snapshot
fields are committed with the existing fenced FAILED transition. Retry rescheduling is not a
terminal outcome and must not persist a terminal quality snapshot.

## Runtime computation boundary

Successful provider paths must compute A7-1 quality while the original facts, selected
`ReferenceDocument` objects, and generated Terraform are still in memory.

Project-decision applicability remains UNKNOWN unless a repository-owned deterministic rule says
otherwise. A successful job may therefore legitimately have quality UNKNOWN. A deterministic
evidence gap may make a technically successful job DEGRADED. A7-4 must not force
EVIDENCE_BACKED to make a positive path look better.

A provider-neutral INPUT_REJECTED outcome is treated as a valid technical classification result:
technical PASS with quality/knowledge/project-decision NOT_APPLICABLE and no Terraform output.

## Official-knowledge boundary

A7-0 measured AWS Provider 5.100.0 as:

- schema resources: 1,526;
- schema resources with official documentation: 1,514;
- schema resources without official documentation: 12.

A7-4 may package compact read-only coverage metadata derived from that exact pinned source/schema
measurement. It must not infer official-knowledge availability from selected retrieval hits.

The preferred representation stores the 12 explicit official-knowledge gaps plus provenance,
rather than introducing a hand-maintained 1,514-resource production allowlist.

If the exact gap set cannot be reproduced or verified from the A7-0 pinned evidence, implementation
must stop instead of fabricating the list.

This metadata does not mean v4 is served. A7-4 does not perform embedding, OpenSearch ingestion, or
live corpus deployment.

### Verified exact A7-0 knowledge-gap set

The A7-4 preflight blocker was resolved without a live provider/runtime action by independently
reading the pinned public provider source commit
`f7a3b98da589ab1d52756b0dcee0dbf2de83d635` through GitHub's repository API.

The pinned resource documentation tree contains 1,520 Markdown files:

- 1,514 use the exact `.html.markdown` path recognized by the A7-0 compiler;
- 6 use legacy/non-matching `.markdown` names.

The generated managed-resource registries confirm those six legacy-named resources are managed
schema resources. The registries also expose six `aws_alb*` compatibility resource aliases with
no matching `.html.markdown` document.

Combined with the accepted A7-0 measurement of 1,526 schema resources and a 1,514-resource
schema/document intersection, the following 12 distinct resources exhaust the exact official
documentation gap set:

- `aws_account_region`
- `aws_api_gateway_rest_api_put`
- `aws_ec2_image_block_public_access`
- `aws_pinpoint_email_template`
- `aws_rds_custom_db_engine_version`
- `aws_securityhub_configuration_policy_association`
- `aws_alb`
- `aws_alb_listener`
- `aws_alb_listener_certificate`
- `aws_alb_listener_rule`
- `aws_alb_target_group`
- `aws_alb_target_group_attachment`

This set is now verified A7-0 evidence for A7-4. It must be represented as compact coverage metadata,
not expanded into a 1,514-resource production allowlist.

## API and observability boundary

`GET /api/analysis/jobs/{id}` gains one nullable additive quality object. Legacy rows without a
snapshot return no quality object rather than guessed state.

Micrometer records only finite status/reason labels. Job/project/resource/reference identifiers do
not become metric labels.

The terminal quality log uses the existing `analysisJobId` MDC and bounded enum/version values
only.

## Validation

Repository-only validation is sufficient for A7-4 implementation:

- focused quality persistence tests;
- provider quality attachment tests;
- ownership/stale-generation/retry regression;
- API compatibility;
- fresh repository-context/restart read-back;
- Micrometer cardinality checks;
- full Backend Local Verification including MariaDB Flyway/Hibernate/repository validation.

No live Vertex, Bedrock, OpenSearch, GCP, or cost-bearing action is authorized.

## Recovered A7-0 exact official-knowledge gap evidence

The first A7-4 Codex execution correctly stopped with
`A7_0_KNOWLEDGE_GAP_MANIFEST_UNVERIFIED` because its environment could not download the pinned
HashiCorp artifacts/source through the proxy and the exact 12-resource set had not been committed.

The gap set was then independently reproduced from the same pinned public provider source:

- provider source commit:
  `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`
- `internal/service` Git tree:
  `08ac3f85622b439f826da9b5677414f0f0b86fda`
- `website/docs/r` Git tree:
  `fda313b7ec2d69be5077056340ba25d6dc763b0d`
- generated service package files inspected: **256**
- managed resource `TypeName` values extracted only from
  `FrameworkResources()` and `SDKResources()`: **1,526**
- same-commit official `website/docs/r/*.html.markdown` intersection: **1,514**
- exact managed-resource difference: **12**

The exact sorted gap set is:

~~~text
aws_account_region
aws_alb
aws_alb_listener
aws_alb_listener_certificate
aws_alb_listener_rule
aws_alb_target_group
aws_alb_target_group_attachment
aws_api_gateway_rest_api_put
aws_ec2_image_block_public_access
aws_pinpoint_email_template
aws_rds_custom_db_engine_version
aws_securityhub_configuration_policy_association
~~~

The extraction intentionally excluded data-source `TypeName` entries. A preliminary unscoped
`TypeName` scan was rejected because it mixed data sources with managed resources; the accepted
reproduction parses only the two generated managed-resource registry functions.

The reproduced cardinalities exactly match the accepted A7-0 measurements
(`1,526 / 1,514 / 12`). This resolves the A7-4 preflight evidence blocker without deriving
knowledge availability from current retrieval hits and without running a live provider, embedding,
OpenSearch, or GCP operation.

A7-4 may use this exact set to build the compact read-only runtime coverage manifest described by
the Work Package. The runtime manifest must retain the pinned commit/count provenance above.


## Next gate

A7-5 does not start automatically. It requires separate user approval after A7-4 implementation,
independent acceptance, merge, and closure.


## A7-0 official-knowledge gap recovery evidence

The A7-4 preflight originally stopped because the exact 12-resource A7-0 official-documentation gap
set had not been committed and the Codex execution environment could not download the pinned
provider artifacts/source.

The gap set was subsequently reproduced directly from the pinned public provider source commit:

- provider source commit: `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`
- `internal/service` tree: `08ac3f85622b439f826da9b5677414f0f0b86fda`
- official `website/docs/r` tree: `fda313b7ec2d69be5077056340ba25d6dc763b0d`
- generated service-package registries inspected: 256
- managed resource registrations were taken only from generated
  `FrameworkResources()` and `SDKResources()` `TypeName` entries; data-source registrations were
  excluded
- reconstructed managed-resource registrations: 1,526
- exact-name managed resources with official `.html.markdown` resource documentation: 1,514
- exact-name official-documentation gaps: 12

The sorted verified gap set is:

1. `aws_account_region`
2. `aws_alb`
3. `aws_alb_listener`
4. `aws_alb_listener_certificate`
5. `aws_alb_listener_rule`
6. `aws_alb_target_group`
7. `aws_alb_target_group_attachment`
8. `aws_api_gateway_rest_api_put`
9. `aws_ec2_image_block_public_access`
10. `aws_pinpoint_email_template`
11. `aws_rds_custom_db_engine_version`
12. `aws_securityhub_configuration_policy_association`

The reconstructed counts exactly match the accepted A7-0 measurement
(`1,526 / 1,514 / 12`). This recovery did not use the current retrieval hits, the v3 30-resource
set, the current provider default branch, or fabricated resource names.

A7-4 may therefore proceed with a compact versioned read-only coverage manifest carrying these 12
gaps and the pinned provenance. This evidence does not claim that v4 is currently served or ingested.

## Accepted implementation evidence

**Status: A7-4 COMPLETE — independent acceptance PASS.**

The implementation adds `V20261004_007__add_analysis_job_quality_snapshot.sql`, with nullable bounded
columns `quality_contract_version`, `technical_status`, `knowledge_status`, `quality_status`,
`project_decision_status`, `runtime_quality_boundary`, and `quality_reasons`. Existing rows are not
backfilled. Success writes the snapshot inside the existing owned success transaction; terminal
failure uses the existing claim-generation and live-lease fenced update. Retry scheduling does not
write terminal quality.

The runtime coverage catalog reads
`quality/aws-provider-5.100.0-official-knowledge-coverage.json`: AWS Provider `5.100.0`, source commit
`f7a3b98da589ab1d52756b0dcee0dbf2de83d635`, generated registry tree
`08ac3f85622b439f826da9b5677414f0f0b86fda`, documentation tree
`fda313b7ec2d69be5077056340ba25d6dc763b0d`, 256 generated registry files, and exact counts
`1,526 / 1,514 / 12`. It interprets schema-known resources outside the verified 12 gaps as having
official knowledge; retrieval hits do not define that universe.

Vertex and Bedrock attach the existing `EvidenceQualityAssessment` to successful architecture
results while facts, selected references, generated Terraform, provider schema, and coverage
metadata remain available. Bedrock carries one retrieval outcome and does not repeat fact
extraction. `AnalysisResult.withTerraformCode` retains the assessment. Project-decision
applicability remains `UNKNOWN`.

`GET /api/analysis/jobs/{id}` now returns nullable `quality` with only `contractVersion`,
`technicalStatus`, `knowledgeStatus`, `qualityStatus`, `projectDecisionStatus`,
`runtimeQualityBoundary`, and bounded `reasons`. Micrometer uses
`terraformers.analysis.quality.terminal` with label keys `contract`, `technical`, `knowledge`,
`quality`, `project_decision`, and `runtime_boundary`; reason counts use
`terraformers.analysis.quality.reasons` with only `reason`. The terminal log contains bounded
status/version/reason names and relies on existing `analysisJobId` MDC correlation.

Deterministic tests cover manifest provenance/gaps, provider attachment, one-pass Bedrock fact
extraction, result-copy preservation, success/failure/input-rejection mappings, degraded and unknown
snapshots, retry and stale-generation fencing, legacy-null API behavior, fresh persistence-context
readback, and low-cardinality metric labels. The isolated implementation environment could not
resolve the Spring Boot parent from Maven Central, so final authority came from repository CI.
Backend Local Verification `37210008792` passed Maven clean tests/package plus MariaDB
Flyway/Hibernate and canonical repository smoke validation. Terraform Static Verification
`37210008786` also passed.

No live provider, embedding, OpenSearch, corpus ingestion, GCP, deployment, or cost-bearing action
was performed. A7-5 was not started. Residual limitations are the intentional
`CONDITIONAL_ON_EXTRACTED_FACTS` boundary, `UNKNOWN` project-decision applicability for arbitrary
requests, and null quality for legacy rows.
