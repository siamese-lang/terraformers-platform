# Case A Retrieval-Grounding Measurement and Decision Specification

## Status

**READY — PRIMARY PROBLEM SELECTED; MEASUREMENT READINESS NOT YET IMPLEMENTED**

This documentation checkpoint advances the **AI/RAG evaluation and targeted improvement** axis and
deepens Case A. It authorizes no production AI change, live experiment, holdout creation, or next
stage. Case B remains portfolio-closed; Case A remains open.

## Decision checkpoint

The user-selected Case A primary problem is **retrieval grounding / required-evidence coverage**.
The M4 fact-extraction `RESPONSE_TRUNCATED` remediation is retained as a completed precursor rather
than re-selected. The AOSS `130489 ms` fact-extraction observation remains a secondary measurement
item because it is one extreme observation, while the same case passed at `9392 ms` in bounded M4
reproduction. A latency-focused change requires a new evidence gate if repeated evidence establishes
a material pattern.

This specification freezes the problem, evidence, measurement contract, alternatives, and decision
boundaries. It does not select a retrieval alternative. Each later stage requires separate user
approval.

## Selected problem

> A required-grounding retrieval miss can be hidden by downstream generation success and structural
> Terraform validation success.

The representative case is `arch-vpc-three-tier`. Its immutable `terraformers-eval-v1` expectations
are:

- required retrieval project decision: `tfref-v2-sg-relations`;
- required retrieval resource types: `aws_vpc`, `aws_lb`, `aws_db_instance`, and
  `aws_security_group`;
- required generated Terraform resource types: `aws_vpc`, `aws_lb`, `aws_db_instance`, and
  `aws_security_group`.

The operating requirement is that a successful RAG result provide the repository evidence required
by the fixed evaluation contract, not merely produce plausible Terraform that passes structural
validation. The observed limitation leaves grounding provenance insufficient despite apparently
successful output. It does **not** establish that the Terraform was hallucinated or incorrect.

## Direct observed evidence

The authoritative retained M4 full comparison is workflow run `36391698161`, source commit
`2526f85b4bef781976126f99e5ed0c25344d0f94`, artifact
`m3-live-baseline-36391698161`, artifact ID `10955719668`, digest
`sha256:3697bf1a703b437132d6bf98eb89a51ab45810154afb02d3e5d3e1ffcbb7d1c6`.

For `arch-vpc-three-tier`, its machine-readable trace records:

| Stage | Result |
|---|---|
| fact extraction | `PASS`, `4403 ms` |
| retrieval | `PASS`, `2591 ms` |
| generation | `PASS`, `26038 ms` |
| validation | `PASS`, `1 ms` |
| first divergence | none |

Here retrieval `PASS` means **retrieval transport/stage success**: the invocation completed. It does
not mean **retrieval expectation / grounding coverage success** against the dataset.

The trace records `requestedTopK = 8`, `resourceTypeFilters = []`, and these ordered hits:

1. `tfaws-5.100.0-aws_lb-example-1-application-load-balancer`
2. `tfref-v2-alb-private-origin`
3. `tfaws-5.100.0-aws_lb_target_group-example-1-instance-target-group`
4. `tfaws-5.100.0-aws_lb_listener-example-1-forward-action`
5. `tfaws-5.100.0-aws_lb-example-2-network-load-balancer`
6. `tfaws-5.100.0-aws_vpc_security_group_ingress_rule-example-1-example-1`
7. `tfaws-5.100.0-aws_vpc-overview`
8. `tfaws-5.100.0-aws_db_subnet_group-example-1-example-1`

Exact dataset scoring gives:

- required project-decision coverage@8: matched `0`, required `1`, coverage `0 / 1`; missing
  `tfref-v2-sg-relations`. The unrelated decision `tfref-v2-alb-private-origin` was rank 2.
- required resource-type coverage@8: matched `aws_lb` and `aws_vpc`; missing `aws_db_instance` and
  `aws_security_group`; matched `2`, required `4`, coverage `2 / 4`.

`aws_db_subnet_group` does not satisfy `aws_db_instance`, and
`aws_vpc_security_group_ingress_rule` does not satisfy `aws_security_group`; they are distinct
resource types.

Nevertheless, generation produced all four required Terraform resource types (`4 / 4`),
`TerraformDraftValidator = PASS`, and the draft expressed security-group relationships among the
ALB, application tier, and database tier. Downstream success alone therefore does not demonstrate
grounding in required repository evidence.

