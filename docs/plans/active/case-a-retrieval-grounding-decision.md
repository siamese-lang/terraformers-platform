# Case A Retrieval-Grounding Measurement and Decision Specification

## Status

**A5 CANONICAL N=3 COMPLETE — HARD GATES NOT MET — HOLDOUT NOT RUN — CASE A OPEN**

A1 measurement readiness, A2 repeated baseline, and A3 protected live comparison are complete. A3
run `36600868601` used source `ab1ae4b1db6011b2fe72a7c5a2b3913232d63857`; artifact
`case-a-a3-retrieval-probe-36600868601` has digest
`sha256:7052c94eac9f00f4d80514f534d5b701ea2f0985493faea487c473e975d51c74`.
The A4 candidate was merged at `34c9dbecfbecdf4bca2be47b64d771c9e68549ca`. The subsequent
evidence-role-aware selector at `489063cbc85ca7208c5e6902c6fa6aa29b4893b9` preserved `4/4` VPC
required-resource coverage in a fresh canonical `N=3` and passed validation non-regression, but did
not meet the project-decision consistency gate. The frozen holdout remains unrun.

## Decision checkpoint

The user-selected Case A primary problem is **retrieval grounding / required-evidence coverage**.
The M4 fact-extraction `RESPONSE_TRUNCATED` remediation is retained as a completed precursor rather
than re-selected. The AOSS `130489 ms` fact-extraction observation remains a secondary measurement
item because it is one extreme observation, while the same case passed at `9392 ms` in bounded M4
reproduction. A latency-focused change requires a new evidence gate if repeated evidence establishes
a material pattern.

This specification freezes the problem, evidence, measurement contract, alternatives, and decision
boundaries. The A3 evidence and user-approved A4 direction select resource-aware candidate
acquisition with bounded resource-coverage selection. Each later stage still requires separate user
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

## Historical measurement-readiness gaps (closed by A1)

The following gaps were identified before A1 and are retained as decision history. A1 closed these
measurement-readiness gaps before A2 was executed.

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

## A2 repeated baseline checkpoint

A2 is **COMPLETE** on source commit `96c6442026f6c57a30a9b248a8af115df1e7f2e4`. Three full
canonical evaluations reproduced the VPC grounding gap `3 / 3` while fact extraction and retrieval
each passed `18 / 18`, architecture validation passed `12 / 12`, negative controls were correct
`6 / 6`, and first-divergence failures were `0`. Across 12 applicable positives there were `4 / 12`
grounding gaps, all with valid output.

All three VPC queries had non-empty resource filters containing `aws_vpc`, `aws_subnet`, `aws_lb`,
`aws_security_group`, and `aws_db_instance`. The earlier CloudFormation-style/filter-loss mechanism
is therefore insufficient to explain A2. Coverage/rank and upstream fact/query wording varied; vector
ranking is not established as the root cause. The AOSS historical `130489 ms` fact-extraction outlier
did not reproduce (`2832 / 3053 / 4443 ms` min/median/max). No latency/model/timeout/topology work is
authorized.

The next candidate is **A3 fixed-facts retrieval alternative comparison / decision** to isolate
upstream variability from retrieval/ranking behavior. A3 is not started or authorized here and
requires separate user approval.

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

## Holdout contract (implemented by A1)

`terraformers-eval-v1` remains immutable. A1 created and froze the separate versioned
`terraformers-eval-holdout-v1` **before** any production retrieval candidate change. The contract
below remains the acceptance boundary for later candidate evaluation.

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
5. **A5 — same-shape after evaluation:** canonical `N=3`, then holdout evaluation only after the
   canonical hard gates pass, with latency/token/quality comparison.
6. **A6 — Case A integrated closure.**

No stage automatically authorizes the next. A1, A2, and A3 are complete; A4 was separately approved
and merged at `34c9dbecfbecdf4bca2be47b64d771c9e68549ca`. The valid A5 canonical `N=3` is complete,
but its hard gates were not met. The frozen holdout was not run and remains gated on a corrective
candidate passing a fresh canonical `N=3`.

