# Case A Extension — AI Semantic Reliability and Runtime Quality Observability

## Status

**DECISION SELECTED — IMPLEMENTATION NOT STARTED — LIVE GCP NOT AUTHORIZED**

The original Case A retrieval-grounding/generalization closure remains valid.

This extension adds a new portfolio claim:

> An AI-backed service must distinguish transport/provider success, pipeline correctness, and output
> quality. A technically successful request can still be a semantic-quality failure, and a model
> provider can return a successful SDK/HTTP exchange without usable output.

The extension is therefore not "add more monitoring" and not "install Langfuse." It is an AI
service reliability case with an explicit outcome model, false-green measurement, runtime quality
signals, and bounded diagnostics.

Implementation is governed by:

.agents/work-packages/case-a-semantic-reliability-v1.yml

## 1. Why this extension exists

The repository already contains evidence that traditional success indicators can be false green.

The retained Case A VPC baseline recorded:

- fact extraction PASS;
- retrieval PASS;
- generation PASS;
- Terraform validation PASS;
- no first divergence;

while the same trace had incomplete required grounding:

- required project decision coverage: 0/1;
- required resource-type coverage: 2/4.

Generation still produced the required Terraform resources.

That means "retrieval completed + generation completed + validation passed" did not prove that the
AI/RAG result satisfied the intended semantic grounding contract.

The later Case A correction solved that specific retrieval-grounding problem and passed canonical
N=3 plus frozen holdout. The new question is operational:

> Can the service itself expose the difference between technical success and output-quality
> evidence instead of requiring a later offline forensic analysis?

## 2. Related Case C evidence

Three Case C observations deepen the same problem from different boundaries.

### 2.1 Repository-owned policy false failure — resolved

Live-validation run 36949479621 produced Terraform containing a hard-coded credential-like
literal. The repository's TerraformDraftValidator rejected it before Terraform CLI validation.

PR #176 defined, and PR #177 implemented, one bounded Vertex regeneration for this class.

PR #190 later changed the product contract: generated Terraform is an editable/reference draft, so
credential/account/placeholder-like literals alone are not a sufficient reason to reject the
draft. The dedicated sensitive-credential regeneration path was removed.

This incident must not be misreported as proven Vertex censorship. It was a repository-owned
policy boundary that was later judged over-constrained.

### 2.2 Executable correctness variance — unresolved diagnostic gap

C2 run 37016993776 used one frozen runtime/source/image identity:

- attempt 1: PASS;
- attempt 2: CORRECTNESS_FAILURE;
- terminal category: terraform_validate_configuration;
- gate stopped at 1/5.

The failed HCL was intentionally not retained. The current safe evidence therefore identifies the
failure stage but cannot reconstruct the exact invalid construct.

This is an observability problem worth fixing: safe diagnostics should reveal a useful bounded
failure class without uploading raw generated Terraform.

### 2.3 Provider content blocking is not explicitly modeled

Current VertexGenerationStage checks finishReason. MAX_TOKENS receives a specific truncation
path. Any other non-normal finish reason becomes VertexResponseFormatException, and
VertexAnalysisProvider maps that to generic provider-neutral RESPONSE_FORMAT.

That collapses different conditions:

- actual malformed/invalid response;
- provider content/safety blocking;
- other abnormal provider stop reasons.

A provider content block may occur after a successful request exchange but still yield no usable
output. That must be represented independently from HTTP/SDK transport success.

No claim is made that the historical Case C sensitive-credential incident was a Vertex safety
block. The two failure classes are explicitly separated.

## 3. Outcome model

The selected design uses four outcome layers.

### Layer 1 — provider transport/completion

Question:

> Did the provider exchange produce a usable completion?

Bounded outcome vocabulary should distinguish at least:

- normal_stop
- output_truncated
- content_blocked
- empty_response
- provider_timeout
- provider_rate_limited
- provider_error

The exact provider-specific finish reasons remain adapter details. Generic application code should
receive a provider-neutral classification.

A successful HTTP/SDK exchange does not imply normal_stop.

### Layer 2 — pipeline correctness

Question:

> Did the repository-owned analysis pipeline complete its required technical contracts?

Existing boundaries remain authoritative:

- source read;
- fact extraction;
- retrieval execution;
- generation response contract;
- required-grounding presence contract;
- generated resource/provider contract;
- structural Terraform draft validation;
- Terraform CLI executable validation;
- durable result finalization.

This layer answers technical correctness, not semantic truth.

### Layer 3 — runtime quality signal

Question:

> For an unlabeled real service request, do the observable signals indicate a healthy or degraded
> output?

This signal is deliberately not called ground truth.

Required states:

- SIGNAL_OK
- DEGRADED
- UNKNOWN
- NOT_APPLICABLE

