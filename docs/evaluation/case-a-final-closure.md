# Case A Final Closure — AI/RAG Quality, Performance & Reliability

## Status

**PORTFOLIO-CLOSED — PASS**

Case A is closed on source commit:

`32d62e21821ed303555ade5e26b03cb669db94ef`

No additional live AI evaluation, rerun-until-lucky loop, model change, prompt change, retrieval change,
corpus change, timeout change, retry change, or new evaluation infrastructure is authorized by this
closure.

## Problem and operating scenario

The user uploads a cloud architecture image and the service executes:

`image → architecture fact extraction → retrieval → Terraform generation → validation`

The retained Case A problem was not simply whether the workflow could complete. The repository had
evidence for three distinct AI/RAG risks:

1. normal inputs could fail during fact extraction because provider output was truncated;
2. retrieval could technically succeed while omitting required repository/provider evidence from the
   final generation context;
3. changes that improved the six known canonical fixtures could overfit them rather than generalize.

A secondary latency investigation also observed large stochastic provider-side response tails.

## Retained before-state

Canonical M3 before-state:

- dataset: `terraformers-eval-v1`
- run: `36379633596`
- fact extraction: `4/6 PASS`, `2/6 FAIL`
- first divergence: two `FACT_EXTRACTION / PROVIDER_RUNTIME` observations

Later targeted reproduction classified at least one affected case more precisely as
`OUTPUT_TRUNCATED / RESPONSE_TRUNCATED`.

The retrieval-specific before-state was also reproducible. In the repeated A2 baseline the VPC case
missed required grounding in `3/3` runs despite downstream Terraform generation/validation being
able to succeed. That established that "generation passed" did not imply "required evidence was
present."

## Selected retrieval mechanism

The accepted retrieval direction remained bounded and evidence-role-aware:

- preserve semantic retrieval rather than replace it with evaluation-ID rules;
- preserve provider/schema evidence needed to express Terraform resources;
- separately preserve applicable repository-owned `PROJECT_DECISION` evidence;
- protect requested-resource coverage when admitting project-decision evidence;
- avoid hard-coded case IDs, expected IDs, fixture IDs, or one-off VPC rules;
- reuse one embedding;
- retain bounded candidate searches;
- later separate base semantic top-K from bounded final evidence capacity so architectures needing
  more than eight independent evidence documents are not structurally truncated at final K8.

The >8-resource behavior was independently live-validated in run `36799722509`:
control maxEvidence 8 selected 8 documents and covered `10/12` requested resource types; adaptive
maxEvidence 16 selected exactly 10 documents and reached `12/12`, adding only the missing
`aws_route_table` and `aws_db_instance` provider-schema anchors.

## Post-PR119 canonical N=3

The final canonical revalidation used exactly three independent workflow dispatches, all on the same
merged source SHA and all at workflow attempt 1:

1. `36803174654`
2. `36803781744`
3. `36804600570`

Shared identity:

- source SHA: `32d62e21821ed303555ade5e26b03cb669db94ef`
- dataset: `terraformers-eval-v1`
- corpus: `terraformers-reference-v3`
- provider version: `5.100.0`
- analysis provider: `vertex`
- embedding provider: `vertex`
- retrieval mode: `REQUIRED`
- base top-K: `8`
- generation model: `gemini-3.8-flash`
- embedding model: `gemini-embedding-001`
- fact-extraction thinking: `LOW`
- fact-extraction max output tokens: `800`
- generation max output tokens: `8192`
- configuration fingerprint:
  `sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`

Artifacts:

| Run | Raw artifact | Raw digest | Grounding report | Report digest |
|---|---:|---|---:|---|
| 36803174654 | 11136776191 | `sha256:c7e475001e15061746c187ceec1303e36c2526bfbcdd4a7789b800713f3eed2d` | 11136781226 | `sha256:4675682b58f9ba89fbcfac333b15cc9a1f0f18463c1ab426a6f7b063ae545d95` |
| 36803781744 | 11137350456 | `sha256:01611892edd973e82ccf9b70260c171c60a63c7884bf0b3d96037520fd119f54` | 11137071307 | `sha256:d810cee485343e44b7743c673a89698818fe70da5b4b380e399167321031be24` |
| 36804600570 | 11136698746 | `sha256:57113234f0290fc1fe0236a86f0c750f241fd464762414a721580b823641800d` | 11136738736 | `sha256:2b6504b9f03d88717be26635a68e7b5ebbd650b675ddd86130a22448154ee8d4` |

Final N=3 hard-gate result:

| Gate | Result |
|---|---|
| fact extraction | `18/18 PASS` |
| retrieval | `18/18 PASS` |
| VPC project-decision coverage | `3/3 PASS` |
| VPC required-resource coverage | `4/4 × 3 PASS` |
| grounding gaps | `0/12` |
| positive Terraform validation | `12/12 PASS` |
| canonical negative controls | `6/6 correct` |
| new first divergence | `0` |
| source/config compatibility | `PASS` |

The VPC `tfref-v2-sg-relations` decision ranked 6, 8, and 8 across the three runs. That rank
variation is evidence of normal semantic-ranking variability, while all three runs still preserved
the required decision inside the bounded selected context.