The missing decision already exists in corpus `terraformers-reference-v3` as
`tfref-v2-sg-relations`: authority `PROJECT_DECISION`, title `Security group relationships`,
resource type `aws_security_group`, priority `100`, and risk tags `no-public-ingress` and
`least-privilege`. The observation is not evidence that this decision is absent from the corpus;
corpus expansion is not the default fix and remains evidence-gated.

## Grounding-gap definition

The scorer must deterministically derive these evaluation/report concepts, not add them to
production domain state:

```text
GROUNDING_GAP =
required retrieval evidence incomplete
AND
generation succeeds

GROUNDING_GAP_WITH_VALID_OUTPUT =
GROUNDING_GAP
AND
Terraform validation passes
```

## Candidate mechanism

The retained VPC facts contain resource candidates including:

```text
AWS::EC2::VPC
AWS::ElasticLoadBalancingV2::LoadBalancer
AWS::EC2::Subnet
AWS::EC2::Instance
AWS::RDS::DBInstance
AWS::EC2::SecurityGroup
```

`RetrievalQueryTextBuilder` includes those values in query text. `ReferenceQuery`, however, derives
resource filters from Terraform-shaped query values matching `aws_[a-z0-9_]+`. The trace therefore
recorded `resourceTypeFilters = []`. `OpenSearchKnnQueryBuilder` applies its `resourceTypes` terms
filter only when that list is non-empty. The current evidence establishes this path for the run:

```text
CloudFormation-style extracted resource vocabulary
→ no Terraform aws_* resource filters
→ unfiltered semantic k-NN retrieval
```

### Why this is not yet root cause

This path could contribute to the coverage miss, but it does not prove the full causal mechanism or
a fix. A controlled decision experiment must distinguish missing/empty filters, top-K cutoff,
semantic query construction, vector ranking, project-decision authority/priority handling, and
combinations of these. Resource-vocabulary normalization remains one candidate, not a conclusion.

## Measurement gaps

- `EvaluationRunner` preserves raw retrieval provenance, but stage `PASS` represents a completed
  invocation even when fixed retrieval expectations are incomplete.
- No canonical machine-generated repeated-run Case A scorer currently combines required-decision
  coverage/rank, required-resource coverage/rank, grounding-gap count, multi-run failure frequency,
  latency distribution, and retrieval-coverage distribution.
- `evaluation/baselines/m3-live-baseline-metrics.json` has useful deterministic counters but is
  retained M3 evidence, not a general repeated Case A scoring pipeline.
- No separate Case A holdout dataset exists.
- The live workflow supports `m3-full-baseline`, `m4-arch-vpc-three-tier`, and
  `m4-arch-private-aoss`, with M3/M4-specific confirmations; it has no dedicated repeated Case A
  experiment. Historical input names must not be silently repurposed.
- The retained configuration fingerprint does not fully encode the M4 fact-extraction-specific
  thinking identity or its retained fact-extraction output-budget identity
  (`MAX_FACT_TOKENS=800`).

These are measurement-readiness gaps, not authorization to alter production retrieval.

## Repeated baseline contract

Before any production retrieval change, run `N = 3` independent full canonical evaluations of the
unchanged six-case `terraformers-eval-v1`. Three runs are a bounded reproducibility screen, not a
statistically strong reliability-rate estimate. Every scheduled run counts; failures must not be
rerun until they pass. Do not calculate or claim p95/p99 from three runs.

Freeze at least this identity:

```text
dataset = terraformers-eval-v1
corpus = terraformers-reference-v3
providerVersion = 5.100.0
analysisProvider = vertex
embeddingProvider = vertex
retrievalMode = REQUIRED
topK = 8
generationModel = gemini-3.8-flash
embeddingModel = gemini-embedding-001
embedding dimension = 1024
fact-extraction thinking = LOW
fact-extraction max output tokens = 800
generation/live-evaluation max output tokens = 8192
```

Measurement readiness must encode both the exact fact-extraction thinking identity and the
fact-extraction output-budget identity in experiment provenance/fingerprint before this baseline if
they are not currently represented. Case A is isolating retrieval behavior, so the M4
`LOW` thinking behavior and retained `MAX_FACT_TOKENS=800` fact-extraction bound must remain frozen
across repeated before/after experiments. The distinct generation/live-evaluation output bound
remains `8192`. The canonical fixtures and expectations remain unchanged.

For every run record fact-extraction status/failure category; retrieval, generation, and validation
status; first divergence; stage and observed end-to-end latencies; query text, filters, and top-K;
ordered hits and required retrieval coverage/ranks; required/forbidden generated-resource coverage;
negative-control classification; Terraform validation; output tokens when exposed; and input tokens
or cost only when reliably exposed. Never fabricate unavailable usage or cost. Aggregate meaningful
values with count, failure frequency, min, median, and max—never small-sample percentiles.

