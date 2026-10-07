# PT-6R2 — provider terminality and latency-budget decision

**HUMAN_REQUIRED: PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION.** Audit preparation only;
PT-6R2 is not complete. No timeout/retry implementation or policy has been selected.
Execution base, read once from GitHub main: `406981a8c629a02503f5660453dcc7921b8d5177`.
[Work Package](../../.agents/work-packages/product-trust-pt-6r2-provider-terminality-v1.yml)
stays on one branch/PR through the later USER decision and bounded implementation. No rebind/rebase.
This advances backend reliability: Case A's provider boundary and Case B's durable job terminality.

## Decision brief

An accepted job **can remain RUNNING indefinitely** when a synchronous Google request never
returns and the independent heartbeat/DB stay healthy. SDK 1.72.0 explicitly removes transport
timeouts and installs **five SDK attempts even without retry options**. Application `maxAttempts=3`
only acts after a translated timeout returns. Neither leases nor output-token limits bound that wait.

The installed SDK already supports millisecond client/request call timeout; no upgrade is justified.
There are at most **six logical GenAI calls per attempt** (facts 1, embeddings 2, initial generation
including compact fallback 2, repair 1). A call timeout covers its inner SDK attempts, rather than
giving each SDK attempt a fresh timeout. It cannot establish a hard worker-thread cancellation or
an accepted-to-terminal bound on queue/auth/storage/finalization. Its plain
`InterruptedIOException("timeout")` cause is also missed by today's classifier.

Propose explicit SDK attempts **1**, empty retryable HTTP-status list, and an original-accepted-age
cutoff in every option. This removes hidden status/error retries; it is **a requested policy change,
not an applied change**. Existing MAX_TOKENS compact fallback and single closure/repair stay intact.
Durable retries, where proposed, remain timeout-only. Do not newly retry rate limits, generic 5xx,
content or format failures.

| Unselected option | Fact / generation+repair / embedding request budgets | SDK attempts | Durable attempts | Six-call envelope per attempt | All-attempt GenAI + retry-delay envelope | Proposed original-accepted-age cutoff |
| --- | --- | --- | --- | --- | --- | --- |
| A: tolerant common budget | 370 / 370 / 370 s | 1 | 3 | 2220 s | 6680 s | 1320 s (22 min) |
| B: tolerant stage budgets, one durable retry | 370 / 220 / 10 s | 1 | 2 | 1050 s | 2110 s | 900 s (15 min) |
| C: tighter stage budgets, no durable retry | 230 / 120 / 7 s | 1 | 1 | 604 s | 604 s | 480 s (8 min) |
| D: tolerant stage budgets, no durable retry | 370 / 220 / 10 s | 1 | 1 | 1050 s | 1050 s | 480 s (8 min) |

**Recommendation: D**, subject to USER decision. Its request headroom exceeds completed broad-v4
facts/generation and the older exactly instrumented text-generation tail. One durable attempt
avoids restarting the entire image/facts/retrieval/generation path after an unexplained long wait;
the eight-minute accepted-age cutoff limits user waiting even with multiple slow sequential calls.
This favors truthful bounded failure over another expensive complete attempt. A/B tolerate more
transient timeout recovery and longer waiting. C risks more legitimate slow generation timeouts.
Older experimental completed tails around 326/626 seconds could time out under D; no claim that
all legitimate calls fit these budgets is made.

The cutoff is a **proposed enforcement target**, not a measured SLO or an implemented guarantee.
A future terminal bound is `J + Δ`, where `J` is the selected accepted-age cutoff and `Δ` is bounded
deadline inspection/scheduling plus durable transition/commit delay under an operating DB/service.
**Δ is not established or selected here**. Existing fixed-delay poll interval 2 s is not a guaranteed
2-second inspection bound: its scan/cleanup work can itself wait. If implementing a whole-job bound
needs a new architecture/product boundary, stop rather than silently expanding the correction.