The existing single target runtime must be reused. Later live work must verify main SHA,
billing/quota/model access, and absence of a duplicate runtime; activate the protected target; run
only the approved experiment; preserve artifacts; return it to idle; and verify `node_count=0`.
Workflow support may be minimally extended only if A1 proves it necessary, while retaining the
protected activate/evaluate/idle lifecycle and exact trusted-SHA gates. Do not duplicate
GKE/OpenSearch/Vertex infrastructure.

## Original decision-specification non-goals (historical boundary)

These constraints describe the pre-A1 decision-specification task and are retained as historical
scope boundaries; the A1/A2 checkpoints below supersede any current-state reading of them.

This task does not change production retrieval, ranking, production top-K, prompts, corpus, models,
embeddings, fact extraction, generation, retry/backoff, Terraform validation, code, tests,
workflows, datasets, fixtures, runtime, or infrastructure. It does not create the holdout, run GCP,
add reranking, LangChain, LangGraph, an LLM judge, another vector database, or a persistent Python AI
worker; start Case C; or close Case A.

## Residual risks

- A2 reproduced the VPC grounding gap in all three bounded runs, but `N=3` does not establish a
  long-run reliability rate or causality; coverage/rank and upstream fact/query wording still varied.
- Upstream fact-extraction variability can alter retrieval input unless isolated by the probe.
- Wider retrieval can trade recall for noise, latency, and generation context growth.
- Filtering can lose cross-resource evidence; deterministic reranking can encode ranking bias.
- Three runs reveal bounded reproducibility but cannot establish tail latency or a reliability rate.
- Token/cost fields may remain unavailable, and corpus/index behavior may vary despite frozen
  declared identity.
- The holdout design itself can still encode evaluator bias; frozen expectations and corpus checks
  mitigate but do not eliminate it.

## A3 comparison and A4 decision checkpoint

The completed comparison is documented in [Case A A3 Fixed-Facts Retrieval Probe
Evidence](../../evaluation/case-a-a3-retrieval-probe-readiness.md). Required VPC evidence was
available below the current global K8 cutoff: `aws_db_instance` evidence was repeatedly around rank
12 and `tfref-v2-sg-relations` around rank 8–10. Wider retrieval established candidate availability,
not K24 as a production solution.

A4 rejects simple global K24 widening, unfiltered retrieval, relationship-first retrieval, and pure
global priority reranking. The selected candidate gives every distinct query resource type one
singleton-filtered, final-limit-bounded search opportunity while reusing the single query embedding,
then deduplicates by document ID and applies bounded deterministic resource-coverage selection.
Global results remain the fill baseline and remain unchanged when already adequate. The A4 candidate
was merged at `34c9dbecfbecdf4bca2be47b64d771c9e68549ca`. This was the decision at that time; the A5
evidence below now establishes which part improved and which acceptance conditions remain open.

## A5 canonical after-state and corrective decision checkpoint

### Historical A5 run continuity

The pre-PR #115 attempted run `36654267799`, from source
`21167c57b0b51643890d80ce2364318655c050c1`, is not a valid A5 after-state. Structured resource
types were lost along the integration path and `resourceTypeFilters` became empty, so the
resource-aware retrieval path was bypassed. PR #115 corrected that integration contract and merged
at source `33769a220f8265270bd3bb842922640c73caedcf`.

The first valid A5 canonical `N=3` after that correction consisted of runs `36661209926`,
`36661894427`, and `36661979115`:

| Canonical result | First valid A5 `N=3` result |
|---|---|
| VPC required-resource coverage | `4/4` in `3/3` |
| VPC `tfref-v2-sg-relations` | `2/3` |
| Grounding gaps | `1/12` |
| Positive Terraform validation | PASS `11/12` |
| Negative controls | correct `6/6` |

Run `36661894427` failed validation because the generated Terraform contained placeholder/example
output, including a placeholder ACM certificate ARN. That observation was not attributed to
retrieval. The later PR #117 fresh canonical `N=3` below did not reproduce this validation
regression and passed positive validation `12/12`.

