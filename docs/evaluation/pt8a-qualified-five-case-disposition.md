# PT-8A — Qualified Five-Case Observation Disposition (2026-10-11)

**Status:** USER-APPROVED QUALIFIED CLOSURE / **NOT** ORIGINAL OFFICIAL PT-8A ACCEPTANCE PASS. The user approved the **scope and residuals**, not a fabricated ten-dimension or five-case semantic score.

**Identity:** This account of the observations was prepared from user-provided owner-scoped A/B review JSON and the N1–N3 persisted Job/Diagnostics files in the preceding interactive review. **Raw A/B/N1–N3 diagnostic JSONs are not committed here**: original user-held primary evidence remains the source, and this sanitized summary must not masquerade as the signed original artifact or independently rerun evidence. Cite the Job IDs/hash when locating originals. Old official A–E workflow artifacts and reviews are immutable and not replaced by this cohort.

| Observation | Project ID | AnalysisJob ID | Input SHA-256 | Job / CLI | Quality |
|---|---:|---|---|---|---|
| A — Serverless API | 21 | `640b0c25-329d-47bb-8310-f6861224a2cc` | `0a6a59273dc5a70ab10415328a53427291c2b08c25a94f088bb874a607946007` | SUCCEEDED / Terraform CLI PASS | UNKNOWN |
| B — Multi-AZ VPC fleet | 22 | `cf32fada-b137-4250-ab0a-e7640c8a3124` | `d14c4ba606cd3edf26abc93721577293d1378bc3f3903a0d678af020295b162b` | SUCCEEDED / PASS | UNKNOWN |
| N1 — ALB/EC2-ASG/RDS | 24 | `fdea13a6-44a0-4b05-8f49-3dc19d5b79be` | `06c9a3d7b7c173db2046417d8851e034fedc3800dfe6a0d4bc86a0ea524c2fe4` | SUCCEEDED / PASS | UNKNOWN |
| N2 — ALB/ECS/Aurora | 27 | `dc89d5ff-a3ba-48e2-b081-6da0bfd29500` | `2fa0e58a600ac3d334c1ce6736a14c44f67f8b7a72f816e0775c1f1f23efdf2c` | SUCCEEDED / PASS | UNKNOWN |
| N3 — Route53/CloudFront/S3 | 28 | `d5a7841c-9a45-4c18-9736-2a8c1364fa91` | `8250e2ccbc7d6a3900b9d51de82f05c1d0b6437eeab1c3435274663e92f62599` | SUCCEEDED / PASS | UNKNOWN |

**Relation to the historical official image set:** The supplementary A/B input SHA-256 values above match the pinned original official **A and B** candidate bytes. Their historical negative/positive outcomes and later five-case observations can be reviewed as **same-image cross-revision evidence**, but not necessarily identical runtime/model/corpus/configuration conditions. N1/N2/N3 have **different source images** and demonstrate additional breadth, not replacement observations of official C/D/E. The two sets' job IDs, image SHA, code revision and acceptance method must not be collapsed.

**Candidate current portfolio upgrade (not an accepted semantic score):** [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274) corrected **confirmed loss of structured extracted facts at the initial generation boundary** and removed an overly restrictive first semantic retrieval type filter. [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277) refined icon/service presence vs CREATE intent, directed wiring guidance, all-aliased AWS provider coherence and safe acceptance of the single Repair output. Observed same-image A now includes the previously missing API Gateway→Cognito authorizer wiring; **N2** visibly demonstrates Repair's final HCL correction; the five cases establish a bounded newer CLI-positive observation. For *these* engineering claims the latest evidence is more directly relevant than old Case A's v3-only retrieval closure, whereas older controlled v3 holdout, B5 crash recovery and Case C pod-replacement evidence remain stronger for other claims.

**Portfolio policy:** Preserve raw evidence/old failures **without freezing the old Case A/B/C storylines**. Rank the newest defensible mechanisms/observations as lead material and use historical closures as true baseline or complementary proof only. Final grouping and number of stories are determined by depth and evidence, not fixed to three. See [Portfolio Final Completion](../plans/active/portfolio-final-completion.md).

**Independent review boundaries:**

- B/N1/N2/N3 major service placement and directed architecture roughly matched the practical images; **A** restored the previously missing API Gateway→Cognito authorizer linkage but retains meaningful domain/authorization deployment gaps. Not a full A PASS.
- N2 Repair changed the initial malformed/incomplete Terraform into a technically valid draft. N1/N3 initial and final drafts were identical. This is **one observed Repair success**, not a general success rate.
- N3 Route 53 alias target plus CloudFront default certificate and lack of custom alias/certificate is an internally chosen HCL configuration-consistency concern. The image cannot determine actual domain, hosted-zone ownership or certificate ARN. **No CloudFront-specific gold-answer adjustment authorized**.
- `knowledgeStatus=COMPLETE` reflects official resource documentation coverage, not proved input-facts truth. `qualityStatus=UNKNOWN` and `projectDecisionStatus=UNKNOWN` stay UNKNOWN. CLI PASS is not semantic acceptance nor AWS deployment proof.
- Supplemental first N2/N3 uploads (Projects 25/26) had `application/octet-stream`, failed at facts stage, and are preserved as input MIME failure evidence; corrected submissions had explicit image/png and image/jpeg without modifying original image hashes.
- Historically identified facts→initial-generation dataflow defect was corrected by [PR #274](https://github.com/siamese-lang/terraformers-platform/pull/274); intent/repair syntax bounds corrected in [PR #277](https://github.com/siamese-lang/terraformers-platform/pull/277). Deterministic code tests support propagation; sample A supports actual authorizer wiring. No claim that every relationship or retrieved document is used correctly.

**Legacy official frozen A–E:** Remain separately consumed, negative or NOT_RUN as recorded in original [official procedure](product-trust-pt-8a-official-acceptance-procedure.md) and [B–E cause evidence](product-trust-pt-8a-b-to-e-cause-diagnostics.md). The new N1–N3 were **not** a replacement of frozen official C–E; no regrading or resetting attempts/budgets.

**Program boundary:** This USER-qualified observation closure allows evidence and portfolio narrative reconciliation but **does not** satisfy the approved PT-8B dependency `INDEPENDENT_PT8A_ACCEPTANCE_REQUIRED`, authorize new live tests or automatically activate PT-9. The next read-only decision and remaining tasks are fixed in [Portfolio Completion Plan](../plans/active/portfolio-final-completion.md).