Candidate observable evidence includes:

- extracted architecture resource types;
- selected reference resource-type coverage;
- selected reference count/authority/risk metadata;
- generated resource types;
- generation warnings;
- structural/executable validation result;
- provider completion reason.

The exact rule must be deterministic and versioned.

A successful AnalysisJob may therefore be SUCCEEDED while separately exposing qualitySignal =
DEGRADED when the technical result is usable but observable quality evidence is incomplete.

The job must not silently look fully healthy in that state.

### Layer 4 — labeled semantic quality

Question:

> Against a frozen evaluation case with expected truth, did the output meet the semantic contract?

This layer is available only for labeled evaluation datasets.

For positive architecture fixtures, semantic success requires:

- expected input classification;
- complete required project-decision grounding;
- complete required resource-type grounding;
- complete required generated-resource coverage;
- zero forbidden generated resources;
- accepted validation result.

For negative controls, semantic success requires:

- expected rejected/non-architecture classification;
- no Terraform output.

Do not infer this layer for arbitrary user input.

## 4. False-green contract

For labeled evaluation:

technical_success = true

means the existing accepted technical stage/validation predicate passed.

semantic_success = true

means the frozen expected semantic contract passed.

false_green = technical_success && !semantic_success

The exact technical-success predicate must be derived from the current evaluation schema so the
extension does not rewrite history.

The first required deliverable is a machine-readable 2x2 outcome matrix:

| | semantic success | semantic failure |
|---|---:|---:|
| technical success | true positive success | **false green** |
| technical failure | technical failure | combined failure |

Negative controls must be represented explicitly rather than omitted.

## 5. Runtime quality signal design

The runtime signal must be useful but modest in claim.

### Resource grounding signal

When architecture facts contain concrete AWS provider resource types, compare them with the
resource-type metadata represented in the final selected references.

Record bounded counts:

- observed fact resource-type total;
- reference-covered resource-type total;
- missing coverage count.

Do not emit resource names as metric labels.

If the fact resource set is non-empty and selected reference coverage is incomplete, the runtime
quality signal may become DEGRADED.

If facts do not provide a meaningful resource set, the signal should become UNKNOWN, not PASS.

### Generated-output signal

A generated architecture output cannot be SIGNAL_OK unless existing structural/provider/executable
contracts pass.

Generated-resource comparison may contribute to the runtime signal only where the relationship to
the observed facts is technically justified. The implementation must not assume every extracted
resource type must always appear as a top-level generated resource.

### Persistence and API

Preferred direction:

- persist a bounded quality-signal status;
- persist a bounded reason code;
- persist a quality-contract version;
- expose them in the existing AnalysisJob result API;
- preserve backward compatibility.

This gives post-run evidence even after process restart and prevents quality state from existing
only as ephemeral metrics.

Any DB migration must be additive.

## 6. Provider partial-failure taxonomy

The Vertex adapter currently has enough response metadata to distinguish abnormal completion from
normal completion.

Selected direction:

- introduce a provider-neutral content-blocked failure reason;
- map known provider content/safety stop reasons to that category;
- preserve OUTPUT_TRUNCATED separately;
- preserve malformed/invalid response as RESPONSE_FORMAT;
- preserve empty response as a bounded separate reason if the API contract can support it cleanly.

Observability must expose the bounded provider outcome without prompts, response text, safety
payloads, image bytes, or user-specific labels.

Provider safety settings must not be weakened merely to obtain a passing test.

Deterministic tests should construct blocked/empty provider responses. A live intentionally unsafe
prompt is not required.

## 7. Terraform executable diagnostic taxonomy

The current terminal category terraform_validate_configuration is too coarse to explain the
remaining Case C variance.

The selected direction is not to retain failed HCL.

Instead, inspect Terraform's structured JSON diagnostic output and preserve only bounded derived
metadata where safe, for example categories such as:

- missing required argument;
- unsupported argument;
- invalid reference;
- invalid function argument;
- invalid value/type;
- provider configuration/closure;
- syntax/configuration class;
- unknown/other.

The exact taxonomy must follow actual Terraform JSON diagnostics observed in tests; do not invent
categories that cannot be derived reliably.

Allowed evidence:

- bounded diagnostic category;
- error/warning count;
- optionally a stable diagnostic fingerprint derived from sanitized bounded fields.

Forbidden evidence:

- raw generated HCL;
- secret values;
- complete CLI output;
- arbitrary diagnostic detail;
- prompt/reference content.

The acceptance question is:

> If run 37016993776 happened again, would the retained evidence identify a technically useful
> failure class without exposing the generated file?

## 8. Observability backend decision

A new observability product is eligible, not mandatory.

Compare three candidates.