### Fresh canonical boundary and frozen identity

The evidence-role-aware selector implementation was evaluated with a fresh canonical
`terraformers-eval-v1` `N=3`: workflow runs `36672724021`, `36672728974`, and `36673383660`, all
from authoritative source `489063cbc85ca7208c5e6902c6fa6aa29b4893b9`. The frozen holdout was
not run.

### A5 evidence and acceptance result

| Canonical result | Fresh `N=3` result |
|---|---|
| Fact extraction | PASS `18/18` |
| Retrieval | PASS `18/18` |
| Positive Terraform validation | PASS `12/12` |
| Negative controls | correct `6/6` |
| First divergence | `0` |
| Grounding gaps | `1/12` |
| VPC required-resource coverage | `4/4` in `3/3` |
| VPC `tfref-v2-sg-relations` | `2/3` |

Required-resource coverage therefore remains solved at `4/4` in all three VPC runs, validation
non-regression passed at `12/12`, and all six negative controls passed. Project-decision consistency
still failed at `2/3`, so the existing Case A hard gate remains unmet and holdout remains unrun.

The failed VPC sample was run `36672724021`. Its runtime resource filters were `aws_vpc`,
`aws_subnet`, `aws_lb`, `aws_lb_listener`, `aws_lb_target_group`, `aws_db_instance`, and
`aws_security_group`. Final K8 contained `tfref-v2-alb-private-origin` but omitted
`tfref-v2-sg-relations`. For that runtime resource set, the ALB/CloudFront decision had three matched
resources and one unsupported resource (`aws_cloudfront_distribution`), while the security-group
decision had one matched resource and no unsupported resources.

### Remaining selector defect

The remaining retrieval problem is the deterministic ordering within the eligible project-decision
set, not candidate acquisition, an OpenSearch failure, or a missing corpus document. The fresh
canonical evidence establishes the mechanism: **greater resource overlap is insufficient evidence
of applicability when a candidate also depends on resource types absent from the runtime
architecture.** The current comparator prioritizes three matching resource types before one
unsupported dependency, allowing a broader but partially incompatible decision to outrank a fully
self-contained decision. The decision-for-decision structural replacement rule has the same
ordering defect.

The selector must continue to model the separate evidence roles generation may require:

```text
provider/schema evidence
→ how the Terraform resource is expressed

project-decision evidence
→ which repository-owned architectural/security constraint should govern it
```

Consequently, the broad ALB/CloudFront decision was considered before the fully contained security-
group decision and occupied the saturated final K8. This explains the remaining `2/3` project-
decision consistency despite `4/4` VPC resource coverage in `3/3`. Simply increasing K or changing
candidate acquisition is not the selected correction.

### Selected corrective candidate: evidence-role-aware bounded retrieval

The selected corrective candidate is a **general evidence-role-aware bounded retrieval design**. It
must be validated and does not yet prove holdout generalization. Production retrieval may use only
normal runtime facts and corpus metadata:

- from `ArchitectureRetrievalFacts` / `ReferenceQuery`: summary, components, relationships, and
  canonical Terraform `resourceTypes`;
- from retrieved corpus documents: semantic score, `authority`, `resourceTypes`, `priority`,
  `riskTags`, and document identity for deduplication only, never expected-ID matching.

Production code must not receive or inspect evaluation case IDs, `requiredProjectDecisionIds`,
`requiredReferenceIds`, holdout IDs, `arch-vpc-three-tier`, or `tfref-v2-sg-relations` as a special
constant. Evaluation expectations remain evaluation-only.

#### Candidate acquisition contract

Retain the current single embedding, the bounded global semantic search, and one bounded singleton
resource search per distinct query resource type. Add one bounded **project-decision candidate
lane**, using the same embedding and filtered by:

```text
authority = PROJECT_DECISION
resourceTypes intersects query.resourceTypes
```

