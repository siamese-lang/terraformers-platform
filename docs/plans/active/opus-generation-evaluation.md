# Opus Generation Evaluation Plan

## Status

**PLANNING ONLY — NO PRODUCTION MODEL CHANGE — NO LIVE OPUS RUN YET**

This plan separates the next model experiment from the canonical Case A retrieval evaluation so
that model behavior, retrieval behavior, and migration behavior cannot be conflated.

Authoritative starting point:

- repository main before this plan: `3fc610f1e9d601a4d5f79f281b358b783342c4ce`
- latest canonical runs: `36691519268`, `36691527069`, `36693007328`
- canonical retrieval result: 18/18 retrieval PASS, VPC project-decision coverage 3/3,
  VPC required-resource coverage 4/4 × 3, grounding gaps 0/12, negative controls 6/6
- remaining observed issue: one independent Terraform validation failure caused by generated
  placeholder/example deployment content in run `36691527069`

The frozen Case A holdout remains unrun.

## Why this experiment is separate

The current Case A evidence already shows that the selected retrieval-grounding defect is no longer
observed in the latest N=3. The remaining placeholder/example Terraform observation occurs after
successful retrieval and is therefore a generation reliability question.

The next question is deliberately narrower:

> With the image and retrieved evidence held fixed, can Claude Opus 5.5 produce a valid,
> correctly-classified Terraform draft under the same semantic generation contract?

This plan does not reinterpret a model-generation failure as a retrieval failure, and it does not
change the Case A canonical acceptance history.

## External model identity

Availability was verified against Google Cloud documentation on 2026-09-30.

- model: Claude Opus 5.5 on Google Cloud
- model ID: `claude-opus-5-5`
- launch stage: GA
- supported input: text, image, PDF
- supported output: text
- global endpoint: supported

Official model card:

`https://docs.cloud.google.com/gemini-enterprise-agent-platform/models/partner-models/claude/opus-5-5`

Before any live invocation, verify that the project can access the model and that the executing
identity has the required Vertex prediction permission. Model access or quota failure is an
environment/setup result, not a model-quality result.

## Current structural finding

The existing production path cannot safely switch to Opus by changing only
`VERTEX_GENERATION_MODEL_ID`.

Today:

```text
VertexRuntimeProperties.generationModelId
  ├─ VertexArchitectureFactsExtractor
  └─ VertexGenerationStage
```

Both fact extraction and final generation therefore share one Gemini model setting.

Also:

- `VertexGenerationStage` is implemented with the Google GenAI Gemini request API;
- `VertexAnalysisProvider` directly depends on the concrete `VertexGenerationStage`;
- the canonical live evaluation hard-codes `gemini-3.8-flash` and its fingerprint;
- the existing Case A workflow is a retrieval/canonical workflow, not a model-comparison workflow.

Therefore the Opus experiment must not be implemented by replacing the current model ID in the
production or canonical paths.

## Experiment isolation boundary

The first Opus experiment is **evaluation-only**.

It must not run:

- Vertex fact extraction;
- Gemini embedding;
- OpenSearch retrieval;
- `ReferenceEvidenceSelector`;
- Case A scoring as a retrieval experiment;
- frozen holdout;
- production `SelectedAnalysisProvider`.

Instead the experiment consumes only:

```text
frozen canonical image
+ frozen ordered ReferenceDocument IDs
+ committed corpus content
+ frozen semantic generation contract
        ↓
Claude Opus 5.5 on Google Cloud
        ↓
existing domain result shape
        ↓
existing TerraformDraftValidator
        ↓
generation-only result artifact
```

This isolates the model-generation variable.

## Frozen experiment fixture

The committed file:

`evaluation/opus-generation-v1/manifest.json`

is the source of truth for experiment inputs.

It freezes:

- six existing `terraformers-eval-v1` images;
- each image SHA-256;
- expected classification;
- exact ordered reference IDs supplied to generation.

The ordered reference IDs come from canonical run `36691519268` on source
`3fc610f1e9d601a4d5f79f281b358b783342c4ce`.

That run was chosen before implementation because:

