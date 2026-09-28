# M3-R3b — GCP target corpus ingestion adapter

**Status: STATIC IMPLEMENTATION COMPLETE — LIVE EXECUTION PENDING**

## Execution authorization

The static implementation from this task is complete. M3-R2 OpenSearch live readiness is also
complete. The remaining work is the separately protected live execution on the same target runtime.

Do not wait for, inspect, approve, or perform protected GCP deployment work. Do not require GitHub
CLI authentication, live GCP credentials, billing access, a running GKE cluster, or a reachable
OpenSearch endpoint in order to complete the static implementation and tests.

If live credentials would be required for a validation step, stop only that live step and report it
as deferred. Complete all code, tests, and documentation that can be proven offline.

## Read first

- `AGENTS.md`
- `docs/AI_PROJECT_STATE.md`
- `docs/plans/MASTER_PLAN.md`
- `docs/plans/active/M3-ai-evaluation-baseline.md`
- `docs/architecture/decisions/ADR-005-target-ai-rag-runtime.md`
- `docs/evaluation/m3-r1-target-runtime-evidence.md`
- `docs/reference-retrieval.md`
- `scripts/rag/ingest-corpus.py`
- `corpus/terraformers-reference/v3/`
- `backend/src/main/resources/application-gcp-target.yml`
- `backend/src/main/java/com/terraformers/modernization/reference/VertexEmbeddingProvider.java`
- `backend/src/main/java/com/terraformers/modernization/reference/opensearch/HttpOpenSearchTransport.java`

## Problem

The repository now has the immutable `terraformers-reference-v3` static corpus contract, but the
only committed live ingestion utility is AWS-specific: it assumes Bedrock embeddings, SigV4/AOSS,
S3 receipts, and a CodeBuild execution environment.

The selected target runtime instead requires:

- Vertex `gemini-embedding-001`;
- **document** embeddings using the Vertex retrieval-document task semantics;
- 1024 dimensions;
- the committed `terraformers-reference-v3` OpenSearch mapping;
- plain internal HTTP to the in-cluster OpenSearch service; and
- stable document/provenance metadata identical to the committed v3 corpus.

## Goal

Add the smallest one-shot GCP-target ingestion adapter/tooling needed to ingest
`terraformers-reference-v3` into the existing OpenSearch OSS target **without** creating a new
service, agent framework, second runtime, or alternative retrieval implementation.

Preserve the historical AWS ingestion path.

## Required behavior

The GCP target ingestion path must:

1. validate the committed corpus with the existing
   `scripts/checks/rag-corpus-contract-verification.py`;
2. read the corpus/index/vector/content identity from `corpus-manifest.json` rather than duplicating
   those values in unrelated code;
3. require `corpusVersion=terraformers-reference-v3`,
   `indexName=terraformers-reference-v3`, `embeddingModelId=gemini-embedding-001`,
   vector dimension 1024, vector field `embedding`, and content field `content`;
4. create the OpenSearch index from the committed `index-schema.json` when absent;
5. fail closed if an existing index has an incompatible vector/content mapping;
6. generate **document embeddings** with the selected Vertex embedding model using
   retrieval-document semantics, not the backend's query-embedding semantics;
7. require exactly one finite 1024-dimensional vector per document;
8. index all 128 documents under their existing stable `documentId` identity while preserving all
   committed document metadata/provenance;
9. be idempotent for the same corpus version/checksum and stable document IDs;
10. fail closed if the same corpus version is presented with a different full corpus checksum;
11. verify the final corpus-version document count is exactly 128;
12. run at least one representative 1024-dimensional k-NN query after ingestion and require a hit;
13. emit a machine-readable ingestion receipt/result containing at least corpus version, full corpus
    checksum, document count, index name, embedding model identity, vector dimension, representative
    hit IDs, outcome, and elapsed time;
14. not require S3, AOSS, AWS credentials, or an AWS region for the GCP path.

## Design constraints

- Reuse/refactor existing corpus contract, mapping, idempotency, and verification logic where that
  reduces duplication, but do not break or silently change the historical AWS ingestion path.
- Keep cloud-specific authentication/model invocation at an adapter boundary.
- Do not change `VertexEmbeddingProvider.embed(String)` to use document semantics: that class is
  the Spring retrieval-query path and must remain `RETRIEVAL_QUERY`.
- If shared embedding validation can be extracted cleanly without widening the production API,
  that is acceptable; otherwise keep the one-shot ingestion concern outside the serving path.
- Do not add a persistent Python AI service.
- Do not introduce LangChain, LangGraph, agents, rerankers, or another vector store.
- Do not change the curated v3 document content to make ingestion easier.
- Do not deploy the backend or alter Terraform/IAM in this task.
- Do not add another broad verification workflow when existing CI can cover the static code.

## Runtime handoff contract

The implementation should be runnable later with short-lived ADC/Workload Identity credentials and
an internal OpenSearch endpoint. Do not embed service-account keys or live endpoints.

The live M3-R3 execution step will supply the final target values from the deployed runtime and will
prove Vertex document embeddings + OpenSearch ingestion. This task only needs to make that execution
deterministic and testable.

## Validation

Add focused offline tests proving at minimum:

- v3 contract values are read/validated correctly;
- the document-embedding request uses retrieval-document semantics and requests 1024 dimensions;
- malformed/non-finite/wrong-dimension embedding responses fail;
- index creation uses the committed schema;
- incompatible existing mapping fails;
- stable document IDs and metadata are retained;
- same-version checksum mismatch fails;
- rerun/idempotency behavior does not duplicate documents;
- final count >128 or <128 fails;
- representative k-NN readiness verification is required;
- historical AWS ingestion tests continue to pass.

Use existing repository tests/checks and run only the relevant regressions required by
`AGENTS.md`.

## Non-goals

Do not:

- perform live Vertex calls;
- connect to the live OpenSearch cluster;
- create Kubernetes resources;
- change the protected GCP deployment workflows;
- tune retrieval ranking or prompts;
- run the six-case M3 baseline;
- adopt a new framework or database.

## Completion report

Report:

- base SHA;
- branch/head SHA;
- changed files;
- the exact GCP ingestion execution contract;
- how Vertex document embeddings differ from the existing query embedding path;
- validation commands and results;
- whether the historical AWS ingestion path remained unchanged/compatible;
- unresolved live-only handoff items;
- immediate next single task.

If GitHub authentication is unavailable, complete the implementation/tests locally and report the
commit/diff. Missing PR capability is not an implementation blocker.
