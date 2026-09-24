# ADR-005: Select one reusable GCP/open-source AI/RAG target runtime

## Status

Accepted

## Context

M3-1 through M3-3 established a fixed evaluation dataset, stage-provenance contract, and reusable
runner. M3-4 could not execute because the historical AWS Bedrock + AOSS runtime had already been
torn down. The repository working contract now requires a single live target runtime rather than an
evaluation-only cloud stack followed by a second final environment.

The existing application boundaries constrain the decision:

- `AnalysisGenerationStage` / `AnalysisProvider` require multimodal generation and structured
  output without exposing provider SDK types to the application core.
- `EmbeddingProvider` returns a float vector for retrieval queries.
- `OpenSearchReferenceRetriever` already preserves the OpenSearch k-NN query, corpus/version
  filters, ordered hits, source metadata, authority, and risk tags behind
  `OpenSearchTransport`.
- `terraformers-reference-v2` contains 128 documents and a 1024-dimensional OpenSearch
  `knn_vector` contract. Its manifest identifies the historical Titan embedding model, so changing
  the embedding model requires a new corpus/index identity rather than silently mutating v2.
- The Kubernetes workload contract is already a reusable repository asset.

Prior operator-observed GCP constraints from 2026-09-16 are useful as a planning bound but are not a
fresh quota read for this task: Free Trial billing was active, the previous runtime had been torn
down, an all-regions CPU limit of 12 vCPU and approximately 250 GiB of Seoul SSD quota had been
observed, and quota increase was not available on the trial account. M3-R2 must refresh project,
billing and quota state before any resource creation and abort if those assumptions no longer hold.

## Decision

### 1. Runtime: GKE Standard, one zonal target cluster

Use **one zonal GKE Standard cluster** in `asia-northeast3` as the target runtime foundation.
`asia-northeast3-a` is the preferred initial zone, subject to the M3-R2 pre-apply capacity refresh.

The initial planning node shape is one `e2-standard-4` node (4 vCPU / 16 GiB), expressed as an IaC
variable rather than a hard-coded architecture limit. Later milestones may resize or add nodes in
the same cluster; they must not create a second production architecture.

Reasons:

- it reuses the existing Kubernetes workload contract;
- OpenSearch requires host `vm.max_map_count=262144`;
- GKE Standard supports node-level Linux sysctl configuration including `vm.max_map_count`;
- GKE Autopilot rejects unsafe pod sysctls and removes the node-level control needed by this
  OpenSearch deployment;
- a zonal Standard cluster is eligible for the GKE monthly cluster-management free-tier credit,
  while node compute remains billable.

A standalone Compute Engine VM remains a fallback only if the refreshed GKE/quota evidence blocks
the Standard cluster. It is not deployed in parallel.

### 2. Generation: Vertex AI Gemini 3.8 Flash

Use Vertex AI **`gemini-3.8-flash`** through the Google Gen AI Java SDK as the target
`AnalysisGenerationStage` implementation.

Reasons:

- it is GA;
- it accepts image input and returns text;
- structured output is supported, matching the current JSON response contract;
- it avoids a separately hosted GPU model service;
- it uses the same GCP workload identity boundary as the rest of the target runtime.

Use the `global` model endpoint initially. The GKE cluster remains in Seoul; model processing is
therefore not claimed to be Seoul-resident. If a later requirement demands Seoul-only model
processing, this part of the decision must be revisited.

### 3. Embeddings: Vertex AI gemini-embedding-001 at 1024 dimensions

Use Vertex AI **`gemini-embedding-001`** with `outputDimensionality=1024`.

Use:

- `RETRIEVAL_DOCUMENT` when embedding corpus documents;
- `RETRIEVAL_QUERY` for runtime query embeddings.

This retains the existing 1024-dimensional OpenSearch vector contract while changing the embedding
identity explicitly.

### 4. Retrieval: OpenSearch OSS in the same GKE Standard cluster

Run **OpenSearch OSS as a single-node StatefulSet** for the initial target runtime, backed by a GCE
persistent disk and exposed only through an internal Kubernetes Service.

This keeps the existing `OpenSearchReferenceRetriever`, query builder, response parser and
provenance semantics. M3-R2 adds only the target transport/authentication and deployment pieces
needed behind `OpenSearchTransport`.

The exact OpenSearch image version and disk class/size are pinned in M3-R2 after a compatibility
and fresh quota check. Single-node operation is a portfolio/dev target-runtime decision and is not
an HA or production-SLA claim.

Vertex AI Vector Search and third-party managed search services remain deferred because they would
replace an already reusable OpenSearch contract without a measured need.

### 5. Runtime identity: Workload Identity Federation for GKE

Enable Workload Identity Federation for GKE and grant the application Kubernetes ServiceAccount the
minimum permissions required for Vertex AI. Do not use committed or long-lived Google service
account keys.

OpenSearch stays cluster-internal and does not require Google API credentials for ordinary search
traffic.

### 6. Corpus/index identity: create terraformers-reference-v3

M3-R3 creates **`terraformers-reference-v3`** from the same curated v2 document content and stable
document IDs, but with the new embedding identity and index checksum.

The AWS Terraform provider version remains **5.100.0** because that field describes the Terraform
content being generated and retrieved, not the cloud on which Terraformers itself is deployed.

The v2 corpus remains immutable historical evidence.

### 7. No orchestration framework adoption

