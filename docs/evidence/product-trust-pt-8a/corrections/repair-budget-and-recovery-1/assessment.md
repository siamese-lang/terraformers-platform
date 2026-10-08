# PT-8A repair truncation and recovery assessment

Repository-only human corrective iteration 4, bound once to remote main
`70373842629fdd569815780e52dc5f0353b97880`. Independent acceptance and USER merge
are pending. This advances AI/RAG reliability and the Case A deployed evidence
chain; tests and governance are supporting evidence, not final product acceptance.

## Supported cause and product correction

Readiness run 37744660097 accepted job a93a7dcf-c40b-43f4-b55b-357c914a72f3 and
failed after 86,959 ms with PROVIDER_OUTPUT_TRUNCATED. Independent forensic addendum
[6056738716](https://github.com/siamese-lang/terraformers-platform/pull/258#issuecomment-6056738716)
and USER-supplied correlated logs establish facts completion, retrieval 8 hits,
initial generation completion, closure retrieval 7 hits, then repair MAX_TOKENS.
The original review 6055241032/artifact's empty retrieval list is preserved and
qualified by this addendum; it is not evidence that actual retrieval was absent.

Production path is `VertexGroundedGenerationOrchestrator.generate()` → generated
resource evidence closure → `VertexGenerationStage.repair()` →
`completedResponse()` → `requireNormalCompletion()`. Previously repair reused
the initial generation config: 8,192 output tokens and no thinking override
(official model default MEDIUM), while regenerating complete HCL with expanded
official schema/reference context. MAX_TOKENS correctly failed before parsing;
repair has no truncation retry. Facts' existing 800-token bound is not the cause.
Prior evidence has no response usage or full repair payload, so the exact division
between HCL size and thinking consumption is unknown.

The narrow correction gives repair its own **16,384 output-token bound and LOW
thinking**, using the same configured Gemini 3.8 Flash and SDK 1.72.0. Official
static Google documentation confirms a 65,536 maximum and LOW/MEDIUM/HIGH support
(see `model-policy-evidence.json`). Initial generation remains 8,192 with its
existing one-time MAX_TOKENS compact fallback. Repair remains **one provider call**.
Only the repair prompt adds complete-HCL-once/no explanatory prose or comments,
omitting redundant optional defaults while explicitly preserving required args,
nested blocks, relationships, references, wiring and authorization. Full original
facts/summary/HCL, official reference/schema context and strict response schema remain.

This avoids a global budget increase, a new repair retry, incomplete patch-HCL
assembly or relaxing provider/CLI/quality validation. A larger bound can still
truncate, cost more or exceed a deadline; LOW/compact effects are not live-proven.
Any new truncation remains failure, even if the returned fragment looks valid.
Option D 370/220/10s, SDK attempts 1/HTTP statuses [], durable attempts 1, original
8-minute cutoff/fencing/sweep and single closure/repair remain unchanged.

Safe backend logs distinguish facts, initial_generation (including compact),
closure and repair; finish reason, output/thinking/total usage are recorded when
available. Closure is retrieval, so token usage is NOT_APPLICABLE. Missing provider
usage stays unknown. Observer stores only these bounded metadata fields from
job-correlated logs, without payload/image/prompt/raw log/exception message.
`outcome=received` is receipt of provider output, not quality or durable success.

## Retrieval observation correction

Observer accepts actual `embeddingProvider=VERTEX` and historical lowercase `vertex`.
Index/full ID membership/hit-count checks and expected-document source provenance
accounting are unchanged. Exact USER logs are separate deterministic fixtures,
not new Cloud Logging reads or a rewritten original artifact. Tests recover 8/7
events and 15 official hits using explicitly synthetic catalog metadata for their
actual IDs; wrong index/count/ID fails and missing/wrong-source evidence produces
zero official hits. Hit counts do not alone establish relevance or sufficiency.

## Provenance reuse and once-only recovery disposition

Authenticated GitHub downloads verified both archive digests; selected original
JSON members are byte-identical and separately inventoried in `artifact-bindings.json`.
Origin clean run **37710171426 / attempt 1 / artifact 11522067032 / source 70373842**
embedded 5,395 with zero skips, exact model/dimension/UUID/content/checksum. That
receipt remains origin-source evidence, never relabeled as a corrected-source run.

The receipt and original readiness snapshot prove complete IDs/non-vector content,
but exclude vector values and provide no clean-completion vector checkpoint,
write watermark or complete all-writer audit. A vector-only external write could
retain the same UUID and non-vector content. GitHub dispatch history alone cannot
exclude that write. **Cross-source reuse is BLOCKED_VECTOR_WRITE_CONTINUITY_UNPROVEN**;
no current live index query was performed and no executable bypass was added.

[Proposed recovery v3](../../../../evaluation/product-trust-pt-8a-corrective-recovery-procedure-v3.md)
defines independently reviewable, read-only admission of authenticated origin,
exact mapping/IDs/non-vector bodies/UUID/model/dimension and complete vector-write
continuity. It requires independent review and explicit USER approval, plus new
actual source/image-bound live authority before any execution. Current v2 receipt
and same-source history guards remain unchanged; v2 is not superseded by this draft.
If existing continuity evidence cannot be supplied, a separate USER decision is
required; no automatic reembedding, index rebuild/delete or overwrite is authorized.

The consumed failed readiness remains FAILED. A proposed corrective capability is
one distinct dispatch/attempt-1/upload total for this defect across source changes,
with complete history, immutable failed evidence and explicit authority binding.
Unknown acceptance/history stops rather than resubmits. It cannot reset A–E, which
remain NOT_RUN, or promote a successful readiness into official product acceptance.
No recovery dispatch is enabled by this PR; independent acceptance/USER merge and
the proposed amendment/live gates remain. PT-8B and teardown remain unauthorized.

## Validation and claim limits

`validation.json` records focused backend 77/77, full backend package
580 tests / 0 failures / 0 errors / 4 environment-gated MariaDB skips, and Python
observer/RAG tests including final metadata parsing. Required schema-field rejection,
MAX_TOKENS despite plausible JSON, typed provider failures/no retry, strict quality,
initial compact fallback plus at most one closure/repair, canonical workflow v4 path
and real local corpus-checker regression are covered. Metadata/scope/frozen checks
are repository-only; normal PR CI is GitHub-owned regression evidence, not acceptance.
One one-off metadata invocation used the wrong field capitalization and stopped
before completion. Its error is preserved; a dependency read corrected the invocation's
field/path selection, with no repository behavior repair or counter reset. The one
corrected metadata check passed. This is separate from the passing product test runs.

No live GCP/OpenSearch/model/embedding/image fetch/upload/publish/rollout/readiness/
official case/apply/IAM operation or manual database/browser measurement occurred.
Historical failures, censors, counters, frozen candidate/truth and v2 procedure are
preserved. No successful external acceptance, new realistic rates or live fix
effectiveness is claimed. Stop for independent review and USER merge.
