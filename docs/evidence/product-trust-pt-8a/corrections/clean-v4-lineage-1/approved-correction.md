# One USER-authorized PT-8A clean-v4 lineage correction

Authority: `AUTHORIZE_ONE_PT8A_CLEAN_V4_LINEAGE_CORRECTION`, current conversation.
Independent review [6039675842](https://github.com/siamese-lang/terraformers-platform/pull/256#issuecomment-6039675842)
reviewed `e3d3b1ceaaf9bcee9a979273850482d6f00e5663` on existing PR #256. Decision:
CHANGES_REQUIRED; sole blocker is that the frozen readiness procedure cannot produce clean
retained-v4 lineage with the old HEAD-skip/current-project-SHA ingestion path.
Execution base stays `2bdb73d20486262856bfa6b680b7bf221fa23ca0`; branch stays
`codex/product-trust-pt8a-official-acceptance-preparation`. No refresh/rebase/rebind.

USER instruction, retained verbatim:

> **AUTHORIZE_ONE_PT8A_CLEAN_V4_LINEAGE_CORRECTION**
>
> Continue on the existing PT-8A Work Package and PR #256.
>
> Implement only the clean-v4 lineage correction required by independent review `6039675842`.
>
> Use the existing `gcp-target-corpus-ingestion.yml`; do not create another workflow.
>
> Add one explicit PT-8A clean-v4 re-embedding mode that:
>
> - preserves existing v3 and ordinary A7-7 ingestion behavior;
> - rebuilds the exact PT-8A corpus using provider source `f7a3b98da589ab1d52756b0dcee0dbf2de83d635`;
> - uses project-decision source `1ae69d589ac3965733818819792d98c5638e0ae5`, not current `GITHUB_SHA`;
> - requires exact corpus checksum `da410626b80d8624e93c8a8da81206a2ed8da1b06086a75279d5646b068bd66a`;
> - requires the exact 1526 / 1514 / 1514 / 0 / 5387 / 8 / 5395 broad-v4 contract;
> - before mutation, verifies the existing index has the exact expected mapping, ID universe and non-vector document content;
> - if that content/universe is not exact, STOP for a separate destructive index-rebuild decision;
> - if exact, freshly embeds **all 5,395 documents** with `gemini-embedding-2 / 1536`, overwriting their vectors under the same document IDs rather than HEAD-skipping them;
> - performs zero existing-ID skips;
> - verifies the same retained index UUID remains bound before/after;
> - verifies the exact ID/non-vector content again after completion;
> - emits an unambiguous receipt such as `embedded_this_run=5395`, `skipped_existing=0`, exact index UUID, content identity, corpus checksum, model and dimension;
> - makes `verify_exact_v4` accept that receipt as `EXACT_REUSABLE_COMPLETED_V4`.
>
> This clean mode must require explicit `FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE` authority, bound to the exact reviewed source SHA, frozen candidate identity and frozen PT-8A procedure SHA. The historical A7-7 confirmation token alone is not sufficient.
>
> Freeze the future execution order so this clean re-embedding happens **before the first PT-8A readiness dispatch**, because historical provenance is already known to be `MODEL_PROVENANCE_UNPROVEN`.
>
> Add deterministic tests proving:
>
> - ordinary v4 rerun still skips existing IDs;
> - PT-8A clean mode re-embeds/overwrites every expected existing ID;
> - v3 cannot use clean mode;
> - missing/wrong live approval fails before embedding;
> - non-vector mismatch stops without overwrite;
> - historical project-source/checksum mismatch fails;
> - the fresh receipt is accepted by exact-v4 verification;
> - partial/mixed receipts remain non-reusable.
>
> Update the frozen PT-8A procedure/evidence and Work Package consistently.
>
> Do not perform any live GCP/OpenSearch/model/embedding/image-fetch/publish/rollout/index-delete operation in this correction.
>
> Run proportional deterministic validation and normal PR CI once. Preserve failures; do not rerun until green.
>
> Then stop at:
>
> `HUMAN_REQUIRED: FINAL_REALISTIC_AI_RAG_LIVE_MODEL_COST_ACCEPTANCE`
>
> Do not self-accept or merge.

This is human corrective iteration 1, separate from autonomous repair 1 (the preserved original
nonexistent-test-module invocation). Neither counter is reset; no second corrective pass is
implicitly authorized by a new failure. No live approval is inferred from this correction.

The vector-only update has no upsert: it overwrites each existing ID's vector without replacing
non-vector data. It does not create/delete an index or relabel mapping metadata. A failed write,
a lost acknowledgement, partial embedding, content/UUID change, retrieval failure or authority
change prevents a completed lineage receipt. Active tasks settle after pending cancellation;
acknowledged writes remain cleanup/audit-accountable in a failed receipt, without a rollback claim.
GitHub failed-run binding/partial artifacts are retained. No automatic rerun/resume occurs.

The workflow admits the clean request before WIF; the isolated pod rechecks public GitHub authority
via verified HTTPS without copying the runner token. Existing protected environment, WIF, GKE,
OpenSearch and KSA are reused. No new IAM grant or credential boundary. If public GitHub reads fail,
stop; do not silently supply another credential or authorize access expansion.

The independent reviewer still decides acceptance. CI is regression evidence only. Historical
MODEL_PROVENANCE_UNPROVEN remains factual; the candidate inputs are immutable; all five cases
remain NOT_RUN. Live source/image remain null. Stop at the original live/model/cost human gate.