The exact unresolved USER decision is the request budgets, SDK retry policy, durable attempt count,
accepted-age cutoff and its terminal/enforcement scope. All options anchor the cutoff to the original
persisted acceptedAt, including queue/retry waiting; it must never reset per attempt. Late success,
expired/stale ownership, retry overlap, result cleanup and noncooperative threads must be addressed
in the same bounded implementation before claiming acceptance. Selecting D alone cannot promote
this audit to a proved eight-minute guarantee. After approval, freeze the actual contract and the
deterministic timeout/hang validation plan in this same Work Package/PR; do not merge an audit-only
final PR or start PT-7.

## v4 approval and immutable truth reconciliation

[Authority snapshot](../evidence/product-trust-pt-6r2/authority.json) records merged PR #253,
[independent acceptance 6032563396](https://github.com/siamese-lang/terraformers-platform/pull/253#issuecomment-6032563396)
at head `690b436d69c4b1671e86b8aeb2b2249623291276`, USER merge as this execution base at
`2026-10-07T06:51:11Z`, and subsequent
[USER approval 6032637762](https://github.com/siamese-lang/terraformers-platform/pull/253#issuecomment-6032637762).
Candidate revision **2**, identity
`3e105771401051e0b199d8f79b3b993b161f27a03beeb0fb2d223f460d9a0757`, is now externally recorded
as **USER_FROZEN_EXACT_IMMUTABLE_CANDIDATE** for future PT-8A.

The manifest, identity JSON and truth-review Markdown retain their exact approved bytes, including
pre-approval false/null markers. As for historical PT-1, those snapshots do not override subsequent
external approval authority. No candidate identity is recomputed; revision-1 superseded evidence is
unchanged. Model-under-test count stays **0** and `executionAuthorized=false` for official inputs.
Truth freeze/independent review/merge do not authorize inference, cloud or OpenSearch execution.
This reconciliation is inside substantive PT-6R2, without a state-sync PR.

## Exact SDK 1.72.0 findings

[Audit JSON](../evidence/product-trust-pt-6r2/audit.json) binds pinned repository files, Maven binary,
source ZIP and transport source SHA-256 values. Published Maven SHA-1 values were independently
checked; the twelve critical source files equal official **v1.72.0** tag commit
`af291504959d6d644090ab70f01e6840a3614e79`. `javap` of the cached 1.72.0 binary confirms timeout
construction and default RetryInterceptor installation/attempt count. No SDK upgrade or request.

| Exact class/method | Confirmed behavior |
| --- | --- |
| `types.HttpOptions.timeout`, lines 50–52, 181–186 | Supported integer **milliseconds**; 0 disables call timeout. |
| `Client.Builder.httpOptions`; `types.GenerateContentConfig.httpOptions`; `types.EmbedContentConfig.httpOptions` | Both client and individual generate/embed request scope. Models forwards config HTTP options into ApiClient. |
| `ApiClient.mergeHttpOptions`, 715–754; `buildRequest`, 443–448; `HttpApiClient.executeRequest`, 81–95 | Present request timeout overrides client; absent request timeout inherits client transport. Clearing an absent request value does not erase the client default. Raw per-request options are tagged and create a client clone with that timeout. |
| `ApiClient.createHttpClient`, 274–309 | Without a custom transport, connect/read/write become **0**; call timeout is set only if provided. Current production supplies only API v1, hence no finite transport deadline. |
| `ApiClient.createHttpClient`, 305–307; `RetryInterceptor.intercept`, 62–107 | Absent HttpRetryOptions becomes an empty options object, not null; interceptor uses **5 total attempts**. IOExceptions and HTTP **408, 429, 500, 502, 503, 504** are retried. Other statuses/content/format failures are not SDK retries. |
| `RetryInterceptor.calculateDelay`, 109–121; `HttpRetryOptions` defaults | Initial 1 s, exponential base 2, max delay 60 s, jitter 1 (factor 0–2). First four retry sleeps total at most 30 s. Code also sleeps after the final retryable HTTP-status response: five sleeps at most 62 s. Final IOException throws before that last sleep. |
| `ApiClient.mergeHttpOptions`, 728–751; `getTimeoutHeader`, 670–676 | `X-Server-Timeout=ceil(timeoutMs/1000)` exists, but is added only when the supplied options' **headers Optional is present**. Timeout-only options do not automatically add it. Supplied custom headers can override it. Current config sets no timeout/header. Header emission does not prove Vertex backend deadline enforcement. |
| `HttpApiClient.executeRequest`, 93–94; `Models.processResponse...`, body-read catch | Transport/body-read IOException is wrapped in `GenAiIOException`, retaining its cause. |
| OkHttp 4.12.0 `RealCall.timeoutExit`, 394–401 | Whole-call timeout produces `java.io.InterruptedIOException("timeout")`, optionally with another cause. This need not be SocketTimeoutException. |
| OkHttp `OkHttpClient.Builder.callTimeout`, 910–937; `RealCall`, 70–75, 135–156 | One timeout covers DNS/connect/upload/server/body and retries in the **same Call**. Watchdog cancels call/exchange/socket; it does not interrupt an arbitrary worker thread, retry sleep or noncooperative DNS code. |
| `ApiClient.setHeaders`, 511–529 | Credential `refreshIfExpired()` occurs while building the request, before OkHttp `newCall(...).execute()`. HttpOptions call timeout does not bound credential refresh. |

Default SDK retries already include status/IO failures below the application taxonomy; it would be
incorrect to report that production currently has no retry unless explicitly enabled. At most six
logical calls × five SDK `chain.proceed` attempts = **30 per job attempt**, up to **90** for three
timeout-driven durable attempts. This is not an exact wire RPC/model/cost count: OkHttp's separate
`retryOnConnectionFailure=true` recovery/follow-up layer can perform additional exchanges. No
wire counts were measured here. Setting a finite call timeout does not turn this into `5 × timeout`;
all inner attempts share one timeout period, though cancellation cannot forcibly cut retry sleep.

Proposed SDK attempts=1 **and empty retryable-status list** avoids the interceptor's final-status
sleep; attempts=1 alone still sleeps after a default-retryable error response. Every option requests
this explicitly. Keeping/enabling SDK retries would require a separate selected status/IO taxonomy,
attempt/delay calculation and double-layer cost tradeoff, rather than relying on defaults.

Exact release sources: [ApiClient][sdk-api], [HttpApiClient][sdk-http],
[RetryInterceptor][sdk-retry], [HttpOptions][sdk-options], [HttpRetryOptions][sdk-retry-options].
The offline Maven dependency-tree diagnostic could not execute because its plugin is not cached;
it is recorded as such. Release POM, verified artifacts, prior cached backend package inventory and
binary javap provide the alternative source binding. The cached package is not asserted to be a
fresh build of this bound main. No backend tests or product measurements were run for this audit.

## Production attempt call graph and failure semantics

New accepted jobs are INTEGRATED_JAVA (`AnalysisJobService.create`, 39–70), persisted PENDING,
submitted after commit. `SelectedAnalysisProvider` selects Vertex under the retained overlay.
Bound source pins `com.google.genai:google-genai:1.72.0` (`backend/pom.xml`, 22, 125–127).
`VertexRuntimeConfiguration`, 16–23, creates one shared Vertex Client with API **v1**; no timeout,
retry options or custom transport. All generate/embed adapters below receive that same bean and
set no request HTTP overrides. Overlay selects generation `gemini-3.8-flash`, embedding
`gemini-embedding-2`/1536, REQUIRED broad-v4. The profile/class embedding fallback is still 001/1024;
configuration evidence is not a fresh observation of a cloud runtime. Production defaults are
`ANALYSIS_MAX_ATTEMPTS=3`, `ANALYSIS_RETRY_DELAY=10s`, lease 60 s, renew 20 s.

SDK `Models.generateContent`, 7207–7233, returns one private generate call when its function map
is empty; production supplies no tools, so automatic function calling adds no calls. Embedding-2
uses Vertex REST `:embedContent`; the embedding-001 fallback uses `:predict`
(`Models.buildRequestForPrivateEmbedContent`, 5807–5820; `Transformers.tIsVertexEmbedContentModel`).

| Sequential stage | External call / model | Maximum invocations inside one attempt | Timeout translation and durable outcome |
| --- | --- | --- | --- |
| Read persisted source | GCS `storage.get`, then blob content | One readContent operation (metadata and content RPCs), before facts | Separate Storage SDK/default policy; not GenAI. StorageException translated to ObjectStorageException. No new storage retry policy here. |
| Facts | shared GenAI generateContent / configured generation model | **1** if retrieval enabled; no facts compact retry | Facts classifier selects PROVIDER_TIMEOUT if recognizable; orchestrator only normalizes standard SocketTimeoutException/HttpTimeoutException causes. Plain InterruptedIOException is missed. |
| Initial query embedding | shared GenAI embedContent / configured embedding model | **1** | Adapter propagates SDK failure; orchestrator recognizes only the two standard timeout classes. |
| Initial retrieval | HTTP OpenSearch vector/global/resource/decision searches | **1** retrieval operation; up to **34 HTTP requests** | Each request timeout 30 s/connect 10 s, no explicit HTTP application retry. HttpTimeoutException cause within provider execution can become AnalysisProviderTimeoutException. |
| Normal grounded generation | shared GenAI generateContent / configured generation model | **1** | Recognized timeout -> AnalysisProviderTimeoutException; rate limit/provider error gets its existing semantic subtype. |
| Compact generation fallback | same shared generateContent | **1**, iff first generation ends MAX_TOKENS | One compact retry only. Transport timeout does not consume/trigger this fallback. Repeated MAX_TOKENS fails. |
| Generated-resource evidence closure | shared embedContent, then HTTP OpenSearch official resource searches | **1 embedding**, **1 retrieval operation**, up to **16 HTTP requests** | Only after usable architecture draft with missing generated-resource official evidence and retrieval enabled. Embedding/Search failures use the paths above. |
| Grounding repair | shared generateContent / configured generation model | **1** | Single-attempt repair. Timeout may cause a durable full-attempt retry under the current taxonomy, never an in-attempt repair retry. No second closure/repair cycle. |
| Draft/CLI validation | Local Terraform 1.8.5/AWS 5.100.0 | init **1**, validate **1** | Existing 60/20-second subprocess waits; terminate waits at most 2 s each. Technical failure terminal, not a new provider retry. |
| Result finalization | GCS write / DB registration / optional cleanup | One owned finalization; failure may invoke compensation | Outside returned provider work. No AnalysisProviderTimeoutException conversion at this storage boundary; existing cleanup accountability preserved. |

`ReferenceQuery` caps distinct resource types at **16**, including closure. Initial retrieval with
`n>0`: global 1 + n resource searches + project decision 1 + at most n missing-official searches =
`2+2n ≤ 34`; closure at most 16. Zero-resource query has only its global search. Maximum combined
OpenSearch request waits are **50 × 30 s = 1500 s**, conditional on ordinary request-timeout behavior;
this is a deliberately loose adversarial ceiling, not observed latency. CLI timed waits contribute
at most **84 s**, excluding filesystem/process-creation/CPU work. SDK credentials, GCS/DB, queue
delay and local parsing/IO have no independently established whole-job bound in this audit.

For the maximal grounded path: **facts + embedding + initial + compact + closure embedding + repair
= 6 GenAI calls**, four generateContent/two embedContent. Simple success uses three; successful
single-generation closure/repair uses five. Disabled retrieval omits facts/embeddings/search;
ambiguous/non-architecture rejection does not enter closure/repair or CLI finalization.

Current durable retry occurs only if `result == null`, a cause-chain
AnalysisProviderTimeoutException exists, and `attemptCount < maxAttempts`. It schedules PENDING
`nextAttemptAt=now+10s`, cancels heartbeat in finally, and later reclaims/starts the entire path.
Exhaustion records fenced FAILED with safe timeout reason and existing quality semantics. Rate
limits/5xx/content/format/truncation do not gain durable retries. A finalization storage timeout is
terminal, not retried at the durable provider layer. Source GCS reads occur inside provider analyze;
if their cause chain contains a standard SocketTimeoutException/HttpTimeoutException, the generic
orchestrator normalization can make them retryable. Do not equate that existing boundary with a new
GCS policy or with finalization writes outside it. Credential discovery/refresh is ancillary Google
auth network work, not a generate/embed model call; refresh checks happen during request construction
and actual auth RPC counts depend on credential type/cache, so no fabricated fixed count is given.

The plain SDK call-timeout chain `GenAiIOException -> InterruptedIOException("timeout")` contains
no timeout-named class. `ProviderFailureClassifier.isTimeout`, 27–33, returns false;
`AnalysisJobOrchestrator.hasStandardNetworkTimeout`, 87–94, recognizes only SocketTimeoutException
and HttpTimeoutException. Generation wraps the missed cause as PROVIDER_ERROR and bypasses standard
normalization; facts/embedding also do not become retryable timeout. A nested recognized timeout
can change this outcome, so not every SDK IO failure is falsely called a timeout. Missed facts/generation timeout causes produce existing PROVIDER_ERROR terminal quality; raw
embedding IO failure can leave the terminal reason list empty. Recognized provider timeouts instead
reach the existing timeout retry/exhaustion path and PROVIDER_TIMEOUT quality. Future coverage
must use the real SDK call-timeout shape and preserve ordinary interruption/non-timeout semantics.

## Mechanical terminality proof and current test limits

`AnalysisJobRunner.run`, 47–80: claim increments attempt/generation, mark RUNNING, schedule heartbeat,
then execute provider+validate synchronously on an analysis-job worker. The separate
`analysis-lease-heartbeat` scheduled executor calls `renewLease` every 20 s; runner 169–192 and
repository 68–75 only check generation/status/live lease and extend expiry. They check neither
worker progress nor original accepted age. The heartbeat is cancelled in finally only after work
returns/throws. Healthy repeated renewal keeps the row outside expired-lease eligibility forever.

If a Google call never returns, no SDK attempt/retry/error, durable retry/exhaustion or failure
transition is reached. Max output tokens, max attempts, lease duration and the 30-second executor
shutdown wait do not impose a deadline on this scenario. If heartbeat fails, lease loss fences
later writes but does not cancel the blocked thread. Reclaim SQL has no max-attempt/createdAt bound;
`maxAttempts=3` is not an absolute cap on repeated process-loss lease reclaims.

The local executor's **2 core / 4 max / 50 queue** bounds capacity, not accepted wait duration.
Rejected submissions preserve PENDING eligibility; stalled workers can strand queue work. Existing
dispatch fixed-delay scan/cleanup does not terminalize age-expired rows. Result finalization holds
an owned DB row lock during GCS storage/compensation (`AnalysisJobStateService.markSucceededOwned`,
99–145); a competing deadline transition may wait. Thus merely adding HttpOptions.timeout or an
unchecked clock test cannot justify a universal hard terminal deadline. Do not broaden storage
hardening here; resolve/limit the exact future terminal contract and stop for any required new design.

Existing deterministic tests are read, **not rerun**: runner timeout scheduling/exhaustion,
rate-limit/semantic terminality, heartbeat same-generation renewal, stale-owner fencing; orchestrator
standard network normalization/storage separation; facts HttpTimeoutException classification;
generation MAX_TOKENS fallback/closure/repair limits. Heartbeat test runs one callback and lets the
provider return. These prove their existing contracts but do not test a never-returning SDK call,
actual InterruptedIOException call timeout, credential refresh, or an independent accepted-age
cutoff. Source control flow establishes the present defect without another pre-decision fault
harness, fabricated before/after test, or live request.

## Historical latency, completed versus censored

[Latency evidence](../evidence/product-trust-pt-6r2/latency.json) retains every stage value from the
four historical v4 files, raw-file hashes/configuration, artifact digest verification and comparison
limits. These are stage timers, not all isolated RPC measurements. No percentile or population
accuracy is calculated.

| Run / artifact | Observation | Duration | Completion / comparability |
| --- | --- | --- | --- |
| 37419983898 / 11393232503; canonical-1 VPC | facts / composite retrieval / generation | 12698 / 1920 / 23809 ms | Completed synthetic v4/embedding-2/3.8-flash; no compact retry. |
| Same; canonical-3 CloudFront | generation | **88451 ms** | Completed **with compact retry**; longest recorded broad generation stage is not one isolated RPC. |
| Same; holdout EKS | facts | **68747 ms** | Completed, same configured generation model. |
| Same; holdout workload/RDS/SG | facts / retrieval / generation / CLI | **183242 / 805 / 9090 / 17948 ms** | Completed; summed stages **211085 ms**, not a persisted accepted-to-terminal duration. |
| Same; holdout non-architecture | facts | **39805 ms** | Completed; confirms controls also pay fact-extraction work before generation classification. |
| 37480519016 / 11422455892; original case 01 | acceptance receipt -> first observed terminal | **122464 ms** | Completed poll-observed sample; old timing predates PT-4 terminalAt. Provider internal RPC timers absent. |
| Same case 01 correlated logs | analysis_execution / result_finalize; initial / closure retrieval | **116633 / 481; 3227 / 777 ms** | Completed coarse/composite stages; analysis_execution includes provider and CLI. Not isolated facts/embedding/generation/repair durations. |
| Same; original case 02 | original observation censor | **424846 ms** | **TERMINAL_NOT_OBSERVED**, not FAILED, a successful provider duration, or a replacement terminal measurement. |
| Same case 02 logs | claim -> initial retrieval log gap; initial / closure retrieval | approximately **375181; 1005 / 1245 ms** | Completed retrieval logs within censored job; pre-retrieval gap includes unseparated work, not proof of exact vision latency/root cause. |
| 36758290144 / 11118632138, diagnostic attempt 3 | CloudFront candidate text-only generation | **109773 ms** | Completed, one HTTP exchange/200, no network failures; same model, older experimental fact-reuse/MEDIUM path, not current production. |
| 36739886593, earlier comparison (documented rounded values) | CloudFront control / AOSS candidate text generation | approximately **626 / 326 s** | Completed experimental stage evidence; exact current-path RPC duration not established. Shows proposals may reject legitimate tails. |
| 36391698161 / 10955719668 | AOSS facts | **130489 ms** | Completed older v3/embedding001/source; supporting tail, not current v4 calibration. |

Broad run source is `46d50f5e13f85199e25ca62088b43158f9084264`, model 3.8-flash/embedding-2,
SDK pin already 1.72.0. Fact/embedding/generation adapters are byte-unchanged; evaluator/provider
orchestration was subsequently unified by PR #238. It did not measure today's full durable closure
job. Original PT-2 runtime source `688119631a790e876235565711c07085aff15124` uses today's Vertex
adapters/orchestration, but PT-3 semantic guard and PT-4 timing/trust came later. Do not overwrite
old poll times with new terminalAt semantics or attribute the case-02 gap to a proved upstream cause.
Local stub browser times and CI times are not GenAI latency evidence.

## Arithmetic and residual risk

For request budgets F/G/E, GenAI maximal sequential envelope is **F + 3G + 2E**. With durable
attempts A and unchanged timeout-only delay D=10 s: **A(F+3G+2E) + (A−1)D**. SDK attempts=1 and
empty status set gives no SDK retry sleep. If SDK retries were kept, each call still shares T but
may retain post-cancellation retry-sleep delay; do not multiply call timeout blindly by five or
assume cancellation interrupts sleep. No option enables more retries.

Including known adversarial non-provider timed work, the loose envelope is
**A(F+3G+2E + 1500 + 84) + (A−1)10 seconds**:
A **11432 s**, B **5278 s**, C **2188 s**, D **2634 s**, **plus unestablished queue/auth/GCS/DB/local
work**. Therefore client-only accepted-to-terminal bounds remain unestablished. The independently
enforced original-age cutoff, if approved and proved, supersedes these slower nominal paths:
conditional terminal bound **1320/900/480/480 s + Δ**, respectively. All requests/stages/retries
must respect the remaining job budget and late-result fencing; no whole deadline resets.

Budgets are explicit heuristics: tolerant F = ceil-to-10s(2 × 183242 ms) = 370 s;
tolerant G = ceil-to-10s(2 × older exact 109773 ms) = 220 s; tolerant E =
ceil-to-1s(3 × largest known original composite retrieval 3227 ms) = 10 s. Composite retrieval is
only a loose observed envelope for its contained embedding, not an isolated embedding percentile.
Tighter F/G use 1.25 × 183242/88451 ms -> 230/120 s; tighter E uses 2 × 3227 -> 7 s.
Job cutoffs ceil-to-minute **6/4/2/2 × 211085 ms** -> 22/15/8/8 min. Human-readable rounding
follows source/evidence calculations; no percentile or round-number SLO was inferred. Sparse
synthetic/one-realistic-completed samples are weak calibration, and official inputs may be slower.

Cancellation does not establish Google backend work/cost cessation, worker capacity recovery or
DB availability. A durable failure must never appear SUCCEEDED/EVIDENCE_BACKED; preserve PT-4
timing/trust and PT-2 final INCOMPLETE: 03–10 NOT_RUN, four realistic aggregate rates null, original
424846-ms censor, zero-product-observation recovery runs unchanged, no additional dispatch.
PT-6R1 code/MariaDB evidence and counters, PT-6 browser history, candidate/truth/corpus, prompt,
model/retrieval/scoring, workflows/IAM/infra/runtime are unchanged. No new CI gate/framework.

## Validation and continuation

[Validation record](../evidence/product-trust-pt-6r2/validation.json): exact SDK/source/tag/artifact
binding, YAML/JSON parsing, Program/state/WP/gate/plan consistency, immutable truth approval,
bound base, historical references/bytes/counters, call graph and option arithmetic, allowed scope,
staged `git diff --check`. No backend/MariaDB/browser/model/cloud/OpenSearch test or live action.
Normal automatic PR CI may execute; it is not PT-6R2 hang/timeout acceptance. No workflow dispatch,
SDK upgrade, image publication/deployment, official case submission, PT-2 or PT-7 execution.

Next action requires **USER decision at PROVIDER_OR_JOB_LATENCY_BUDGET_DECISION**. Continue the
same branch/PR only after that decision; independent review and USER merge remain required after
bounded implementation/evidence. CI green, audit PR creation, v4 freeze or a recommendation grants
neither acceptance nor merge authority.

[sdk-api]: https://github.com/googleapis/java-genai/blob/af291504959d6d644090ab70f01e6840a3614e79/src/main/java/com/google/genai/ApiClient.java
[sdk-http]: https://github.com/googleapis/java-genai/blob/af291504959d6d644090ab70f01e6840a3614e79/src/main/java/com/google/genai/HttpApiClient.java
[sdk-retry]: https://github.com/googleapis/java-genai/blob/af291504959d6d644090ab70f01e6840a3614e79/src/main/java/com/google/genai/RetryInterceptor.java
[sdk-options]: https://github.com/googleapis/java-genai/blob/af291504959d6d644090ab70f01e6840a3614e79/src/main/java/com/google/genai/types/HttpOptions.java
[sdk-retry-options]: https://github.com/googleapis/java-genai/blob/af291504959d6d644090ab70f01e6840a3614e79/src/main/java/com/google/genai/types/HttpRetryOptions.java
