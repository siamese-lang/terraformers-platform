# Case A A7-0 — Authoritative Knowledge Coverage

## Status

**COMPLETE — A7-1 NOT STARTED — LIVE/COST ACTION NOT REQUIRED**

Implementation execution base:

`dd34d1cec34d31783ca2d672c65311ce82fd2666`

PR #198 merged as:

`e91a789df241efc509b8854ebb2e364dce79a013`

The historical `terraformers-reference-v3` corpus remains unchanged. A7-0 establishes the
authoritative provider-knowledge universe, removes the per-resource compiler allowlist, measures
the broad v4 candidate, and selects the ingestion scope. It does not activate v4 in the serving
runtime and does not start A7-1.

## Problem

The historical v3 corpus contains 128 documents but represents only 30 AWS provider resource
types. That was sufficient for the original Case A fixtures but cannot distinguish, for arbitrary
valid resources, among:

- provider resource not supported;
- provider supports the resource but official usage knowledge is unavailable;
- authoritative knowledge exists but was not indexed;
- authoritative indexed knowledge exists but retrieval missed it.

The historical v2 compiler also required a source-code `RESOURCE_SPECS` entry for every supported
resource.

## Immutable v3 baseline

The retained v3 baseline is:

- document count: 128
- provider resource types represented: 30
- Terraformers project decisions: 8
- AWS Provider version: 5.100.0
- provider source commit: `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`

PR #198 CI run `37182581821` passed the RAG tooling regression:

- v1 corpus contract: PASS
- v2 corpus contract: PASS
- v3 corpus contract: PASS
- RAG tests: 17 / 17 PASS
- additional static tests: 40 / 40 PASS

The v3 contract digest remained:

`df8c198f0648827d754e1ef92ff4c07b1397e7dd36a06487eebfee1443ce892f`

## Implemented A7-0 compiler and coverage contract

PR #198 added the versioned v4 compiler and coverage reporter with these properties:

- arbitrary `aws_*` managed resources are validated from the supplied provider schema;
- official documentation paths are derived deterministically rather than from a production
  per-resource source-code dictionary;
- a resource absent from v3, including `aws_lambda_function` in the regression fixture, is built
  without changing compiler source;
- provider schema, official source-document presence, and actually indexed official evidence are
  separate coverage dimensions;
- source-file presence does not count as indexed official evidence when the parser emits no
  `AWS_PROVIDER_DOC` or `AWS_PROVIDER_EXAMPLE` chunk;
- source-missing and evidence-extraction-gap resources are separate machine-readable classes;
- provider version, source commit, source path, schema SHA-256, document authority/type, corpus
  identity, and project-decision provenance are retained;
- Terraformers project decisions remain a separate curated authority;
- v3 remains immutable.

The regression specifically prevents the false-green case:

~~~text
official source file exists
  + parser emits no official evidence
  + schema evidence exists
  != official RAG coverage
~~~

## Exact AWS Provider 5.100.0 measurement

The exact schema was generated through the existing `backend/Dockerfile` path, which pins and
checksum-verifies Terraform 1.8.5 and AWS Provider 5.100.0 before running:

`terraform providers schema -json`

The provider source used for documentation comparison was pinned to:

`f7a3b98da589ab1d52756b0dcee0dbf2de83d635`

Measured universe:

| Measurement | Result |
| --- | ---: |
| Provider schema resources | 1,526 |
| Schema resources with official resource documentation | 1,514 |
| Schema resources without official resource documentation | 12 |
| Historical v3 provider resources | 30 |
| v3 coverage of provider schema | 1.9659% |
| v3 coverage of officially documented schema resources | 1.9815% |

This confirms that the historical 1,514 provider-document-file count is not the provider schema
universe. The actual AWS Provider 5.100.0 managed-resource universe is 1,526.

## Broad v4 candidate measurement

The broad candidate was built from the complete schema/official-document intersection rather than
from the historical allowlist.

| Measurement | Result |
| --- | ---: |
| Candidate provider resources | 1,514 |
| Coverage of provider schema | 99.2136% |
| Coverage of officially documented schema resources | 100% |
| Resources with indexed official evidence | 1,514 |
| Official-evidence extraction gaps | 0 |
| Total candidate documents | 5,395 |
| Provider document chunks | 5,387 |
| JSONL bytes | 14,833,335 |
| Content characters | 10,618,014 |
| Approximate embedding input at 4 chars/token | 2,654,504 tokens |

No Vertex embedding request, OpenSearch ingestion, GCP runtime creation, or other live/cost-bearing
action was performed to obtain this measurement.

## Selected ingestion scope

A7-0 selects **broad/full authoritative coverage**, with the word "full" split by authority type:

1. **Structural authority:** all **1,526** AWS Provider 5.100.0 schema resources remain available as
   deterministic direct-lookup authority.
2. **RAG evidence scope:** ingest the complete **1,514-resource** intersection for which the pinned
   provider schema and pinned official resource documentation both exist.
3. **Official-knowledge gaps:** the remaining **12** schema resources are explicitly represented as
   resources whose provider structure exists but whose official RAG knowledge is unavailable.
   Terraformers does not fabricate substitute official evidence for them.

A bounded 30-resource-style subset is rejected. The measured broad candidate is only 5,395
documents / 14.8 MB JSONL / approximately 2.65M input tokens, so retaining the old allowlist for
size reasons would reintroduce an artificial knowledge boundary. Exact live embedding billing is
not claimed because ingestion was intentionally not executed; any future cost-bearing ingestion
remains separately gated.

## A7-0 acceptance

| Acceptance criterion | Result |
| --- | --- |
| Current v3 provider-resource coverage machine reported | PASS |
| Real provider-schema resource universe machine reported | PASS — 1,526 |
| v4 builder removes per-resource source-code allowlist requirement | PASS |
| Resource absent from v3 builds without builder source change | PASS |
| Schema / official source / indexed official evidence reported separately | PASS |
| Provider version / commit / path / checksum provenance preserved | PASS |
| Project decisions remain separate curated authority | PASS |
| Historical v3 unchanged | PASS |
| Bounded vs broad/full scope selected from measured volume | PASS — broad/full selected |

**A7-0 closure: PASS.**

## Non-claims

A7-0 does not claim:

- v4 is currently served by OpenSearch;
- all 1,526 schema resources have official RAG evidence;
- the 12 schema-only resources have fabricated replacement knowledge;
- broad v4 has been embedded or deployed;
- corpus expansion alone proves generated-output quality;
- retrieval will always select the available authoritative evidence;
- an evaluator LLM is required or selected;
- a live GCP runtime currently exists.

Those boundaries belong to later phases.

## Next gate

A7-1 — Evidence-backed Quality Contract — is the next candidate phase.

It must not start automatically. A separate explicit user decision is required before implementation.

No GCP resource recreation, Vertex call, corpus ingestion, deployment, or other cost-bearing action
is authorized by this A7-0 closure.