- retrieval passed for all six cases;
- all four positive Terraform validations passed;
- both negative controls passed;
- VPC required decision and resource coverage passed.

The fixture is not regenerated after seeing Opus results.

Changing the fixture requires a new version, not an edit to `opus-generation-v1`.

## Prompt contract

The Opus prompt must preserve the **semantic intent** of the current
`VertexPromptBuilder` at the frozen source commit.

For the first experiment, do not strengthen or weaken the generation policy in response to the
placeholder observation.

In particular, do not add a new anti-placeholder instruction before the first Opus result. Doing so
would change both model and prompt at once.

The Claude request may use Claude-native structured output / JSON schema mechanics, but the returned
domain fields must remain equivalent to:

- inputType
- classificationConfidence
- classificationReason
- summary
- components
- relationships
- warnings
- terraformCode

Adapter-specific API syntax is not part of the semantic experiment variable.

## Phase O0 — planning and fixture freeze

Deliverables:

- this decision/experiment plan;
- `evaluation/opus-generation-v1/manifest.json`.

No Java code.
No workflow change.
No model call.

Exit criteria:

- fixture source run and SHA are explicit;
- Case A canonical and holdout boundaries are explicit;
- one-run stopping rule is explicit.

## Phase O1 — evaluation-only Opus harness

Create the smallest non-production harness required to execute the frozen fixture.

Preferred shape:

```text
OpusGenerationEvaluationLauncher
  ├─ load opus-generation-v1 manifest
  ├─ load terraformers-eval-v1 image bytes
  ├─ verify image SHA-256
  ├─ load terraformers-reference-v3 documents by exact frozen IDs
  ├─ preserve manifest order
  ├─ invoke evaluation-only Claude Opus generation stage
  ├─ parse to AnalysisGenerationResult
  ├─ score classification / generated resource expectations
  ├─ run TerraformDraftValidator for architecture cases
  └─ write one machine-readable artifact
```

The Claude generation stage may implement `AnalysisGenerationStage`, but it must not be registered
as the production Spring implementation in O1.

Do not modify:

- `SelectedAnalysisProvider`;
- `AnalysisProviderType`;
- `VertexAnalysisProvider`;
- `VertexArchitectureFactsExtractor`;
- `VertexEmbeddingProvider`;
- retrieval selector/query code;
- canonical evaluation configuration;
- existing Case A workflow;
- corpus or canonical datasets.

This deliberately postpones production refactoring until model evidence exists.

## O1 identity

The output must record at least:

- schema version;
- source repository SHA;
- fixture manifest SHA-256;
- dataset version;
- corpus version;
- model ID `claude-opus-5-5`;
- endpoint/location identity;
- max output tokens;
- prompt-contract identity or hash;
- case ID;
- expected and observed classification;
- supplied ordered reference IDs;
- generation latency;
- output token count if reliably exposed;
- generated Terraform resource types;
- Terraform validation status and reason;
- whether output contains validator-detected placeholder/example language;
- first failure category for the generation-only experiment.

Do not label this artifact as a Case A baseline or holdout.

Suggested schema name:

`opus-generation-evaluation-v1`

Suggested artifact prefix:

`opus-generation-evaluation-`

## Phase O2 — one bounded live run

Run exactly one six-case `opus-generation-v1` experiment.

No quality-failure rerun is allowed.

A rerun is allowed only when the first attempt produced no usable model result because of a verified
setup failure such as:

- model API not enabled;
- model access not granted;
- IAM denial;
- quota unavailable before inference;
- transport/setup failure unrelated to returned model content.

If any case receives a model response and fails quality/validation, that result counts.

Do not tune the prompt and rerun in O2.

## O2 observations

For the four positive architecture cases record:

- observed classification;
- required generated-resource coverage from the existing dataset contract;
- forbidden generated-resource count;
- Terraform structural validation;
- placeholder/example validation reason, if any.

For the two negative controls record:

- observed classification;
- whether any Terraform was emitted;
- format/rejection result.

Also record latency and exposed token usage, but do not invent a cost or latency threshold.

## Decision rule after O2

One six-case run is a bounded diagnostic, not a reliability estimate.

