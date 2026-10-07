# Narrow PT-6R2 finalization/fencing authority

USER explicitly authorized `AUTHORIZE_NARROW_PT6R2_FINALIZATION_FENCING_CORRECTION` in the current conversation after independent review 6036430154 at c51628853eef8fd22e5fa1549cbe33ddf775995f.

Continue the existing Work Package / PR #254 / bound base 406981a8c629a02503f5660453dcc7921b8d5177. Move external result-object write outside the job pessimistic transaction. Re-enter a short durable finalization transaction after write; recheck RUNNING status, generation, matching intent and original eight-minute accepted age before SUCCEEDED. Rejected/late writes remain cleanup-accountable through existing intent/cleanup. Publish no external success before durable commit. Reuse lightweight deadline sweep for PENDING/RUNNING, enforce one durable attempt at claim/reclaim, and establish bounded transition delta under an operating DB/service using deterministic tests. Apply approved policy D request budgets / SDK attempts 1 / statuses [], preserving MAX_TOKENS fallback and single closure/repair.

No general GCS/OpenSearch/MariaDB/network timeout redesign, new queue/job technology, SDK upgrade, prompt/model/retrieval change, live GCP/OpenSearch/model work, final official inputs or PT-7. Stop for independent review after implementation/validation; no self-acceptance or merge.
