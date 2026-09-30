# Case A A3 Fixed-Facts Retrieval Probe Evidence

## Status

**A3 live comparison = COMPLETE. A4 candidate = IMPLEMENTED / LOCAL VALIDATION ONLY. A5 = NOT
RUN. Case A = OPEN.** The A4 candidate has not been merged or evaluated on the live target, so this
document does not claim that production grounding is fixed or that the holdout passes.

## Protected live comparison

The successful protected comparison is workflow run `36600868601`, source SHA
`ab1ae4b1db6011b2fe72a7c5a2b3913232d63857`, artifact
`case-a-a3-retrieval-probe-36600868601`, artifact digest
`sha256:7052c94eac9f00f4d80514f534d5b701ea2f0985493faea487c473e975d51c74`.

The current filtered semantic K8 control completed the VPC retrieval contract in `0 / 3`
snapshots. With the same current query, embedding, and resource filters, the wider diagnostic view
showed that the required evidence existed below the global K8 cutoff: `aws_db_instance` evidence
was repeatedly near semantic rank 12 and `tfref-v2-sg-relations` was near rank 8–10. Thus the
observed mechanism is cross-resource competition in one global semantic ranking, with some required
evidence falling below the final cutoff. This does not establish OpenSearch failure, missing corpus
data, missing resource filters, or a wrong embedding model.

The K24 view was diagnostic candidate-availability evidence, not a production solution. Passing all
24 documents to generation was rejected. Unfiltered retrieval did not improve ranking quality,
relationship-first retrieval regressed a control without closing the gap, and pure priority
reranking could introduce unrelated high-priority project decisions.

The earlier protected run `36592590562` aborted before comparison on Vertex embedding
`429 RESOURCE_EXHAUSTED`. Evaluation-only 13-second pacing corrected that harness/quota mismatch
without changing the frozen model, vectors, corpus, provider, fixtures, expectations, or production
retrieval behavior.

## A4 selected production candidate

A4 rejects simple global K24 widening, unfiltered retrieval, relationship-first retrieval, and pure
global priority reranking as the production path. It selects resource-aware candidate acquisition:
one existing bounded global search plus one equally bounded singleton-filtered search for each
distinct query resource type, all reusing one embedding. Results are deduplicated by document ID and
reduced to the existing final limit through deterministic uncovered-resource coverage, priority,
and semantic/discovery-order tie-breaking. Existing global results remain the fill baseline and are
preserved unchanged when they already cover every requested resource type.

This is a general response to cross-resource competition, not a search for an expected case or
document ID. It introduces neither a global candidate-pool constant nor a larger generation context.
It remains **IMPLEMENTED / LOCAL VALIDATION ONLY** until review and merge; A3 does not prove that the
A4 production candidate improves full-pipeline quality.

## Next boundary

After independent review and user-approved merge, the immediate next task is **A5 same-shape
production evaluation**: canonical `N=3`, then the frozen holdout, with before/after grounding,
regression, latency, and context comparison on the existing live target. Only that evidence can
support a Case A closure decision. A5 has not been run.
