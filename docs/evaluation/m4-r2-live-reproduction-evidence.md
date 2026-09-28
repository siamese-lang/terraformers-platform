# M4-2 Live Root-Cause Reproduction Evidence

## Status

**COMPLETE**

M4-2 used the single existing GCP target runtime to classify the two canonical M3
`FACT_EXTRACTION / PROVIDER_RUNTIME` failures without changing the dataset, models, prompt,
corpus, retrieval ranking, top-K, validator, or runtime topology.

## Fixed identity

- repository commit: `5a75ed1f34bce8c67ec34644d2cbf7550848c86e`
- canonical M3 baseline workflow run: `36379633596`
- dataset: `terraformers-eval-v1`
- configuration fingerprint:
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`
- generation model: `gemini-3.8-flash`
- embedding model: `gemini-embedding-001`
- corpus/index: `terraformers-reference-v3`
- provider knowledge version: `5.100.0`
- vector dimension: `1024`
- OpenSearch top-K: `8`
- runtime: existing GKE target + Vertex AI + OpenSearch OSS
- runtime activation evidence: workflow run `36384234371`

## Bounded reproduction results

| Case | Workflow run | Fact extraction | First divergence | Downstream result |
| --- | --- | --- | --- | --- |
| `arch-vpc-three-tier` | `36385950712` | **FAIL**, 9556 ms | `FACT_EXTRACTION / OUTPUT_TRUNCATED` | retrieval/generation/validation `NOT_RUN` |
| `arch-private-aoss` | `36386233526` | **PASS**, 9392 ms | none | retrieval **PASS** 3787 ms; generation **PASS** 17204 ms; validation **PASS** 3 ms |

### `arch-vpc-three-tier`

The new M4-1 diagnostics preserved:

`reason=RESPONSE_TRUNCATED`

The failure therefore reproduced as output truncation rather than a generic provider runtime
exception. The trace did not reach retrieval, generation, or validation.

Artifact:

- name: `m4-r2-reproduction-36385950712`
- artifact digest:
  `sha256:ba3d46db5e08fcd8a338e1b55cb28f852ae3a2014190f221b344d38dd3ac37ed`

### `arch-private-aoss`

The original M3 fact-extraction failure did **not** reproduce in this bounded attempt. Fact
extraction completed, retrieval reached the expected project-decision AOSS references, generation
completed with `retryOccurred=false`, Terraform validation passed, and `firstDivergence` was
null.

Artifact:

- name: `m4-r2-reproduction-36386233526`
- artifact digest:
  `sha256:c5e9aa301541a281ce438220951f007f4bb21a61063e902f90ce994503d005bf`

## Decision gate

M4-2 provides no evidence for a generic retry/backoff change:

- no 429/throttling result was captured;
- no 5xx/capacity result was captured;
- the reproduced VPC failure is explicitly `RESPONSE_TRUNCATED`;
- the AOSS failure was not reproduced under the same configuration.

M4-3 must therefore be truncation-targeted. A generic transient-provider retry must remain
deferred unless later evidence independently demonstrates a retryable provider status.

The next code change must inspect the existing Vertex fact-extraction response/token contract and
implement the smallest change that directly addresses the reproduced truncation while preserving the
fixed prompt/model/dataset/corpus/retrieval/validator identity wherever possible.

## Runtime lifecycle

The bounded reproduction session used the approved one-node target runtime. Protected idle run
`36386950442` on commit `c8ae238cfcad31c03f5e5b3bec1b59f5c9b513be` passed the plan gate
with `operation=idle`, exactly one managed-resource change, and plan JSON SHA-256
`2d36638ec05b13cdc7c3f9b4b99d12b2f4b945df4658864c8ef8460d1599afcd`. It updated only
`google_container_node_pool.target`, applied `0 added, 1 changed, 0 destroyed`, and the final
runtime-boundary check passed with the idle `node_count=0` contract.
