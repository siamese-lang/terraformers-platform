# M3-R1 Target Runtime Evidence

## Status

**Decision complete / live pre-apply refresh required**

Decision: [ADR-005 — Single GCP/open-source AI/RAG target runtime](../architecture/decisions/ADR-005-target-ai-rag-runtime.md)

This document separates architecture selection from values that can change between observation and
resource creation.

## Existing contract requirements

| Existing contract | Required target capability | Decision |
| --- | --- | --- |
| `AnalysisGenerationStage` | image input, JSON/structured text output, provider-neutral result | Vertex AI `gemini-3.8-flash` |
| `EmbeddingProvider` | one query text → float vector | Vertex AI `gemini-embedding-001`, 1024 dimensions |
| `OpenSearchTransport` + `OpenSearchReferenceRetriever` | HTTP search transport, OpenSearch k-NN, filters, ordered hit provenance | OpenSearch OSS inside target GKE Standard cluster |
| versioned corpus/index | immutable corpus identity, 1024-d vector field, document metadata | derive `terraformers-reference-v3` from v2 and re-embed |
| Kubernetes base/workload | Kubernetes-compatible serving runtime | one zonal GKE Standard cluster |
| cloud credential boundary | short-lived runtime identity | Workload Identity Federation for GKE |

## Repository facts that constrain the decision

- `terraformers-reference-v2` contains 128 documents.
- v2 declares `amazon.titan-embed-text-v2:0` and vector dimension 1024.
- v2 OpenSearch schema uses `knn_vector`, FAISS/HNSW and cosine similarity.
- Runtime retrieval already derives query embeddings through `EmbeddingProvider`, builds the
  OpenSearch request independently, and sends it through `OpenSearchTransport`.
- The remaining ingestion script is AWS-bound through boto3, AWS4Auth, S3 receipt storage, AOSS and
  Bedrock embeddings; M3-R3 replaces only that target-specific ingestion binding.
- The generated/retrieved Terraform knowledge remains AWS provider 5.100.0 content. Moving the
  Terraformers application to GCP does not rewrite the generated Terraform target.

## Last operator-observed GCP constraints

These observations came from prior work on the same Free Trial GCP account and are planning
constraints, not a fresh M3-R2 quota snapshot:

| Observation date | Constraint | Planning interpretation |
| --- | --- | --- |
| 2026-09-16 | Free Trial billing active; previous live resources later torn down | Paid resources can consume trial credit, but this is not an Always Free design |
| 2026-09-16 | all-regions CPU quota observed at 12 vCPU | initial 4-vCPU target shape fits the last observation with headroom |
| 2026-09-12~16 | Seoul persistent-disk/SSD constraint observed around 250 GiB | keep OpenSearch disk materially below this until refreshed |
| 2026-09-16 | quota increase unavailable on the trial account | choose a shape that fits existing quota rather than depending on an increase |
| prior runtime | `asia-northeast3` used successfully; one prior zonal capacity failure was also observed | prefer Seoul but verify the exact zone immediately before apply |

The target project ID and current quota usage are intentionally not committed here. M3-R2 begins
with a fresh non-destructive observation and records the project/region context in the PR evidence.

## Public capability evidence checked on 2026-09-24

- GKE Standard supports node-level Linux sysctls including `vm.max_map_count`.
- GKE Autopilot rejects unsafe sysctls and does not offer the same node-level control.
- OpenSearch requires `vm.max_map_count=262144`.
- GKE StatefulSets can use persistent disks.
- GKE Workload Identity Federation supports Google API access without service-account key files.
- Gemini 3.8 Flash is GA, accepts image input and supports structured output.
- `gemini-embedding-001` supports configurable output dimensionality up to 3072 and retrieval task
  types; 1024 can therefore be selected explicitly.
- One zonal Standard cluster is eligible for the GKE monthly management-fee free-tier credit; node
  compute and persistent storage remain billable.

## Cost/operational boundary

Initial M3-R2 planning shape:

- one zonal GKE Standard cluster;
- one 4-vCPU / 16-GiB E2 node as the starting variable value;
- one single-node OpenSearch StatefulSet with persistent storage;
- Vertex AI generation/embedding usage on demand;
- no public OpenSearch endpoint;
- no second evaluation cluster;
- no GPU node and no self-hosted LLM.

The node may later be resized or the same cluster extended when backend/observability components are
added. That is expansion of one environment, not a second environment.

## Explicitly deferred

- GKE regional/HA topology;
- OpenSearch multi-node HA;
- Vertex AI Vector Search;
- third-party managed Elasticsearch/OpenSearch;
- self-hosted multimodal or embedding models;
- LangChain/LangGraph;
- exact final disk size/class and OpenSearch image version;
- exact final node count;
- final frontend/database/object-storage/IdP products outside the M3 AI/RAG foundation.

## M3-R2 entry conditions

M3-R2 may implement the selected target, but its first live action must refresh the mutable account
facts listed in ADR-005. If the fresh values do not support the one-cluster shape, stop before apply
and revise the decision; do not create a parallel fallback environment.