The VPC target starts from the retained observation: decision coverage@8 `0 / 1`, retrieval
resource-type coverage@8 `2 / 4`, generated required resources `4 / 4`, and Terraform validation
`PASS`. The repeated baseline must show whether the miss repeats, varies with upstream facts, varies
in rank, or was a one-run outcome. No retrieval fix precedes this baseline.

Record AOSS fact-extraction latency in all three runs, report min/median/max, and preserve failures
separately. Do not make a timeout/model/topology change from the historical `130489 ms` observation
alone.

## Retrieval scoring contract

Score deterministically against each applicable positive `EvaluationCase.retrieval` contract:

- **Project-decision coverage:** `required_project_decision_matched`,
  `required_project_decision_total`, coverage@K, rank of each present required decision, and missing
  required decision IDs.
- **Required resource-type coverage:** `required_resource_type_matched`,
  `required_resource_type_total`, coverage@K, first rank containing each required type, and missing
  required types.
- **Retrieved evidence:** ordered document IDs, rank, score, authority, `documentType`,
  `resourceTypes`, priority, and `riskTags`.
- **Query provenance:** query text, `resourceTypeFilters`, requested top-K, corpus version, provider
  version, and embedding identity.
- **Downstream evidence:** whether generation was reached, required generated-resource coverage,
  forbidden generated-resource count, Terraform validation, and first divergence.

## Holdout contract

`terraformers-eval-v1` remains immutable. A later bounded measurement-readiness task must create and
freeze a separate versioned `terraformers-eval-holdout-v1` **before** a production candidate change;
this task creates no holdout files.

Minimum composition is two new positive architecture fixtures, one ambiguous/cropped control, and
one non-architecture control. Positive fixtures must be repository-owned synthetic inputs; differ
materially in visual/topology composition from the canonical six; use only expectations verified to
exist in the frozen current corpus; state required project-decision and/or resource-type retrieval
expectations; include relationship-sensitive grounding where appropriate; and define
required/acceptable/forbidden generated types and validation expectations. At least one positive
case must exercise the same class of grounding mechanism without copying the VPC fixture. Do not add
corpus documents merely to make holdout cases pass.

Freeze stable case IDs, fixture SHA-256, expected classification, required/acceptable/forbidden
components and relationships, retrieval expectations, generation expectations, and validation
expectation. Verify every expected reference in the frozen corpus before authoring the expectation.

The holdout detects overfitting to `arch-vpc-three-tier` and the canonical six. No case-specific
production exceptions (including VPC, security-group-fixture, or expected-document branches) are
allowed. A production candidate must generalize through a provider/corpus-independent mechanism.
Holdout before/after runs use the canonical frozen identity except for the deliberately selected
retrieval change.

## Alternative comparison matrix

| Alternative | Question answered | Expected advantage | Primary risk | Evidence needed |
|---|---|---|---|---|
| Current control | Is current miss reproducible? | no change | preserves gap | repeated baseline |
| Top-K adjustment | Is required evidence below cutoff? | higher recall | noise/latency/token growth | ranks at wider K |
| Resource vocabulary normalization | Does restoring filters improve coverage? | targeted candidate set | over-filtering / mapping errors | fixed-probe coverage |
| Metadata/resource filtering | Can metadata narrow evidence correctly? | relevance | cross-resource evidence loss | cross-case coverage |
| Bounded deterministic reranking | Are useful documents retrieved but badly ordered? | improved top-K ordering | ranking bias/complexity | candidate-set/rank comparison |

The current control retains present query construction, semantic k-NN, `topK = 8`, and filtering.
Top-K-only diagnostics change no query, filter, corpus, embedding, or reranking; later work must
freeze candidate K values and record required ranks, irrelevant evidence, latency, reference/input
expansion, and observable token impact without changing production top-K. Normalization/filter
recovery must be generic and provider/corpus-contract based, never fixture-specific. Metadata
filtering must measure whether cross-resource decisions are lost. Any bounded reranking policy must
be explicit, deterministic, and evaluated only after candidate retrieval; metadata availability or
`priority=100` alone is not justification, and no LLM judge/reranker is in the default set.

Prefer an evaluation-only retrieval probe that reuses the retained VPC summary, components,
relationships, and resource candidates as fixed input against the current frozen OpenSearch
corpus/index:

```text
hold fact extraction constant
→ vary retrieval strategy only
→ observe retrieval coverage/rank/latency
```

This prevents Vertex fact variation from masquerading as retrieval improvement. If existing tooling
cannot do this, A1 may add only the smallest evaluation-only mechanism. It must not modify the
production serving path. A probe is diagnostic; any selected production candidate must still pass
the full image-to-validation evaluation.