The implementation may minimally extend the existing OpenSearch metadata-filter mechanism for
authority filtering. This lane is necessary because selection cannot preserve a relevant decision
that corpus growth excludes from both the global and singleton-resource top-K candidate sets. The
lane's resource intersection is **candidate acquisition only**; overlap alone is not sufficient
evidence that a decision is applicable or eligible for forced promotion. Each candidate search
remains bounded by the existing final K, and final selected context remains bounded by K8. This does
not authorize a second embedding call, an LLM reranker, a judge call, a new retrieval service,
global K24, or a larger final generation context.

#### Evidence-role-aware selection contract

The final selector must reason about three separate dimensions:

```text
1. required/requested resource coverage

2. provider evidence coverage
   authority in:
   - PROVIDER_SCHEMA
   - PROVIDER_DOCUMENTATION

3. applicable project-decision evidence
   authority:
   - PROJECT_DECISION
```

The correction must preserve required resource coverage while admitting applicable
project-decision evidence where capacity permits. Provider evidence and project-decision evidence
serve different roles; this design does not make every `PROJECT_DECISION` document more important
than provider evidence. Project-decision coverage is modeled as distinct documents / distinct
decision evidence, not as a boolean coverage state per resource type. Two different applicable
decisions may both be valuable even when both reference the same requested resource. Resource
overlap and new-resource coverage are relevance signals, but they are not a stopping condition that
declares all decision evidence for a resource satisfied.

For each `PROJECT_DECISION` candidate, compare metadata with the runtime query resource set using
general signals equivalent to:

```text
matchedResources = candidate.resourceTypes ∩ query.resourceTypes
unsupportedResources = candidate.resourceTypes - query.resourceTypes
```

A `PROJECT_DECISION` candidate may be force-promoted only when it intersects the query resource set
and at least one of these generic eligibility conditions holds:

1. its `resourceTypes` are a subset of `query.resourceTypes`, so it has no unsupported resource
   dependency; or
2. the same document was independently retrieved through the normal global or per-resource
   semantic lane.

Within that eligible set, the frozen deterministic `PROJECT_DECISION` ordering is:

1. fewer unsupported resource types;
2. greater matched query-resource count;
3. higher semantic similarity;
4. higher corpus priority; and
5. stable discovery order.

`unsupportedResources == 0` represents a decision whose declared resource dependencies are fully
contained in the runtime architecture. It must take precedence over a broader decision that overlaps
more requested resources but depends on resources absent from that architecture. New-resource
coverage may contribute as a relevance signal, but does not merge distinct decisions into resource-
level boolean coverage. Neither case IDs, document IDs, nor expected IDs may participate in
eligibility or ordering.

#### Safe replacement and context-budget saturation

After protecting requested resource coverage and provider anchors, distinct eligible decision
candidates are considered in the deterministic relevance order above and admitted only while safe K
capacity exists. A project-decision candidate may enter final K only if replacing an existing
selected document does not reduce requested resource coverage or provider evidence coverage already
secured for requested resources. Prefer replacing redundant evidence before unique evidence. The
only provider anchor for a requested resource must not be removed merely to add a project decision,
and no decision may be inserted if required resource coverage would decrease. For
decision-for-decision replacement, an incoming decision is structurally better only when it has
fewer unsupported resource types, or when unsupported counts are equal and it matches more query
resources. Semantic score, priority, and discovery order must not replace one structurally
equivalent distinct decision with another. Already selected distinct decision evidence remains
protected when it is structurally equivalent. The result must never exceed configured final K.

Some future architecture may require more independent provider and decision evidence than final K
can hold. The implementation must therefore distinguish conceptually between:

```text
candidate not found
candidate found but final context budget cannot safely admit it
```

This is a residual condition to expose through existing or new bounded selection diagnostics if
implementation requires it, not a reason to increase K now. Any future K, token-budget, or
compression decision requires separate evidence.

### Explicitly rejected or forbidden corrective paths