### Opus is a viable replacement candidate when

- all six classifications match the existing dataset expectations;
- all four positive cases pass the existing TerraformDraftValidator;
- no positive case emits validator-detected placeholder/example output;
- required generated-resource expectations are met for all four positives;
- neither negative control emits Terraform.

This result authorizes a **separate decision about production integration**.
It does not automatically change production.

### Opus is not yet a clean replacement candidate when

any returned model result violates one of the above conditions.

Record the failure class and stop.

Do not prompt-tune, add retries, change the validator, or modify retrieval in the same experiment.

### Inconclusive

If model access/setup prevents usable inference before model output, fix only that access problem and
rerun the same frozen experiment.

## Phase O3 — production integration decision, only if authorized

O3 is not part of the first implementation.

If O2 justifies adoption, then separately design the production boundary.

At that point evaluate the previously identified structure debt:

1. separate fact-extraction model identity from final-generation model identity;
2. make the production orchestration depend on `AnalysisGenerationStage` rather than the concrete
   Gemini stage where appropriate;
3. use provider-neutral generation exception types consistently;
4. preserve Gemini fact extraction and Gemini embedding unless separate evidence justifies changing
   them;
5. add Claude generation routing without reinterpreting `AnalysisProviderType` ambiguously.

Do not perform these refactors before O2 merely to make the experiment possible.

## Relationship to Case A

Case A retrieval evidence and the Opus generation experiment are separate evidence streams.

The Opus experiment must not:

- overwrite canonical Case A results;
- revise prior Case A run outcomes;
- consume the frozen Case A holdout;
- cause a new canonical N=3 automatically;
- claim that Opus fixes retrieval.

After the model experiment, Case A closure/holdout sequencing must be decided explicitly.

## Explicit non-goals

This plan does not authorize:

- Sonnet/Haiku/Fable comparison;
- Bedrock reintroduction;
- prompt tuning;
- validation-aware retry;
- model fallback;
- multi-model voting;
- LLM judge/reranker;
- corpus changes;
- retrieval changes;
- K changes;
- embedding changes;
- fact-extraction changes;
- canonical dataset edits;
- holdout execution;
- production deployment.

## Stop criteria

The immediate work stops after:

1. O0 planning/fixture PR is reviewed and merged;
2. one bounded O1 implementation PR is reviewed and merged;
3. one O2 live Opus run is executed and its artifact is reviewed.

No automatic follow-on PR is authorized.

The next action after O2 must be a human decision based on the recorded result.

## O1 implementation checkpoint — 2026-09-30

O1 adds an evaluation-only Java harness under
`backend/src/main/java/com/terraformers/modernization/evaluation/opus/`. The standalone
`OpusGenerationEvaluationLauncher` validates the frozen manifest, canonical dataset image bytes and
checksums, exact ordered v3 corpus documents, and fixed model/location identity before using an
ADC-backed `ClaudeVertexClient`. It preserves `VertexPromptBuilder` prompt semantics, removes only
Claude-unsupported schema bounds, uses Claude structured output, permits one compact truncation
retry, reuses `VertexResponseParser` semantics, runs `TerraformDraftValidator`, and writes an
`opus-generation-evaluation-v1` artifact with deterministic generation-only failure precedence.

The dedicated manual launcher is `.github/workflows/gcp-opus-generation-evaluation.yml`. It is
`workflow_dispatch`-only, requires `RUN_OPUS_GENERATION_EVALUATION`, runs an ephemeral pod as the
existing `terraformers-backend` Kubernetes ServiceAccount, and neither checks OpenSearch nor creates
cloud resources or IAM bindings.

O1 validation commands:

- `mvn -q -f backend/pom.xml -Dtest='*OpusGeneration*,VertexPromptBuilderTest,VertexResponseParserTest,TerraformDraftValidatorTest' test`
- `mvn -q -f backend/pom.xml test`
- `git diff --check`

No live Claude/Opus model call or evaluation workflow dispatch occurred during O1. Production
provider routing and the existing Case A workflow are unchanged. O2 remains a separate,
explicitly-authorized one-run activity; this checkpoint does not authorize or begin O2.