### A. Repository-native Micrometer + evaluation artifacts

Advantages:

- already integrated into Spring Boot;
- low-cardinality application metrics exist;
- existing job/source correlation;
- lowest operational cost.

Potential gap:

- model-call trace/score exploration may remain fragmented across logs and evaluation artifacts.

### B. Langfuse

Evaluate whether it materially adds:

- LLM/model-call traces;
- prompt/config identity;
- evaluation score attachment;
- model latency/token/cost visibility;
- release/job correlation;
- useful investigation workflow.

Also evaluate:

- Java integration path;
- payload privacy/control;
- self-hosted or managed cost;
- another persistence/service dependency;
- whether existing artifacts already answer the same questions.

### C. OpenTelemetry + existing metrics

Evaluate whether standardized tracing materially improves:

- async request/job propagation;
- model/retrieval/storage span correlation;
- release-level RCA.

Do not select it solely to populate trace_id.

### Decision rule

Select a new product only if the implemented layered outcome model leaves a demonstrated
trace/score/correlation question that repository-native evidence cannot answer cleanly.

The comparison itself is required; adoption is not.

## 9. Implementation phases

### A7-1 — false-green measurement

Add technical/semantic/false-green fields to the existing evaluation output or derived report.

Re-score preserved before-state and current after-state.

Required evidence:

- historical VPC false green is representable;
- final canonical N=3 and holdout produce the expected after-state;
- existing Case A acceptance metrics remain unchanged.

### A7-2 — provider partial-failure semantics

Implement and test bounded provider completion/outcome classification.

No prompt/model/retrieval change.

### A7-3 — runtime quality signal

Add deterministic runtime signal calculation, bounded persistence/API exposure, metrics, and logs.

No labeled-truth claim for arbitrary user input.

### A7-4 — executable diagnostics

Add safe structured Terraform diagnostic classification.

Do not preserve raw failed generated Terraform.

### A7-5 — observability product decision

Use evidence from A7-1 through A7-4 to decide whether repository-native observability is enough or
whether Langfuse/OpenTelemetry adds material value.

Any new product requires explicit user approval before dependency/runtime adoption.

### A7-6 — representative live validation

If repository-only evidence cannot prove the service-level claim, recreate the existing GCP
representative runtime.

Do not invent a second architecture.

Live prerequisites:

- fresh project/billing/credit/quota/model-access check;
- reviewed bootstrap recreation;
- exact source/image identity;
- existing GKE/OpenSearch + Vertex + MariaDB VM/PD + GCS + Secret Manager/Secret Sync + Artifact
  Registry topology unless a separate architecture decision changes it.

Minimum live evidence:

1. authenticated positive architecture request;
2. terminal AnalysisJob/result read-back;
3. runtime quality signal visible through API and metrics/logs;
4. authenticated negative-control request with no Terraform output;
5. exact release/config identity;
6. no rerun-until-lucky behavior.

A naturally occurring provider content block or Terraform executable failure must be retained as
evidence. It must not be automatically discarded.

After live evidence, cloud teardown/cost closure is mandatory again.

### A7-7 — integrated closure

Close only when the project can explain:

- why HTTP/job success is insufficient;
- what false green means;
- which signals are technical vs semantic;
- what can be measured online without labels;
- what requires labeled evaluation;
- how provider content blocking is distinguished from malformed output;
- how executable failures are diagnosed safely;
- whether/why Langfuse or OpenTelemetry was selected or rejected;
- live service evidence if it was required;
- residual risks.

## 10. Acceptance boundary

The extension is successful if it produces a technically defensible answer to this question:

> How does Terraformers know whether the AI service is healthy when the request itself succeeded?

The answer must not be merely:

- HTTP 200;
- AnalysisJob SUCCEEDED;
- provider call did not throw;
- Prometheus is reachable;
- Terraform text is non-empty.

It must show a layered outcome contract and measured evidence.

## 11. Explicit non-claims

Even after this extension, do not claim:

- semantic ground truth for arbitrary unlabeled user inputs;
- perfect hallucination detection;
- statistically strong long-run reliability from a handful of runs;
- automatic production-safe Terraform deployment;
- exactly-once model invocation;
- safety-filter bypass;
- complete production SLOs unless separately measured.

## 12. Stop rule

Do not continue merely to add AI tooling.

Stop when:

- false-green before/after is measurable;
- runtime quality signal is durable and observable;
- provider partial failures are explicitly classified;
- executable correctness diagnostics are actionable and bounded;
- observability backend decision is evidence-backed;
- the selected service-level claim has sufficient live or deterministic evidence;
- final portfolio documents accurately reflect the result.

A new GCP live environment is justified only for the service-level validation above, not for
repeating already-proven Case A canonical closure.
