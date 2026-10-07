# PT-3 — expose missing authorization on generated S3 origins

Status: repository implementation / deterministic verification; independent acceptance and
explicit merge pending. No live/model/GCP evaluation or rollout is authorized or performed.

USER approved PT-3 and the one Program amendment after independent review
[6028079078](https://github.com/siamese-lang/terraformers-platform/pull/246#issuecomment-6028079078).
PT-2 is [final INCOMPLETE evidence](product-trust-pt-2-final-disposition.md), not a successful
ten-case benchmark. PT-3 is bound once to main `5aa275e666d45f4181a3ca2ebee6740f0700a75b` by the
[Work Package](../../.agents/work-packages/product-trust-pt-3-origin-authorization-v1.yml).

## Supported mechanism and impact

Original job `2337e6bd-1f70-4382-a515-b4cdb9cba7d9` produced
[main.tf](../evidence/product-trust-pt-2/case-01/main.tf), SHA
`962ea2b714bee2981b0a3993046dc5a65eeefb6fd05d29f4e7026f6665e16971`.
Lines 7–9 create the bucket, 11–17 create the OAC, and 24–28 bind the signed regional S3 origin.
The full HCL has no supplied bucket read grant. Same-job
[logs](../evidence/product-trust-pt-2/case-01/correlated-backend-logs.json) establish draft and
executable validation PASS; [terminal evidence](../evidence/product-trust-pt-2/case-01/terminal-job.json)
establishes persisted UNKNOWN quality with empty reasons. CloudFront OAC request signing does not
itself grant access to a newly created S3 bucket. The official
[CloudFront S3 origin guide](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/private-content-restricting-access-to-s3.html)
describes granting `cloudfront.amazonaws.com` access, including `s3:GetObject`. No AWS apply,
observed origin HTTP error, or exact deployment outcome is claimed.

The earliest **supported** causal gap is the final-output contract, not an inferred model stage:

- `VertexGroundedGenerationOrchestrator` closes evidence for types already present in generated
  Terraform. An omitted authorization resource cannot enter that generated-type inventory.
- Its closure/repair is bounded and type-grounded; selected docs do not establish every relationship.
- `GeneratedTerraformContractInspector` previously checked AWS provider membership/modules only.
- `EvidenceQualityAssessor` previously checked type knowledge/evidence and project decisions only.
- `TerraformCliValidator` checks init/validate structure and schema, not effective S3 read access.

Initial raw facts, first draft, repair content and provider RPC trace were not emitted by the observed
job. It is unknown whether generation or repair introduced the omission. The final persisted defect
and the absence of a deterministic check are directly observable; no upstream cause is invented.

Earlier A7-7 source run `37401380524`, artifact `11385926347`, digest
`3df24aa8744c93b5d543b466d718f33ed6f54f089e214689137b82623acd43a0`, was downloaded and inspected
without inference. Its three canonical reports retain validation outcomes 3 PASS/1 FAIL,
3 PASS/1 FAIL, and 4 PASS/0 FAIL; holdout retains 1 PASS/1 FAIL. These are historical synthetic
controls, not realistic generalization. They support preserving broad-v4 authority, schema/CLI
separation and natural failures; they do not establish CloudFront→new-S3 permission completeness.

## Minimum correction and alternatives

Extend the **existing** generated-contract inspector with a deterministic authorization-omission
check and consume it in the **existing** quality assessor. A final draft with an identifiable OAC
origin pointing to a newly declared S3 bucket and no supplied read-authorization wiring gets
`CLOUDFRONT_S3_ORIGIN_AUTHORIZATION_MISSING` and quality `DEGRADED`. Technical PASS remains an
accurate structural/schema/CLI dimension; the editable Terraform is retained.

This exposes the defect instead of silently treating complete type evidence as complete semantics.
No new evaluator, model judge, prompt rule, retrieval change, generated IAM grant, closure cycle,
repair attempt or retry is added. Current production already assesses final post-repair HCL and
persists quality reasons, so no orchestration/API/database/frontend rewrite is necessary.

The lexical block reader prevents comments, quoted examples and heredocs from supplying fake
declarations. It recognizes direct bucket references (including string interpolation), transparent
local aliases, and matching supplied bucket-name expressions. Policies may be explicit JSON,
`jsonencode`, a document data source, file/template expression or a supplied policy variable.
An explicit public-read ACL or AllUsers READ/FULL_CONTROL ACL grant is an alternate declaration.
No policy template, resource label, fixture ID or image is special-cased. Existing/data/external
origins and OAI/custom origins are outside this OAC/new-bucket omission check.

Schema validation cannot detect this semantic omission; changing its error category would be false.
Broad prompt/retrieval tuning would add nondeterminism without proving the supported boundary.
An automatic semantic repair would spend another provider call and need a separately reviewed
generation budget. The chosen correction reuses the current deterministic quality responsibility
and introduces zero provider calls.

## Limits and retained product boundaries

This is **declaration/omission detection**, not arbitrary IAM evaluation. The presence of a policy
expression is not proof that its effect/actions/principals/conditions grant access. ACL ownership,
public-access blocking, policy conditions, instance-specific count/for_each binding, remote policies,
complex computed/dynamic origins and opaque/cyclic expressions are not fully evaluated. Complex
authorization bindings may be unrecognized; the draft remains available and degradation is not a
provider rejection. This does not claim general semantic correctness or usable deployment.

Deployment-specific values can remain variables/references/placeholders with explicit wiring.
Neither supplying an opaque policy nor passing this omission check upgrades the existing runtime
boundary beyond `CONDITIONAL_ON_EXTRACTED_FACTS`; knowledge/project-decision rules are preserved.
In production, unknown project-decision applicability still yields UNKNOWN for otherwise supported
drafts. Known omission now takes precedence as DEGRADED with an explicit semantic reason.

PT-4 must address both independently confirmed presentation and waiting defects: UNKNOWN hidden
by completed-analysis presentation, and the `424846 ms` censor versus unconditional `1~3분` at
`frontend/src/pages/ProjectDetailPage.js:130`. PT-3 does not edit that UI, waiting guidance or status
model. PT-4 must also preserve the distinction between semantic degradation and processing completion.

## Deterministic evidence and next gate

The [validation record](../evidence/product-trust-pt-3/validation.json) binds exact commands, counts,
toolchain, source hashes, preserved raw identities and scope. Before correction, a controlled fully
evidenced, technical-PASS draft without authorization returned EVIDENCE_BACKED (expected regression
failure). That controlled NOT_APPLICABLE decision input is not the original production UNKNOWN
snapshot. A second test replays the untouched observed HCL with production UNKNOWN applicability
and isolated complete-evidence inputs: it now records the semantic reason/DEGRADED without changing
technical PASS. Both are offline assessments, not new product/model samples.

Regression coverage includes renamed buckets, interpolated references, multiple origins, unrelated/
empty policy, private ACL, comments/heredoc examples, valid supplied policy forms/local aliases/public
ACLs, preserved nonaffected origins, JSON/database/API reason persistence, and final post-repair
assessment without another closure/repair. Existing MAX_TOKENS compact retry and single-attempt
repair tests remain intact.

No additional PT-2 action, frozen truth/model-output tuning, model/prompt/retrieval/scorer/corpus,
IAM/GCP/runtime/infrastructure, teardown or frontend change occurred. Autonomous repairs used: 0.
Independent review must verify original PT-3 criteria and limits; CI success alone is not acceptance.
Stop at independent review / explicit merge checkpoint. New live/model evaluation requires its own
approval; PT-4 cannot start before this correction is independently accepted and merged.
