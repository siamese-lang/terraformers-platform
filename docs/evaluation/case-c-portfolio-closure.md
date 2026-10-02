# Case C Final Portfolio Closure — Cloud Runtime Measurement Guardrails & Immutable Delivery

## Status

**PORTFOLIO-CLOSED — RESIDUAL RISKS ACCEPTED**

Closure decision:
[Case C Portfolio Sufficiency Closure Decision](../plans/active/case-c-portfolio-sufficiency-decision.md).

## Portfolio question

How should an operations team measure capacity when the workload includes stochastic AI generation
and the integrated request path can fail for correctness reasons before infrastructure saturation?

The project initially attempted to measure GKE/runtime capacity directly. The first capacity run
proved that this was the wrong order of operations.

## Before-state

Capacity run `36957682821` stopped at concurrency 1:

- two requests succeeded and a later request failed;
- no valid queue/CPU/OpenSearch saturation point had been established;
- the failing path was Terraform executable correctness rather than measured capacity.

A naïve continuation would have increased concurrency, nodes, replicas, or executor capacity while
the workload itself was not yet a stable correctness prerequisite.

## Root mechanism work

The architecture audit found that several repository rules had been conflated:

- RAG/retrieval evidence was acting as a generated-resource allowlist;
- example/editable Terraform was rejected for policy-like literal content;
- a sensitive-credential detector could force a second model generation;
- Terraform draft regex checks duplicated responsibilities owned by Terraform itself.

Those rules could create false failures unrelated to whether the resulting draft was valid
Terraform for the exact bundled provider.

The selected boundary became:

`retrieval guidance → generation → exact AWS provider resource existence → terraform init/validate`

with retrieval context advisory rather than authoritative resource eligibility.

## Implementation and delivery evidence

PR #190 implemented the simplified boundary and passed final static verification.

A new backend image was then published from exact source
`b420291fa1534184d2a260883df718cc96511505` in run `37015159218`:

- immutable digest:
  `sha256:0db599d2487a3f7850bd0b5e454fe04d9cb234ae840202c7ad01f1a147478419`;
- source revision embedded in the image;
- no mutable `latest` publication.

Run `37015932695` rolled out that exact digest:

- previous digest checked before mutation;
- target digest provenance verified;
- Ready replicas = 1;
- Available replicas = 1;
- embedded source SHA matched;
- backend health = UP.

## Same-scenario correctness gate

Run `37016993776` repeated the frozen integrated request under the same source/image/fixture and
concurrency 1.

Attempt 1 passed:

- terminal `SUCCEEDED`;
- result object persisted;
- generated Terraform readable and non-empty.

Attempt 2 failed:

- terminal `FAILED`;
- classification `CORRECTNESS_FAILURE`;
- terminal category `terraform_validate_configuration`;
- accepted attempts = `1/5`;
- automatic whole-gate rerun = `false`.

This is stronger evidence than a rerun-until-green result. The corrected system no longer failed on
the previous request-schema/sensitive policy boundary, but it still demonstrated real stochastic
Terraform executable-validity variance.

## Engineering decision

The project stops here for portfolio purposes.

The correct operational decision is not to keep adding diagnostics and retries until the same fixture
eventually reaches 5/5. It is to state that capacity results are not trustworthy while the
correctness prerequisite is variable, preserve that limitation, and avoid pretending that
infrastructure tuning solved it.

The useful engineering material is therefore:

- detecting an invalid measurement premise;
- separating RAG guidance, product policy, provider eligibility, and Terraform correctness;
- using exact source/digest provenance for the deployed revision;
- making live gates fail closed rather than rerun until lucky;
- refusing to attribute correctness variance to infrastructure capacity;
- preserving unresolved risk instead of expanding the project indefinitely.

## Alternatives rejected

- **Continue capacity testing anyway** — rejected because correctness failures contaminate capacity
  attribution.
- **Remove Terraform CLI validation** — rejected because it would recreate the previous false
  success mode where invalid drafts were stored as successful output.
- **Keep broadening RAG corpus until the gate passes** — rejected because the observed failure is not
  currently evidence of retrieval failure.
- **Add automatic regeneration on any Terraform validation failure** — rejected because it hides
  failure frequency and can become a rerun-until-lucky mechanism.
- **Continue implementing every C3–C8 item before portfolio closure** — rejected because it optimizes
  for system completeness rather than the repository's primary success criterion.

## Residual risks and deferred engineering

Retained but not required for this portfolio case:

- AI-generated Terraform can still fail `terraform validate` on repeated identical scenarios;
- capacity saturation point is not established;
- executor-aware load-harness semantics are not repaired;
- repository declarative image and live image convergence remains follow-up work;
- single-replica rollout availability has not been optimized;
- integrated-path readiness and faulty-release rollback remain unproven;
- full production unattended Terraform apply is outside the product claim.

## Case A compatibility

This result does not invalidate Case A.

Case A closed on retrieval-grounding coverage, negative-control behavior, repeated canonical
evaluation, and a frozen holdout. Its final closure explicitly did **not** claim that Terraform
structural validation proved deployment correctness or that generated Terraform had been
plan/applied.

The Case C failure adds a stricter downstream executable-validity observation. It does not negate the
retrieval-grounding measurements that Case A actually claimed.

## Portfolio-ready summary

> A capacity test on the GKE target failed before saturation because the AI-generated Terraform path
> was not functionally stable. Instead of tuning infrastructure against a contaminated workload, I
> separated retrieval guidance, repository policy, provider-resource eligibility, and Terraform CLI
> correctness. I removed false policy gates, published and rolled out an immutable source-bound
> backend image, and repeated the same integrated request under a fixed runtime identity. The first
> attempt passed and the second failed real `terraform validate`, proving that remaining variance
> belonged to generated-code correctness rather than measured GKE capacity. I therefore stopped the
> capacity experiment, preserved the residual risk, and avoided claiming a bottleneck the evidence
> could not support.