The two negative controls were classified correctly in every run with no Terraform generation.

Important limitation: the final N=3 did not itself reproduce the exact successful-zero-hit path that
motivated PR #119. The dashboard negative control received global retrieval evidence in all three
final runs and still classified correctly. Therefore this closure claims canonical end-to-end
non-regression after PR #119, not three live reproductions of the zero-hit branch.

## Frozen holdout generalization gate

The holdout dataset was frozen before the later retrieval corrections and remained unchanged. It
contains two new positive retrieval-grounding scenarios and two new negative-control images.

Authoritative holdout run:

- workflow run: `36805478708`
- source SHA: `32d62e21821ed303555ade5e26b03cb669db94ef`
- attempt: `1`
- dataset: `terraformers-eval-holdout-v1`
- raw artifact: `11137633227`
- raw digest:
  `sha256:a43f7a07253ca9c8afbfe8c5194b08bcb7903f8de7b5f56e0a33dd3e0bd0bd45`
- grounding report artifact: `11137059004`
- report digest:
  `sha256:72ce674823827601594f8fb1389f1911be7eb0cfc1e97f40780c602dbad64ac5`

Shared configuration fingerprint matched the final canonical N=3:

`sha256:d10c56e4f124ee67d1cbc69457249ad0cf1243bf682305f32e65817e32b6ae66`

Holdout result:

| Case | Retrieval grounding | Generation / classification | Validation |
|---|---|---|---|
| `holdout-eks-irsa` | project decision `1/1`, resources `3/3`; `tfref-v2-eks-irsa` rank 1 | required generated resources `3/3`, no forbidden resource | PASS |
| `holdout-workload-rds-sg` | project decision `1/1`, resources `2/2`; `tfref-v2-sg-relations` rank 1 | required generated resources `2/2`, no forbidden resource | PASS |
| `holdout-ambiguous-obscured-flow` | not applicable | `AMBIGUOUS`, Terraform empty | not applicable |
| `holdout-non-architecture-status-board` | not applicable | `NON_ARCHITECTURE_IMAGE`, Terraform empty | not applicable |

Aggregate holdout counts:

- fact extraction: `4/4 PASS`
- retrieval: `4/4 PASS`
- grounding gaps: `0/2` applicable positive cases
- positive validation: `2/2 PASS`
- negative controls: `2/2 correct`
- first divergence: `0`

The holdout therefore preserved the material retrieval-grounding and negative-control behavior on
previously unused fixtures and a project decision (`tfref-v2-eks-irsa`) absent from the canonical
six-case contract.

## Latency interpretation

The canonical and holdout artifacts record stage latencies, but Case A is not closed on a claim that
the selected retrieval changes made the system faster.

The separate FACT_REUSE diagnostic closure already established that large provider response tails
can occur after request-body completion and before response headers with one HTTP exchange, zero
network failures, and HTTP 200. That evidence does not distinguish provider queuing/scheduling from
model compute. Removing the duplicate image call did not eliminate tail exposure, so production
FACT_REUSE adoption remains **HOLD**.

No generic retry, arbitrary timeout, or latency-improvement claim is selected.

## Acceptance decision

Case A satisfies the repository's portfolio-case acceptance intent:

1. **Observed problem:** extraction failures and incomplete retrieval grounding were measured rather
   than inferred.
2. **Mechanism isolation:** truncation was distinguished from generic runtime failure; retrieval
   evidence-role/capacity defects were separated from downstream Terraform success.
3. **Alternatives/trade-offs:** broader K, reranking, hard-coded expected evidence, generic retries,
   and larger architectural additions were rejected without supporting evidence.
4. **Bounded implementation:** production retrieval behavior was changed without exposing evaluation
   expectations to production code.
5. **Same-condition after-state:** final canonical N=3 passed every frozen hard regression gate.
6. **Generalization:** the frozen holdout passed on different positive and negative fixtures.
7. **Residual risk:** provider latency tails, semantic ranking variability, token-budget pressure, and
   finite holdout size remain explicit rather than hidden.

**Decision: Case A is PORTFOLIO-CLOSED.**

## Residual risks and non-claims

This closure does not claim:

- statistical reliability over a production traffic distribution;
- that four holdout cases exhaust architecture diversity;
- that every semantic ranking order is stable;
- that the system is latency-optimized;
- that provider-side response tails are solved;
- that maxEvidence 16 is universally sufficient;
- that Terraform structural validation proves deployment correctness;
- that generated Terraform was plan/applied against live infrastructure.

Future Case A work requires new evidence from production usage, a newly reproduced defect, or a
material requirement change. Do not reopen the case merely to add more runs.

## Next portfolio task

Case B is already portfolio-closed. With Case A now closed, the next representative portfolio case
is **Case C — Cloud Runtime Capacity & Safe Delivery**.

This closure does not automatically authorize load generation, rollout experiments, replica/HPA
changes, node sizing, OpenSearch sizing, or delivery changes. Case C must begin from its existing
measurement contract and first establish the representative workload and current saturation /
rollout baseline before any tuning.