LangChain, LangGraph, agent/multi-agent architecture, a persistent Python AI service and additional
model calls remain deferred. M3-4 and M3-5 must first show an observable failure that such a change
would directly address.

## Candidate comparison

| Capability | Candidate | Decision | Reason |
| --- | --- | --- | --- |
| Runtime | GKE Standard zonal | **ACCEPT** | Reuses Kubernetes contract and supports required node sysctl |
| Runtime | GKE Autopilot | **REJECT for current OpenSearch shape** | Unsafe/node-level sysctl restrictions conflict with OpenSearch host requirement |
| Runtime | Single Compute Engine VM | **DEFER fallback** | Lower abstraction reuse; use only if fresh GKE/quota evidence blocks Standard |
| Generation | Vertex AI Gemini 3.8 Flash | **ACCEPT** | GA, multimodal, structured output, managed identity |
| Generation | Historical Bedrock | **REJECT as active target** | AWS compatibility reference, not current deployment target |
| Generation | Self-hosted OSS multimodal model | **DEFER** | Adds GPU/model-serving operations without measured need |
| Embedding | Vertex AI gemini-embedding-001 / 1024 | **ACCEPT** | Stable model, configurable dimension, retrieval task types |
| Embedding | Self-hosted embedding service | **DEFER** | Adds service/runtime complexity without evidence |
| Retrieval | OpenSearch OSS in target GKE cluster | **ACCEPT** | Maximum reuse of existing query/provenance contract |
| Retrieval | Vertex AI Vector Search | **DEFER** | Requires a new retriever/query contract before any measured need exists |
| Retrieval | Third-party managed Elastic/search service | **DEFER** | Additional vendor/cost boundary and uncertain exact query compatibility |

## Cost boundary

This project does not claim an Always Free target runtime.

Current public pricing used only as a planning reference:

- GKE charges a $0.10/cluster-hour management fee; the GKE free tier provides $74.40/month in
  credits applicable to one zonal Standard or Autopilot cluster. Compute, storage and network
  remain separately billable.
- The current public E2 table lists `e2-standard-4` at $0.13402284/hour on-demand in the selectable
  region table that includes Seoul.
- Gemini 3.8 Flash global Standard PayGo is currently listed at $0.75 per 1M input tokens and $3.75
  per 1M text output tokens through 2026-12-31.

These prices are not budget guarantees. M3-R2 rechecks current pricing and quota before apply and
keeps the deployment inside the same target cluster rather than funding a parallel environment.

## M3-R2 pre-apply gates

Before the first Terraform apply, record a fresh observation of:

1. active GCP project and billing state;
2. remaining Free Trial/credit constraints where available;
3. `CPUS_ALL_REGIONS` and `asia-northeast3` CPU/instance quotas;
4. persistent-disk quota in Seoul;
5. GKE API availability and zonal server configuration;
6. Vertex AI API/model access for `gemini-3.8-flash` and `gemini-embedding-001`;
7. current list-price estimate for the selected node and disk;
8. no conflicting live runtime already occupying the single-target namespace/resource boundary.

If any gate materially invalidates this decision, stop before resource creation and amend this ADR
rather than silently selecting a second environment.

## Consequences

- M3-R2 can implement one reusable GCP runtime instead of a throwaway evaluation stack.
- The target requires a new Vertex generation adapter, Vertex embedding adapter, OpenSearch
  transport/auth implementation and GKE/OpenSearch IaC.
- M3-R3 must re-embed the corpus and bump its version to v3.
- The project retains its AWS Terraform knowledge corpus even though the Terraformers application
  runtime moves to GCP.
- OpenSearch single-node operation trades HA for lower portfolio/runtime cost; later scaling must
  reuse the same cluster/IaC.
- Exact target project ID, final node count, disk type/size and OpenSearch image tag remain
  deployment variables, not a second architecture decision.

## Public evidence

- GKE pricing: https://cloud.google.com/kubernetes-engine/pricing
- GKE node system configuration: https://docs.cloud.google.com/kubernetes-engine/docs/how-to/node-system-config
- GKE Autopilot security restrictions: https://cloud.google.com/kubernetes-engine/docs/concepts/autopilot-security
- Workload Identity Federation for GKE: https://docs.cloud.google.com/kubernetes-engine/docs/how-to/workload-identity
- Compute E2 pricing: https://cloud.google.com/products/compute/pricing/general-purpose
- Gemini 3.8 Flash: https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/gemini/3-8-flash
- Structured output: https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/capabilities/control-generated-output
- Gemini embeddings: https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/embeddings/get-text-embeddings
- OpenSearch Docker/system requirements: https://docs.opensearch.org/latest/install-and-configure/install-opensearch/docker/

## Repository evidence

- `AGENTS.md`
- `docs/architecture/target-architecture.md`
- `docs/architecture/deployment-gcp.md`
- `docs/plans/active/M3-ai-evaluation-baseline.md`
- `backend/src/main/java/com/terraformers/modernization/analysis/AnalysisGenerationStage.java`
- `backend/src/main/java/com/terraformers/modernization/reference/EmbeddingProvider.java`
- `backend/src/main/java/com/terraformers/modernization/reference/opensearch/OpenSearchTransport.java`
- `backend/src/main/java/com/terraformers/modernization/reference/opensearch/OpenSearchReferenceRetriever.java`
- `corpus/terraformers-reference/v2/corpus-manifest.json`
- `corpus/terraformers-reference/v2/index-schema.json`
