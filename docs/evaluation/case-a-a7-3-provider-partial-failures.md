# Case A A7-3 — provider partial failures

Status: **IMPLEMENTED_AWAITING_REVIEW**

## Outcome and taxonomy

A successful transport exchange is no longer treated as proof of usable AI output. The existing
`AnalysisProviderFailureReason` contract now distinguishes `OUTPUT_TRUNCATED`, `CONTENT_BLOCKED`,
`EMPTY_RESPONSE`, `RATE_LIMITED`, `PROVIDER_ERROR`, `RESPONSE_FORMAT`, and `INPUT_REJECTED`.
Timeout remains the distinct `AnalysisProviderTimeoutException` boundary so the established retry
ownership contract is unchanged.

## Deterministic provider mappings

Vertex maps `MAX_TOKENS` to truncation. The installed Google Gen AI SDK's explicit `SAFETY`,
`RECITATION`, `BLOCKLIST`, `PROHIBITED_CONTENT`, `SPII`, `IMAGE_SAFETY`,
`IMAGE_PROHIBITED_CONTENT`, and `IMAGE_RECITATION` finish reasons map to content blocked. `STOP` and
unspecified completion continue normally; other abnormal finish reasons remain response-format
failures rather than being mislabeled as censorship. Blank response text is empty response. An
explicit HTTP 429 is rate limited, timeout-shaped SDK failures retain the timeout boundary, and
other invocation failures are provider errors. Non-empty malformed structured output remains a
response-format failure. Fact extraction retains the same explicit finish-reason boundary and
bounded evidence.

Bedrock maps Anthropic `max_tokens` to truncation, missing or blank successful content to empty
response, explicit AWS throttling/429 exceptions to rate limited, SDK timeouts to timeout, and
other invocation failures to provider error. Non-empty malformed tagged or JSON content remains a
response-format failure. The current Bedrock Claude response contract used here exposes no
unambiguous content-block/refusal stop reason, so A7-3 deliberately does not manufacture one.

## Evaluation and observability

New traces use `OUTPUT_TRUNCATED`, `PROVIDER_CONTENT_BLOCKED`, `PROVIDER_EMPTY_RESPONSE`,
`PROVIDER_TIMEOUT`, `PROVIDER_RATE_LIMITED`, `PROVIDER_ERROR`, and `RESPONSE_FORMAT`. Historical
`PROVIDER_RUNTIME` artifacts, including run `36379633596`, are unchanged and remain insufficiently
specific. Run `36949479621` was repository validation, not provider censorship.

Metrics use only stable low-cardinality categories: `truncated_output`,
`provider_content_blocked`, `provider_empty_response`, `timeout`, `provider_rate_limited`,
`provider_error`, `response_format`, and `rejected_input`. No prompt, image, raw provider payload,
generated HCL, identifiers, or exception messages are recorded as labels or evaluation details.

## Deterministic evidence and unchanged boundaries

Unit coverage injects explicit finish reasons, empty bodies, malformed non-empty bodies, timeout,
throttling, and generic runtime failures without a live model call or unsafe prompt. It also proves
that timeout remains retryable while a rate-limited provider failure is terminal under the current
policy. Input classification rejection remains separate from provider content blocking, and the
existing bounded truncation retry is preserved.

A7-3 adds no database migration, `AnalysisJob` field, API field, evaluation artifact mutation, or
durable quality state. Durable runtime quality persistence remains deferred to separately approved
A7-4 work.