The prior A3 conclusions remain in force. This corrective decision does not authorize VPC-specific
logic; `tfref-v2-sg-relations` hardcoding; holdout-specific logic; passing evaluation expectations
into production retrieval; global K24; increasing final K8; corpus expansion to force a pass; global
unfiltered retrieval; relationship-only retrieval replacing the current query; pure global priority
reranking; “PROJECT_DECISION always wins” sorting; LLM reranking; judge/model expansion; LangChain;
LangGraph; prompt tuning to hide the selector defect; generation tuning for the one placeholder
observation; or new GCP/runtime topology. Rejected alternatives require new evidence before they can
be reopened.

### Later implementation validation contract

This docs-only decision freezes, but does not implement, later unit-test coverage. Tests must use
synthetic resource names such as `aws_alpha` and `aws_beta`, not Case A fixture names, and cover:

1. empty resource types preserve the existing single global semantic-search behavior;
2. one embedding is still reused for the resource-aware path;
3. project-decision acquisition is generic and authority-filtered;
4. an applicable decision can be admitted when global results already cover all resource types;
5. redundant provider evidence may be replaced by applicable decision evidence;
6. the only provider anchor for a requested resource is protected;
7. required/requested resource coverage never decreases during decision promotion;
8. a project decision sharing only one query resource while depending on several unsupported
   resources is not force-promoted solely because of that overlap;
9. that same candidate may become eligible when independently retrieved by a normal global or
   per-resource semantic lane;
10. an unrelated or broad project decision cannot win solely because its priority is high;
11. resource-coherent decisions outrank less coherent alternatives under the frozen comparator;
12. one decision covering several requested resources is deduplicated and credited once;
13. two distinct applicable decisions for the same requested resource may both be retained when
   safe final K capacity exists;
14. multiple applicable decisions may be retained when final K capacity permits;
15. no decision candidate preserves prior A4 behavior;
16. final results never exceed K; and
17. no case-ID, document-ID, or expected-ID rule exists.

No live Vertex test and no new verifier or workflow is authorized by this contract.

## Immediate next task

After this bounded ordering correction is reviewed and merged, the sequence is:

1. run a fresh canonical `terraformers-eval-v1` `N=3`;
2. evaluate all existing hard regression gates;
3. only if canonical `N=3` passes, run frozen `terraformers-eval-holdout-v1` once; and
4. then perform the Case A closure decision.

For every comparable successful VPC retrieval in the corrected `N=3`, the unchanged acceptance
contract requires `tfref-v2-sg-relations` present in `3/3`, project-decision coverage `1/1` in every
run, and `aws_vpc`, `aws_lb`, `aws_db_instance`, and `aws_security_group` present with resource-type
coverage `4/4` in every run. Across the canonical `N=3`, negative-control classification regression,
previously successful deterministic Terraform validation regression, and new systematic
first-divergence class must each equal zero.

Do not run the frozen holdout before those canonical gates pass. Run `36661894427` does not weaken
them. If placeholder behavior reproduces after the selector correction, treat it as a separate
generation-reliability investigation rather than folding it into this retrieval change. Only after
the canonical gates pass may the frozen holdout run once. Do not proceed automatically to this
implementation or to Case C.

## A1 measurement-readiness checkpoint

A1 is **COMPLETE** as documented in [Case A Retrieval-Grounding Measurement Readiness](../../evaluation/case-a-retrieval-grounding-measurement-readiness.md). The primary problem remains retrieval grounding / required-evidence coverage. Deterministic exact-match scoring, explicit `LOW`/`800`/`8192` provenance, per-run reporting, compatible multi-run aggregation, a fixed-facts evaluation-only probe, and frozen `terraformers-eval-holdout-v1` now make the problem comparable.

At A1 closure, no production retrieval change was authorized or performed and no live GCP
evaluation had run; A2 was the next candidate. Those were historical A1 boundaries. A2 and A3 are
now complete, the A4 candidate was merged at `34c9dbecfbecdf4bca2be47b64d771c9e68549ca`, and
the valid A5 canonical `N=3` did not meet its hard gates. Case A remains open pending a separately
reviewed corrective implementation and validation.
