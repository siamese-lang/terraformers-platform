# M3 Live Baseline Blocker

## Status

**BLOCKED — no live quality run executed**

Recorded against main SHA: `3e706f97c94a051432de9ce576a820d8672a5b4f`

Dataset: `terraformers-eval-v1` (6 cases)

Machine-readable status:
[`evaluation/baselines/m3-live-baseline-status.json`](../../evaluation/baselines/m3-live-baseline-status.json)

## Intended measured path

The last committed real AWS compatibility runtime is defined by
`infra/kubernetes/overlays/aws-runtime-template/backend-configmap-patch.yaml`:

- profiles: `prod,aws-compat`;
- analysis provider: `bedrock`;
- embedding provider: `bedrock`;
- retrieval mode: `REQUIRED`;
- generation model: `global.anthropic.claude-sonnet-4-6`;
- embedding model: `amazon.titan-embed-text-v2:0`;
- corpus: `terraformers-reference-v2`;
- provider schema version: `5.100.0`;
- index: `terraformers-reference-v1`;
- vector/content fields: `embedding` / `content`;
- vector dimension: 1024;
- top-K: 8;
- signing service: `aoss`;
- region: `ap-northeast-2`.

This is a historical AWS compatibility path, not a reversal of the modernization deployment target
or a new AWS architecture decision.

## Why the baseline cannot run now

The repository's canonical lifecycle evidence records a complete project-scoped AWS teardown.

`docs/lifecycle/aws-final-zero-resource-proof.md` records zero project OpenSearch Serverless
collections and zero Terraformers runtime resources. It also records deletion of the Terraform state
bucket, project GitHub OIDC provider, and live roles. The same document states that retained GitHub
environment/variable/secret configuration contains identifiers that must be refreshed before
redeployment.

Therefore the M3 runner has no live AOSS endpoint/index/corpus or usable project live identity to
call. This is not a retrieval-quality failure and is not counted as a failed evaluation case.

## Case disposition

| Case | Result |
| --- | --- |
| `arch-vpc-three-tier` | BLOCKED |
| `arch-cloudfront-private-alb` | BLOCKED |
| `arch-private-aoss` | BLOCKED |
| `arch-s3-metadata-split` | BLOCKED |
| `ambiguous-cropped-service-sketch` | BLOCKED |
| `non-architecture-deployment-dashboard` | BLOCKED |

Executed: **0**

Failed: **0**

Blocked: **6**

## Why no substitute was used

The M3 objective is to measure the current implemented RAG path under fixed dataset/configuration.
Running the stub provider, replacing AOSS with an in-memory/local retriever, changing the model, or
using a different vector store would produce a different baseline and would hide the missing live
dependency.

No such substitution was performed and no quality score was fabricated.

## Unblock boundary

A faithful live run requires all of the following:

1. a valid provider identity with access to the selected generation and embedding models;
2. a live retrieval service compatible with the current `ReferenceRetriever` contract;
3. `terraformers-reference-v2` indexed with the expected mapping and 1024-dimensional embeddings;
4. endpoint/access/network configuration that allows the current signed retrieval adapter to query
   that index; and
5. explicit approval for any paid resource creation or redeployment.

The repository's existing AWS redeployment runbook is intentionally broader than M3 and must not be
executed automatically just to make this baseline green. If a smaller faithful evaluation substrate
is proposed, it still requires cost approval and must not change the measured AI/RAG behavior.

## Consequence for M3

M3-4 remains **BLOCKED** rather than DONE. M3-5 and M4 must not use this no-run status as quality
evidence. Once an approved live substrate exists, execute the existing `EvaluationRunner` against
the unchanged `terraformers-eval-v1` dataset and preserve each live run identity.