## Decision preference rule

Prefer **the smallest general change that satisfies required grounding coverage while preserving
existing successful behavior and acceptable cost/latency trade-offs**. Technology sophistication
does not rank alternatives. Reject a top-K change that only floods generation with irrelevant
evidence and a filter that fixes VPC while excluding another case's required evidence.

## Hard regression gates

Any later candidate must satisfy:

```text
canonical terraformers-eval-v1 unchanged
negative-control classification regression = 0
previously successful deterministic Terraform validation regression = 0
new systematic first-divergence class = 0
```

For every comparable successful VPC retrieval run in the final after-state:

```text
tfref-v2-sg-relations present
required project-decision coverage = 1 / 1

aws_vpc present
aws_lb present
aws_db_instance present
aws_security_group present
required resource-type coverage = 4 / 4
```

Preserving generated output while required grounding remains missing is insufficient. After a later
approved change, run the same-shape canonical `N = 3` and compare before `N=3` with after `N=3`, not
one historical run with three tuned runs. Historical M3/M4 evidence is context, not a substitute.

The selected change must also pass frozen `terraformers-eval-holdout-v1`. Reject it if VPC improves
while holdout grounding regresses, it depends on case-specific rules, negative controls or required
validation regress, or a new systematic first divergence appears. Six canonical fixtures alone
cannot portfolio-close Case A.

## Latency/token/cost boundary

This specification invents no numeric latency threshold. The repeated before baseline must establish
the distribution; then the pre-implementation decision brief must freeze an acceptable trade-off.
Record top-K/reference-count expansion and output tokens where available, input tokens only when the
provider reliably exposes them, and cost only when exposed. Do not claim efficiency without
comparable measurements. A bounded retrieval-latency increase may be acceptable for material
grounding improvement only when explicitly measured and frozen before implementation.

## Experiment sequence

1. **A1 — measurement-readiness:** scoring + provenance + holdout + experiment support.
2. **A2 — repeated current baseline:** canonical `N=3`, frozen current behavior.
3. **A3 — retrieval alternative comparison / decision:** fixed retrieval probe, credible
   alternatives, explicit selection.
4. **A4 — minimal selected production change:** only after user approval.
5. **A5 — same-shape after evaluation:** canonical `N=3`, holdout evaluation, and
   latency/token/quality comparison.
6. **A6 — Case A integrated closure.**

No stage automatically authorizes the next. These labels do not authorize A1 now.

The existing single target runtime must be reused. Later live work must verify main SHA,
billing/quota/model access, and absence of a duplicate runtime; activate the protected target; run
only the approved experiment; preserve artifacts; return it to idle; and verify `node_count=0`.
Workflow support may be minimally extended only if A1 proves it necessary, while retaining the
protected activate/evaluate/idle lifecycle and exact trusted-SHA gates. Do not duplicate
GKE/OpenSearch/Vertex infrastructure.

## Non-goals

This task does not change production retrieval, ranking, production top-K, prompts, corpus, models,
embeddings, fact extraction, generation, retry/backoff, Terraform validation, code, tests,
workflows, datasets, fixtures, runtime, or infrastructure. It does not create the holdout, run GCP,
add reranking, LangChain, LangGraph, an LLM judge, another vector database, or a persistent Python AI
worker; start Case C; or close Case A.

## Residual risks

- One retained VPC run does not establish frequency, ranking stability, or causality.
- Upstream fact-extraction variability can alter retrieval input unless isolated by the probe.
- Wider retrieval can trade recall for noise, latency, and generation context growth.
- Filtering can lose cross-resource evidence; deterministic reranking can encode ranking bias.
- Three runs reveal bounded reproducibility but cannot establish tail latency or a reliability rate.
- Token/cost fields may remain unavailable, and corpus/index behavior may vary despite frozen
  declared identity.
- The holdout design itself can still encode evaluator bias; frozen expectations and corpus checks
  mitigate but do not eliminate it.

## Immediate next task

After this specification is reviewed and merged, seek separate user approval for the bounded
**Case A retrieval-grounding measurement-readiness implementation**. Its allowed purposes are only:

1. deterministic retrieval-expectation scoring;
2. rank/coverage reporting;
3. multi-run aggregation;
4. configuration identity including fact-extraction thinking behavior and fact-extraction
   output-budget identity;
5. an evaluation-only retrieval probe if required;
6. creation of the frozen holdout dataset; and
7. minimal Case A execution support using the existing target runtime/workflow pattern.

It must not change production retrieval semantics. Do not implement A1 from this specification.
