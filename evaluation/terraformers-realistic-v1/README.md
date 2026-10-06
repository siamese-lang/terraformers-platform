# Terraformers realistic-input candidate v1

**CANDIDATE REVISION 3 — HUMAN TRUTH REVIEW REQUIRED — NOT FROZEN**

This metadata-only revision addresses the two unresolved truth-contract blockers in the [authoritative structured PR review](https://github.com/siamese-lang/terraformers-platform/pull/241#issuecomment-6015448816) of head `44b73b02840abbaf1da8349e124e73fe5ccb6e5e`. Candidate revision 2 passed visual-fidelity review. All ten PNG/SVG pairs are byte-for-byte unchanged. Source facts and PT-1 adaptations are separated for the serverless portal, and all six positives distinguish required architecture/resource intent from acceptable implementation support. Components, relationships, classifications and material forbidden interpretations remain unchanged.

## Review the actual inputs and complete truth

The [human review document](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md) embeds all ten actual PNGs and lists provenance, required components, required relationships, resource intent, forbidden interpretations, differences from historical synthetic fixtures and remaining realism limitations for each case. Exact expectations remain in `dataset.json`; semantic resource intent, visual symbol mappings and provenance remain in `provenance.json`. They are review material, not embedded input answers.

| Case | Visual grammar | Image and truth |
| --- | --- | --- |
| pt1-01-serverless-portal | Colored symbol architecture / dot-grid export | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-01-serverless-portal) / [PNG](fixtures/pt1-01-serverless-portal.png) |
| pt1-02-order-fanout | Monochrome messaging deployment sketch | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-02-order-fanout) / [PNG](fixtures/pt1-02-order-fanout.png) |
| pt1-03-parallel-lookup | Original workflow-editor screen | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-03-parallel-lookup) / [PNG](fixtures/pt1-03-parallel-lookup.png) |
| pt1-04-analytics-catalog | Dense graph-paper data-platform sketch | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-04-analytics-catalog) / [PNG](fixtures/pt1-04-analytics-catalog.png) |
| pt1-05-private-web-fleet | Nested network/subnet/AZ export | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-05-private-web-fleet) / [PNG](fixtures/pt1-05-private-web-fleet.png) |
| pt1-06-thumbnail-pipeline | Portrait notebook technical sketch | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-06-thumbnail-pipeline) / [PNG](fixtures/pt1-06-thumbnail-pipeline.png) |
| pt1-07-unresolved-design | Ordinary unfinished whiteboard notes | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-07-unresolved-design) / [PNG](fixtures/pt1-07-unresolved-design.png) |
| pt1-08-partial-export | Naturally partial diagram export | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-08-partial-export) / [PNG](fixtures/pt1-08-partial-export.png) |
| pt1-09-sprint-board | Original project-board interface | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-09-sprint-board) / [PNG](fixtures/pt1-09-sprint-board.png) |
| pt1-10-workshop-table | Paper workshop agenda | [Review](../../docs/evaluation/product-trust-pt-1-realistic-input-freeze.md#pt1-10-workshop-table) / [PNG](fixtures/pt1-10-workshop-table.png) |

## Candidate identity history

Candidate revision 1 at `2cf8a73889c6f612f5594cc7620c503646642892` was rejected in human review and is superseded. Its exact identity bytes are retained at [superseded/candidate-01-identity.json](superseded/candidate-01-identity.json), still hashing to `8bc6daebde25045fcbb0a0bdad6deaedf2e9edeb29a28b39290fc107b6e952d8`. [candidate-history.json](candidate-history.json) records the rejection. The old referenced image/document bytes are available at that immutable Git commit; archived checksums refer to those old bytes, not current paths.

Revision 2 at `44b73b02840abbaf1da8349e124e73fe5ccb6e5e` is superseded because its truth contract required correction, despite passing visual review. Its exact identity bytes are [archived](superseded/candidate-02-identity.json), hashing to `c34a9b4b3b307d09dd2688e0feac3f91c4648e71166df4005b23e7b40eb9b852`. Its assets remain the revision-3 assets; its metadata remains available at that immutable head.

Revision 3 has a newly calculated `candidate-identity.json`, with `candidateRevision: 3` and an explicit superseded identity. It pins dataset, provenance, this README, candidate history, both archived identities and all ten PNG/SVG pairs (26 files). Its own SHA-256 is recorded in program state and review evidence. Metadata changes create a new unapproved identity even when images stay unchanged.

Required resource labels describe visible or semantically required architecture intent. IAM, permissions, policies and other implementation support may use valid existing ARNs/references or equivalent configuration unless the image explicitly requires creation. Reclassification does not remove required delivery/access/routing semantics; per-case audit rationale is in provenance and the review document.

## Asset basis and reuse

- The existing `m3-evaluation-v1` schema and `EvaluationDatasetLoader` are reused unchanged. PNG bytes are the inference inputs; SVGs are editable original representations.
- All geometry, service symbols, palette/UI chrome and agenda text are original contributions. Greek λ, buckets, cylinders, chips, gears and common flow shapes are independently constructed; no official/public artwork, icon pack or screenshot is redistributed.
- Source specification URLs/resolved URLs/access dates/observed HTML SHA-256 are retained in provenance. Public availability is not treated as an artwork license.
- Drawings were constructed deterministically using local vector geometry and Pillow 12.3.0/DejaVu fonts, without image-generation or live model calls. SVG rendering on other machines may change pixels; use committed PNG bytes for measurement.
- Historical synthetic datasets are unchanged. No canonical/holdout images were copied or restyled. `modelUnderTestRunCount` remains 0 and no fixture was changed in response to Terraformers output.

## Remaining limitations and gate

These are authored reference-based diagram/UI/document recreations, not a sample collected from actual users. Varied symbols, labels and layouts address the concrete human-review finding, but do not establish realistic generalization or official-icon/photo/multilingual robustness. The existing scorer is unchanged; human review of semantic aliases and resource intent remains required.

No truth is approved: `truthApprovedBy` and `truthFrozenAt` remain null. The program stops at `HUMAN_REQUIRED: REALISTIC_DATASET_TRUTH_FREEZE`. Human review must bind all visible images and full truth to the new candidate identity. PR merge and PT-2 live baseline remain separate unapproved checkpoints.
