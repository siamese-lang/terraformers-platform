# Case A Retrieval-Grounding Measurement Readiness

## Status and selected problem

**A1 measurement readiness = COMPLETE.** Case A remains **OPEN**. The frozen primary problem is retrieval grounding / required-evidence coverage. A1 changes evaluation measurement only; production retrieval behavior is **UNCHANGED**.

## Scope and retained evidence

A1 makes current and later approved retrieval behavior deterministically comparable without selecting or implementing an alternative. The retained `arch-vpc-three-tier` evidence is top-K `8`, empty resource filters, project-decision coverage `0/1`, resource-type coverage `2/4`, generated required resources `4/4`, and Terraform validation `PASS`. This means successful invocation and structurally valid generation can coexist with incomplete required grounding; it is not proof of hallucination or incorrect Terraform.

## Scorer and report contract

`RetrievalGroundingScorer` compares an `EvaluationCase` and `EvaluationTrace` without mutation. Project decisions match exact `documentId`; resource types match exact hit metadata membership. It reports applicability, matched/total/coverage, first matching ranks and missing requirements while retaining the query, filters, requested top-K, and ordered complete hit provenance. It also records generation/validation status, required and forbidden generated resources, first divergence, stage latency, and the observed sum.

`GROUNDING_GAP` means incomplete required evidence plus successful generation. `GROUNDING_GAP_WITH_VALID_OUTPUT` additionally requires a passing application validator. Failed, blocked, and not-run retrieval retains its actual status and never implies successful grounding. Controls with no requirements have null coverage and explicit non-applicability rather than `0/0 = 100%`.

`CaseAMeasurementReporter` derives `case-a-retrieval-grounding-report-v1` from the raw run plus frozen dataset; the raw `EvaluationRunResult` remains source evidence. Counts cover extraction and retrieval outcomes, both gap flags, negative-control correctness, and applicable validation outcomes.

## Configuration provenance

New live identities explicitly record `factExtractionThinkingLevel=LOW`, `factExtractionMaxOutputTokens=800`, and `generationMaxOutputTokens=8192`. The first two come from the effective Vertex extractor constants. Fields are nullable so historical JSON remains readable. The legacy fingerprint input and expected M3/M4 fingerprint remain unchanged; Case A validates the explicit fields separately.

## Frozen holdout

`terraformers-eval-holdout-v1` retains the `m3-evaluation-v1` schema and four repository-owned synthetic WebP fixtures:

- `holdout-eks-irsa`: architecture; requires `tfref-v2-eks-irsa`, `aws_eks_cluster`, `aws_eks_node_group`, and `aws_iam_role`; generated resources match; validation `PASS`.
- `holdout-workload-rds-sg`: architecture; requires `tfref-v2-sg-relations`, `aws_security_group`, and `aws_db_instance`; generated resources match; validation `PASS`.
- `holdout-ambiguous-obscured-flow`: ambiguous, no Terraform/retrieval requirements, validation not applicable.
- `holdout-non-architecture-status-board`: non-architecture, no Terraform/retrieval requirements, validation not applicable.

Tests pin WebP signatures and SHA-256 through the shared loader, composition, corpus decision IDs, and non-overlap of canonical IDs and hashes. Neither canonical data nor corpus was modified.

## Multi-run aggregation

`CaseAMultiRunAggregator` accepts reports only when dataset and the frozen comparable identity agree, including the three new token/thinking fields. It reports sample/failure count and frequency, min/median/max stage latencies, and per-run matched/total decision and resource coverage by case. It intentionally emits no p95/p99 and makes no statistical reliability claim from three samples.

## Executable path and retrieval probe decision

The Spring-free `CaseAMeasurementLauncher` converts raw result plus dataset to a per-run report and separately aggregates comma-separated reports. Because `EvaluationRunner` always begins with image extraction, `FixedFactsRetrievalProbe` adds the smallest evaluation-only seam from fixed `ArchitectureRetrievalFacts` through the existing query builder and retriever to ordered evidence. It introduces no normalization, top-K candidate, reranking, or production request-flow change.

## Workflow boundary

The existing protected workflow now offers `case-a-full-baseline` with confirmation `RUN_CASE_A_BASELINE`. One dispatch performs one unchanged canonical six-case run and deterministic scoring, validates the explicit provenance, and preserves Case A-specific raw/report artifacts. It does not loop N=3. A1 did not dispatch it, activate GKE, call Vertex, or query live OpenSearch.

## Deterministic validation and limits

Focused scorer, report, aggregation, launcher compatibility, and holdout tests were added. Static workflow/YAML and repository checks cover the executable contract. In this isolated environment Maven execution was blocked before compilation because Maven Central returned HTTP 403; this is an environment limitation, not live evidence.

A1 does **not** prove retrieval quality improved, establish failure frequency, select a retrieval alternative, validate the holdout live, or close Case A. It does not authorize production changes.

## Conclusion and immediate next task

```text
A1 measurement readiness = COMPLETE
production retrieval behavior = UNCHANGED
A2 repeated current baseline = NEXT, separate approval required
```

The immediate next single task after review and merge is **A2 — repeated current baseline, canonical N=3**. Do not begin it without separate user approval.
